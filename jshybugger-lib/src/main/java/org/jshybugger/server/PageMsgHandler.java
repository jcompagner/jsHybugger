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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

/**
 * The PageMsgHandler handles all Page related debugging protocol messages.
 * 
 * https://developers.google.com/chrome-developer-tools/docs/protocol/tot/page
 */
public class PageMsgHandler extends AbstractMsgHandler {

	/** The methods available. */
	private final HashMap<String,Boolean> METHODS_AVAILABLE = new HashMap<String, Boolean>(); 

	/**
	 * Instantiates a new page message handler.
	 *
	 * @param debugServer the debug server
	 */
	public PageMsgHandler(DebugSession debugServer) {
		super(debugServer, "Page");

		METHODS_AVAILABLE.put("disable", true);
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		
		if (METHODS_AVAILABLE.containsKey(method)) {
			
			JSONObject reply = new JSONObject();
			
			reply.put("id", message.getInt("id"));
			reply.put("result", new JSONObject().put("result", METHODS_AVAILABLE.get(method)));
			
			
			conn.send(reply.toString());
			
		} else if ("enable".equals(method)) {
			
			debugSession.getBrowserInterface().sendMsgToWebView(
					"breakpoint-resume",
					new JSONObject(), null);
			
		} else if ("getResourceTree".equals(method)) {

			getResourceTree(conn, message);

		} else if ("getResourceContent".equals(method)) {

			getResourceContent(conn, message);
						
		} else if ("reload".equals(method)) {
			
			pageReload(conn, message);

		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}
	
	/**
	 * Process "Page.getResourceContent" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void getResourceContent(final WebSocketConnection conn,
			final JSONObject message) throws JSONException {
		
		final String url = message.getJSONObject("params").getString("url");
		if (url.startsWith("data:")) {
			
			conn.send(new JSONStringer().object()
					.key("id").value(message.getInt("id"))
					.key("result").value(new JSONObject()
						.put("content", url.substring(url.indexOf(",")+1))
						.put("base64Encoded", url.substring(url.indexOf(";")+1).startsWith("base64")))
					.endObject().toString());
			
		} else {
			debugSession.getBrowserInterface().sendMsgToWebView(
					"getResourceContent",
					new JSONObject().put("params",message.getJSONObject("params")),
					new ReplyReceiver() {
	
				@Override
				public void onReply(JSONObject data) throws JSONException {
					
					try {
						conn.send(new JSONStringer().object()
								.key("id").value(message.getInt("id"))
								.key("result").value(new JSONObject()
									.put("content", debugSession.loadScriptResourceById(url, data.getBoolean("base64Encoded") ))
									.put("base64Encoded", data.getBoolean("base64Encoded")))
								.endObject().toString());
						
					} catch (IOException e) {
						throw new JSONException(e.getMessage());
					}
				}
			});		
		}
	}
	
	/**
	 * Process "Page.getResourceTree" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void getResourceTree(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"getResourceTree",
				null,
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").value(data)
						.endObject().toString());
				}
		});		
	}	
	
	/**
	 * Process "Page.reload" protocol messages.
	 * Forwards the message to the WebView and returns an acknowledge message to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void pageReload(final WebSocketConnection conn, final JSONObject message) throws JSONException {

		debugSession.getBrowserInterface().sendMsgToWebView(
				"page-reload",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				sendAckMessage(conn, message);
			}
		});
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message)
			throws JSONException {
		
		if ("GlobalInitHybugger".equals(method)) {
			sendLoadEventFired(conn, message);
			
		} else if ("GlobalPageLoaded".equals(method)) {
			sendDomContentEventFired(conn, message);
			
		} else {
			super.onSendMessage(conn, method, message);
		}
	}
	
	
	/**
	 * Process "Page.loadEventFired" protocol messages.
	 * Forwards the message to the debugger frontend. This message is triggered by loading the jsHybugger.js file. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void sendLoadEventFired(WebSocketConnection conn, JSONObject msg) throws JSONException {
		
		if (conn != null) {
			
			conn.send(new JSONStringer().object()
					.key("method").value("Page.frameStartedLoading")
						.key("params").object()
					    	.key("frameId").value(msg.get("frameId"))
						.endObject()
					.endObject()
				.toString());

			conn.send(new JSONStringer().object()
				.key("method").value("Page.loadEventFired")
					.key("params").object()
				    	.key("timestamp").value(System.currentTimeMillis())
					.endObject()
				.endObject()
			.toString());
		}
	}

	/**
	 * Process "Page.domContentEventFired" protocol messages.
	 * Forwards the message to the debugger frontend. This message is triggered by the onLoad event. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void sendDomContentEventFired(WebSocketConnection conn, JSONObject msg) throws JSONException {
		
		if (conn != null) {
			conn.send(new JSONStringer().object()
				.key("method").value("Page.domContentEventFired")
					.key("params").object()
				    	.key("timestamp").value(System.currentTimeMillis() / 1000)
					.endObject()
				.endObject()
			.toString());

			conn.send(new JSONStringer().object()
					.key("method").value("Page.frameStoppedLoading")
						.key("params").object()
					    	.key("frameId").value(msg.get("frameId"))
						.endObject()
					.endObject()
				.toString());
		}
	}
}
