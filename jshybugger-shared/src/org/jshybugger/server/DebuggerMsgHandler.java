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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.webbitserver.WebSocketConnection;


/**
 * The DebuggerMsgHandler handles all Debugger class related debugging protocol messages.
 * 
 * https://developers.google.com/chrome-developer-tools/docs/protocol/tot/debugger
 */
public class DebuggerMsgHandler extends AbstractMsgHandler {

	public final static String HANDLER_NAME = "Debugger";

	/** The methods available. */
	private final HashMap<String,Boolean> METHODS_AVAILABLE = new HashMap<String, Boolean>(); 
	
	/** The Constant TAG. */
	private static final String TAG = "DebuggerMsgHandler";
	
	/** The loaded scripts. */
	private Map<String,Integer> loadedScripts = new HashMap<String,Integer>();
	
	/** The script breakpoints. */
	private Map<String,Set<Breakpoint>> scriptBreakpoints =  new HashMap<String,Set<Breakpoint>>();

	
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
						debugSession.loadScriptResourceById(message.getJSONObject("params").getString("scriptId"), false) ));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			conn.send(reply.toString());

		} else if ("setScriptSource".equals(method)) {

			setScriptSource(conn, message);
			
		} else if ("continueToLocation".equals(method)) {
			
			JSONObject location = message.getJSONObject("params").getJSONObject("location");
			continueToLocation(conn, message.getInt("id"), location.getString("scriptId"), location.getInt("lineNumber"));

		} else if ("setBreakpointByUrl".equals(method)) {
			
			JSONObject params = message.getJSONObject("params");
			setBreakpointByUrl(conn, message.getInt("id"), params.getString("url"), params.getInt("lineNumber"), params.getString("condition"), false);
			
		} else if ("setBreakpoint".equals(method)) {

			JSONObject params = message.getJSONObject("params");
			setBreakpointByUrl(conn, message.getInt("id"), params.getJSONObject("location").getString("scriptId"), params.getJSONObject("location").getInt("lineNumber"), params.getString("condition"), true);
			
		} else if ("removeBreakpoint".equals(method)) {
			
			removeBreakpoint(conn, message);
			
		} else if (method.equals("setPauseOnExceptions")) {
			
			setPauseOnExceptions(conn, message);
			
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
			
		} else if (method.equals("setBreakpointsActive")) {
			
			setBreakpointsActive(conn, message);
			
		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}
	
	/**
	 * Process "Debugger.setScriptSource" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	protected void setScriptSource(final WebSocketConnection conn,
			final JSONObject message)  throws JSONException {
		// TODO should this be an abstract method
	}	

	/**
	 * Process "Debugger.setPauseOnExceptions" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void setPauseOnExceptions(final WebSocketConnection conn,
			final JSONObject message)  throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"setPauseOnExceptions",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				DebuggerMsgHandler.this.sendAckMessage(conn, message);
			}
		});				
	}

	/**
	 * Process "Debugger.setBreakpointsActive" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void setBreakpointsActive(final WebSocketConnection conn,
			final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"setBreakpointsActive",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				DebuggerMsgHandler.this.sendAckMessage(conn, message);
			}
		});				
	}

	/**
	 * Process "Debugger.continueToLocation" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void continueToLocation(final WebSocketConnection conn, final int id, final String url, final int lineNumber) throws JSONException {
		debugSession.getBrowserInterface().sendMsgToWebView(
				"continue-to",
				new JSONObject().put("url", url).put(
						"lineNumber", lineNumber),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				JSONWriter res = new JSONStringer().object()
						.key("id").value(id)
						.key("result").object().endObject().endObject();
				
				
				conn.send(res.toString());
			}
		});		
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
				"evaluateOnCallFrame",
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
			
		} else {
			super.onSendMessage(conn, method, message);
		}
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
		Breakpoint breakpoint = Breakpoint.valueOf(params.getString("breakpointId"));

//		Log.d(TAG, "removeBreakpoint: " + breakpoint);
		
		Set<Breakpoint> breakpoints = scriptBreakpoints.get(breakpoint.file);
		if (breakpoints != null) {
			breakpoints.remove(breakpoint);
		}
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"breakpoint-remove",
				new JSONObject().put("breakpointId", params.getString("breakpointId")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id")).key("result").object().endObject()
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
	private void setBreakpointByUrl(final WebSocketConnection conn, final int id, final String url, final int lineNumber, final String condition, final boolean actualLocation) throws JSONException {
				
		final Breakpoint breakpoint = new Breakpoint(url, lineNumber, condition);
//		Log.d(TAG, "setBreakpointByUrl: " + breakpoint);

		debugSession.getBrowserInterface().sendMsgToWebView(
				"breakpoint-set",
				new JSONObject().put("url", url).put(
						"lineNumber", lineNumber).put("condition", condition),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				// add breakpoint to internal list of breakpoints
				Set<Breakpoint> breakpoints = scriptBreakpoints.get(url);
				if (breakpoints == null) {
					breakpoints = new HashSet<Breakpoint>();
					scriptBreakpoints.put(url, breakpoints);
				}
				breakpoints.add(breakpoint);
				
				JSONWriter res = new JSONStringer().object()
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
		
		
		Set<Breakpoint> breakpoints = scriptBreakpoints.get(url);
		if (breakpoints != null) {
			if (conn != null) {
				for (Breakpoint breakpoint : breakpoints) {
					debugSession.getBrowserInterface().sendMsgToWebView(
							"breakpoint-set",
							new JSONObject().put("url", url).put("lineNumber",
									breakpoint.line).put("condition", breakpoint.condition), null);
					
//					Log.d(TAG, "breakpointResolved: " + breakpoint);

					conn.send(new JSONStringer().object()
						.key("method").value("Debugger.breakpointResolved")
						.key("params").object()
								.key("breakpointId").value(breakpoint.getBreakpointId())
								.key("location").object()
									.key("scriptId").value(url)
									.key("lineNumber").value(breakpoint.line)
									.key("columnNumber").value(0)
								.endObject()
							.endObject()
						.endObject().toString());
				}
			}
		}
	
	}
	
	static class Breakpoint {
		final String file;
		final int line;
		final String condition;
		
		public Breakpoint(String file, int line, String condition) {
			super();
			this.file = file;
			this.line = line;
			this.condition = condition;
		}
		
		public String getBreakpointId() {
			return file + ":" + line;
		}
		
		public static Breakpoint valueOf(String breakpointId) {
			int idx = breakpointId.lastIndexOf(":");
			return new Breakpoint(breakpointId.substring(0, idx), Integer.valueOf(breakpointId.substring(idx+1)), null);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((file == null) ? 0 : file.hashCode());
			result = prime * result + line;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Breakpoint other = (Breakpoint) obj;
			if (file == null) {
				if (other.file != null)
					return false;
			} else if (!file.equals(other.file))
				return false;
			if (line != other.line)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Breakpoint [file=" + file + ", line=" + line
					+ ", condition=" + condition + "]";
		}
	}
}
