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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

/**
 * The ConsoleMsgHandler handles all Console related debugging protocol messages.
 * 
 * https://developers.google.com/chrome-developer-tools/docs/protocol/tot/console
 */
public class ConsoleMsgHandler extends AbstractMsgHandler {

	/** The methods available. */
	private final HashMap<String,Boolean> METHODS_AVAILABLE = new HashMap<String, Boolean>(); 
	
	private final List<JSONObject> storedMessages = new ArrayList<JSONObject>();

	/**
	 * Instantiates a new console msg handler.
	 *
	 * @param debugServer the debug server
	 */
	public ConsoleMsgHandler(DebugServer debugServer) {
		super(debugServer, "Console");

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
			
			pushStoredMessages(conn);
		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}
	
	
	private void pushStoredMessages(WebSocketConnection conn) throws JSONException {
		for (JSONObject msg : storedMessages) {
			sendMessageAdded(conn, msg);			
		}
		storedMessages.clear();
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message)
			throws JSONException {
		
		if ("messageAdded".equals(method)) {
			sendMessageAdded(conn, message);
		} else if (method.equals("GlobalInitHybugger")) {
			sendMessagesCleared(conn);			
		} else {
			super.onSendMessage(conn, method, message);
		}
	}

	/**
	 * Send "Console.messagesCleared" message to debugger frontend.
	 *
	 * @param conn the websocket connection
	 * @throws JSONException the jSON exception
	 */
	private void sendMessagesCleared(WebSocketConnection conn) throws JSONException {
		if (conn != null) {
			conn.send(new JSONStringer().object()
				.key("method").value("Console.messagesCleared")
				.endObject()
			.toString());
			
			storedMessages.clear();
		}
	}
	
	/**
	 * Send "Console.messageAdded" message to debugger frontend.
	 *
	 * @param conn the conn
	 * @param msg the msg
	 * @throws JSONException the jSON exception
	 */
	private void sendMessageAdded(WebSocketConnection conn, JSONObject msg) throws JSONException {
		
		if (conn != null) {
			conn.send(new JSONStringer().object()
				.key("method").value("Console.messageAdded")
					.key("params").object()
					    .key("message").object() 
					    	.key("level").value(msg.getString("type").toLowerCase(Locale.US))
					    	.key("source").value("javascript")
					    	.key("text").value(msg.getString("message"))
					    .endObject()
					.endObject()
				.endObject()
				.toString());
		} else {
			storedMessages.add(msg);
		}
	}
}
