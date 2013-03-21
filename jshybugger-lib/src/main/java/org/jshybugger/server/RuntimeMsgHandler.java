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

import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;


/**
 * The RuntimeMsgHandler handles all Runtime related debugging protocol messages.
 * 
 * https://developers.google.com/chrome-developer-tools/docs/protocol/tot/runtime
 */
public class RuntimeMsgHandler extends AbstractMsgHandler {

	/** The methods available. */
	private final HashMap<String,Boolean> METHODS_AVAILABLE = new HashMap<String, Boolean>(); 

	/**
	 * Instantiates a new runtime message handler.
	 *
	 * @param debugServer the debug server
	 */
	public RuntimeMsgHandler(DebugServer debugServer) {
		super(debugServer, "Runtime");

		METHODS_AVAILABLE.put("enable", true);
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
			
		} else if (method.equals("releaseObjectGroup")) {
			
			sendAckMessage(conn, message);
			
		} else if (method.equals("getProperties")) {
			
			getProperties(conn, message);

		} else if (method.equals("evaluate")) {
			
			evaluate(conn, message);

		} else if (method.equals("callFunctionOn")) {

			callFunctionOn(conn, message);
			
		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}

	private void callFunctionOn(final WebSocketConnection conn, final JSONObject message) throws JSONException {

		debugServer.getBrowserInterface().sendMsgToWebView(
				"callFunctionOn",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").value(data.getJSONObject("result"))
						.endObject().toString());
				}
		});		
		
	}

	/**
	 * Process "Runtime.getProperties" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void getProperties(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		JSONObject params = message.getJSONObject("params");
		
		debugServer.getBrowserInterface().sendMsgToWebView(
				"getProperties",
				new JSONObject().put("objectId", params.getString("objectId")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").object()
							.key("result").value(data.getJSONArray("result"))
						.endObject().endObject().toString());
				}
		});		
	}

	/**
	 * Process "Runtime.evaluate" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void evaluate(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		JSONObject params = message.getJSONObject("params");
		
		debugServer.getBrowserInterface().sendMsgToWebView(
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
}
