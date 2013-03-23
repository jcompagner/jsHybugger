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
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.util.Log;
import android.util.SparseArray;

/**
 * This class is the interface between the webview and the debugging service.
 * 
 */
public abstract class AbstractBrowserInterface implements BrowserInterface {

	/** The Constant TAG. */
	private final static String TAG = "JSDInterface";
	
	/** The debug session server. */
	private DebugSession debugSession;
	
	/** The message queue. */
	private List<String> messageQueue = new ArrayList<String>(); 
	
	/** The reply identifier. */
	private int replyIdentifier=0;
	
	/** The reply receivers. */
	private SparseArray<ReplyReceiver> replyReceivers = new SparseArray<ReplyReceiver>();
	
	/** The sync queue mode. */
	private boolean syncQueueMode = false;
	
	/**
	 * Instantiates a new jSD interface.
	 */
	protected AbstractBrowserInterface() {
	}
		
	/**
	 * Gets the debug session.
	 *
	 * @return the debug session
	 */
	public DebugSession getDebugSession() {
		return debugSession;
	}

	/**
	 * Sets the debug session.
	 *
	 * @param debugSession the new debug session
	 */
	public void setDebugSession(DebugSession debugSession) {
		this.debugSession = debugSession;
	}

	/**
	 * Send message to debug service.
	 *
	 * @param path the message handler name i.e. "Debugger.sendPaused"
	 * @param data the JSON data
	 */
	public void sendToDebugService(String path, String data) {
		
		try {
			JSONObject msg = new JSONObject(data);
			debugSession.sendMessage(path, msg);
			
		} catch (JSONException e) {
			Log.e(TAG, "sendToServer failed for path: " + path, e);
		}
	}

	/**
	 * Send message to webview.
	 *
	 * @param command the command
	 * @param data the JSON payload 
	 * @param receiver an optional callback receiver
	 * @throws JSONException some JSON exception occured
	 */
	public void sendMsgToWebView(String command, JSONObject data, ReplyReceiver receiver) throws JSONException {
		
		synchronized (messageQueue) {
			if (receiver != null) {
				replyReceivers.put(++replyIdentifier, receiver);
			}
			messageQueue.add(new JSONStringer().object()
					.key("command").value(command)
					.key("data").value(data)
					.key("replyId").value(receiver != null ? replyIdentifier : 0)
					.endObject().toString());
			messageQueue.notifyAll();
		
			if (!syncQueueMode) {
				notifyBrowser();
			}
		}
	}
	
	public abstract void notifyBrowser();

	/**
	 * Send reply to debug service.
	 *
	 * @param replyId the reply id
	 * @param data the data
	 */
	public void sendReplyToDebugService(int replyId, String data) {
		
		ReplyReceiver rec = replyReceivers.get(replyId);
		if (rec != null) {
			try {
				rec.onReply(new JSONObject(data));
			} catch (JSONException e) {
				Log.e(TAG, "replyToServer failed for replyId: " + replyId, e);
			}
		}
		replyReceivers.remove(replyId);
	}
	
	/**
	 * Gets the queued message. Will be called by the webview. 
	 *
	 * @param wait true will block the call till new data is available
	 * @return the queued message
	 * @throws InterruptedException the interrupted exception
	 */
	public String getQueuedMessage(boolean wait) throws InterruptedException {
		synchronized (messageQueue) {
			syncQueueMode = wait;
			
			if (messageQueue.size()>0) {
				return messageQueue.remove(0);
			}
			
			if (wait) {
				while (messageQueue.isEmpty()) {
					messageQueue.wait();
				}
				
				return messageQueue.remove(0);
			}
		}
		
		return null;
	}
}
