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

import org.json.JSONException;
import org.json.JSONObject;
import org.webbitserver.WebSocketConnection;

/**
 * A MessageHandler is responsible for processing debug protocol messages between app and debugger frontend.
 * 
 * Remote Debugging protocol: https://developers.google.com/chrome-developer-tools/docs/protocol/tot/index  
 */
public interface MessageHandler {

	/**
	 * Gets the message handler name.
	 *
	 * @return the handler name
	 */
	public String getObjectName();
	
	/**
	 * Called on receive message from debug frontend.
	 *
	 * @param conn the websocket connection
	 * @param method the handler method name i.e. pageReload
	 * @param message the JSON message 
	 * @throws JSONException the jSON exception
	 */
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException;

	/**
	 * Called to send a message from the app to debug frontend.
	 *
	 * @param conn he websocket connection
	 * @param method the handler method name i.e. pageReload
	 * @param message the JSON message
	 * @throws JSONException the jSON exception
	 */
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException;
}
