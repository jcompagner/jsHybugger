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

/**
 * This class is the interface between the webview/browser and the debugging service.
 * 
 */
public interface BrowserInterface {

	/**
	 * Gets the debug session.
	 *
	 * @return the debug session
	 */
	public DebugSession getDebugSession();

	/**
	 * Sets the debugging session.
	 *
	 * @param jsrdpServer the new debug server
	 */
	public void setDebugSession(DebugSession debugSession);

	/**
	 * Send message to debug service.
	 *
	 * @param path the message handler name i.e. "Debugger.sendPaused"
	 * @param data the JSON data
	 */
	public void sendToDebugService(String path, String data);

	/**
	 * Send message to webview.
	 *
	 * @param command the command
	 * @param data the JSON payload 
	 * @param receiver an optional callback receiver
	 * @throws JSONException some JSON exception occured
	 */
	public void sendMsgToWebView(String command, JSONObject data, ReplyReceiver receiver) throws JSONException;
	
	/**
	 * Send reply to debug service.
	 *
	 * @param replyId the reply id
	 * @param data the data
	 */
	public void sendReplyToDebugService(int replyId, String data);
	
	/**
	 * Gets the queued message. Will be called by the webview. 
	 *
	 * @param wait true will block the call till new data is available
	 * @return the queued message
	 * @throws InterruptedException the interrupted exception
	 */
	public String getQueuedMessage(boolean wait) throws InterruptedException;
}
