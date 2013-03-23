/*
 * Copyright 2013 Wolfgang Flohr-Hochbichler (developer@jshybugger.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jshybugger.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;


/**
 * The DebuggerMsgHandler handles all Debugger class related debugging protocol messages.
 * 
 * https://developers.google.com/chrome-developer-tools/docs/protocol/tot/debugger
 */
public class DebuggerMsgHandler extends AbstractMsgHandler {

	/** The methods available. */
	private final HashMap<String,Boolean> METHODS_AVAILABLE = new HashMap<String, Boolean>(); 
	
	/** The loaded scripts. */
	private Map<String,Integer> loadedScripts = new HashMap<String,Integer>();
	
	/** The script breakpoints. */
	private Map<String,List<Integer>> scriptBreakpoints =  new HashMap<String,List<Integer>>();
	
	public final static String HANDLER_NAME = "Debugger";
	
	/**
	 * Instantiates a new debugger msg handler.
	 *
	 * @param debugSession the debug server
	 */
	public DebuggerMsgHandler(DebugSession debugSession) {
		super(debugSession, HANDLER_NAME);

		METHODS_AVAILABLE.put("causesRecompilation", false);
		METHODS_AVAILABLE.put("supportsNativeBreakpoints", false);
		METHODS_AVAILABLE.put("canSetScriptSource", true);
		METHODS_AVAILABLE.put("enable", true);
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(final WebSocketConnection conn, String method, final JSONObject message) throws JSONException {
		
		if (METHODS_AVAILABLE.containsKey(method)) {
			
			JSONObject reply = new JSONObject();
			
			reply.put("id", message.getInt("id"));
			reply.put("result", new JSONObject().put("result", METHODS_AVAILABLE.get(method)));
			
			if("enable".equals(method)) {
				for (Entry<String,Integer> entry : loadedScripts.entrySet()) {
					sendScriptParsed(conn, entry.getKey(), entry.getValue());
				}
			}
			
			conn.send(reply.toString());
			
		} else if ("getScriptSource".equals(method)) {
			
			JSONObject reply = new JSONObject();
			
			reply.put("id", message.getInt("id"));
			try {
				reply.put("result", new JSONObject().put("scriptSource", 
						debugSession.loadScriptResourceById(message.getJSONObject("params").getString("scriptId")) ));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			conn.send(reply.toString());
			
		} else if ("setBreakpointByUrl".equals(method)) {
			
			JSONObject params = message.getJSONObject("params");
			setBreakpointByUrl(conn, message.getInt("id"), params.getString("url"), params.getInt("lineNumber"), false);
			
		} else if ("setBreakpoint".equals(method)) {

			JSONObject params = message.getJSONObject("params");
			setBreakpointByUrl(conn, message.getInt("id"), params.getJSONObject("location").getString("scriptId"), params.getJSONObject("location").getInt("lineNumber"), true);
			
		} else if ("removeBreakpoint".equals(method)) {
			
			removeBreakpoint(conn, message);
			
		} else if (method.equals("setPauseOnExceptions") ||
				method.equals("setBreakpointsActive")) {
			
			conn.send(new JSONStringer().object()
					.key("id").value(message.getInt("id"))
					.key("result").object().endObject().endObject().toString());
			
		} else if (method.equals("evaluateOnCallFrame")) {
			
			evaluateOnCallFrame(conn, message);
			
		} else if (method.equals("resume")) {
			sendDebuggerMsgToWebView(conn, "breakpoint-resume", message);
			
		} else if (method.equals("stepOver")) {
			sendDebuggerMsgToWebView(conn, "breakpoint-step-over", message);
			
		} else if (method.equals("stepOut")) {
			sendDebuggerMsgToWebView(conn, "breakpoint-step-out", message);
			
		} else if (method.equals("stepInto")) {
			sendDebuggerMsgToWebView(conn, "breakpoint-step-into", message);
			
		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}

	private void sendDebuggerMsgToWebView(final WebSocketConnection conn,
			final String command, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(command, null, new ReplyReceiver() {
			
			@Override
			public void onReply(JSONObject data) throws JSONException {
				sendAckMessage(conn, message);

				conn.send(new JSONStringer().object()
						.key("method").value("Debugger.resumed").endObject().toString());
			}
		});
	}
	
	/**
	 * Process "Debugger.evaluateOnCallFrame" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void evaluateOnCallFrame(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		JSONObject params = message.getJSONObject("params");
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"eval",
				new JSONObject().put("params", params),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").object()
							.key("result").value(data)
						.endObject().endObject().toString());
				}
		});		
	}
	
	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message)
			throws JSONException {
		if ("paused".equals(method)) {
			sendPaused(conn, message);

		} else if (method.equals("scriptParsed")) {
			sendScriptParsed(conn, message.getString("url"), message.getInt("numLines"));
			
		} else if (method.equals("GlobalInitHybugger")) {
			loadedScripts.clear();
			sendGlobalObjectCleared(conn);
			
		} else if (method.equals("getResourceTree")) {
			
			sendResourceTree(conn, message);
		} else {
			super.onSendMessage(conn, method, message);
		}
	}
	
	private void sendResourceTree(WebSocketConnection conn, JSONObject message) throws JSONException {
		
		JSONStringer result = new JSONStringer().object()
			   .key("result").object()
			      .key("frameTree").object()
			         .key("frame").object()
			            .key("id").value("3130.1")
			            .key("url").value("http://localhost/index.html")
			            .key("loaderId").value("3130.2")
			            .key("securityOrigin").value("http://localhost")
			            .key("mimeType").value("text/html")
			         .endObject()
			      .key("resources").array();
		
		for (String file : loadedScripts.keySet()) {
			result.object()
				.key("url").value(file)
				.key("type").value("Script")
				.key("mimeType").value("text/plain")
			.endObject();
		}
		
		conn.send(result.endArray().endObject().endObject().key("id").value(message.getInt("id")).endObject().toString());
	}

	/**
	 * Send global object cleared.
	 *
	 * @param conn the conn
	 * @throws JSONException the jSON exception
	 */
	private void sendGlobalObjectCleared(WebSocketConnection conn) throws JSONException {
		if (conn != null) {
			conn.send(new JSONStringer().object()
				.key("method").value("Debugger.globalObjectCleared")
				.endObject()
			.toString());
		}
	}
	
	/**
	 * Send paused.
	 *
	 * @param conn the conn
	 * @param message the message
	 * @throws JSONException the jSON exception
	 */
	private void sendPaused(final WebSocketConnection conn, final JSONObject message) throws JSONException {

		if (conn != null) { 
			conn.send(new JSONStringer().object()
					.key("method").value("Debugger.paused")
					.key("params").object()
						.key("callFrames").value(message.getJSONArray("callFrames"))
						.key("reason").value(message.getString("reason"))
						.key("data").value(message.getJSONObject("auxData"))
						.endObject()
					.endObject()
				.toString());
		}		
	}
	
	/**
	 * Process "Debugger.removeBreakpoint" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void removeBreakpoint(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		final JSONObject params = message.getJSONObject("params");
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"breakpoint-remove",
				new JSONObject().put("breakpointId", params.getString("breakpointId")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				String[] breakpointInfo = params.getString("breakpointId").split("[:]");
				List<Integer> breakpoints = scriptBreakpoints.get(breakpointInfo[0]);
				if (breakpoints != null) {
					breakpoints.remove(Integer.valueOf(breakpointInfo[1]));
				}
			
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
					.endObject().toString());
			}
		});
	}
	
	/**
	 * Process "Debugger.setBreakpointByUrl" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void setBreakpointByUrl(final WebSocketConnection conn, final int id, final String url, final int lineNumber, final boolean actualLocation) throws JSONException {
				
		debugSession.getBrowserInterface().sendMsgToWebView(
				"breakpoint-set",
				new JSONObject().put("url", url).put(
						"lineNumber", lineNumber),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				// add breakpoint to internal list of breakpoints
				List<Integer> breakpoints = scriptBreakpoints.get(url);
				if (breakpoints == null) {
					breakpoints = new ArrayList<Integer>();
					scriptBreakpoints.put(url, breakpoints);
				}
				if (!breakpoints.contains(lineNumber)) {
					breakpoints.add(lineNumber);
				}
				
				JSONStringer res = new JSONStringer().object()
						.key("id").value(id)
						.key("result").object()
							.key("breakpointId").value(data.getString("breakpointId"));
				
				if (actualLocation) {
					res.key("actualLocation").object()
						.key("scriptId").value(url)
						.key("lineNumber").value(lineNumber)
						.key("columnNumber").value(0)
					.endObject();
				} else {
					res.key("locations").array()
						.object()
							.key("scriptId").value(url)
							.key("lineNumber").value(lineNumber)
							.key("columnNumber").value(0)
						.endObject()
					.endArray();
				}
				
				conn.send(res.endObject()
					.endObject().toString());
			}
		});
	}
	
	/**
	 * Process "Debugger.scriptParsed" protocol messages.
	 * Forwards the message to the debugger frontend and notifies the webview about all set breakpoints for this script. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void sendScriptParsed(WebSocketConnection conn, String url, int numLines) throws JSONException {

		loadedScripts.put(url, numLines);
		if (conn != null) {
			conn.send(new JSONStringer().object()
				.key("method").value("Debugger.scriptParsed")
				.key("params").object()
					.key("scriptId").value(url)
					.key("url").value(url)
					.key("startLine").value(0)
					.key("startColumn").value(0)
					.key("endLine").value(numLines)
					.key("endColumn").value(0)
					.key("isContentScript").value(false)
					.endObject()
				.endObject().toString());
		}
		
		
		List<Integer> breakpoints = scriptBreakpoints.get(url);
		if (breakpoints != null) {
			if (conn != null) {
				for (int breakpoint : breakpoints) {
					debugSession.getBrowserInterface().sendMsgToWebView(
							"breakpoint-set",
							new JSONObject().put("url", url).put("lineNumber",
									breakpoint), null);
					
					conn.send(new JSONStringer().object()
						.key("method").value("Debugger.breakpointResolved")
						.key("params").object()
								.key("breakpointId").value(url + ":" + breakpoint)
								.key("location").object()
									.key("scriptId").value(url)
									.key("lineNumber").value(breakpoint)
									.key("columnNumber").value(0)
								.endObject()
							.endObject()
						.endObject().toString());
				}
			}
		}
	
	}
}
