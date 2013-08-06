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
 * The ReplyReceiver is a simple interface for processing asynchronous feedback. 
 * It is used when a message is received from the debugging frontend and forwarded to the webview, and some feedback
 * has to be sent back after the webview has processed the message.  
 */
public interface ReplyReceiver {

	/**
	 * On reply.
	 *
	 * @param data some JSON data
	 * @throws JSONException the jSON exception
	 */
	void onReply(JSONObject data) throws JSONException;
}
