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
package org.jshybugger;

import org.jshybugger.server.AbstractBrowserInterface;
import org.jshybugger.server.ReplyReceiver;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * This class is the interface between the webview and the debugging service.
 * 
 */
public class JSDInterface extends AbstractBrowserInterface {

	/** The web view. */
	private WebView webView;
	
	/** The activity. */
	private Activity activity;
	
	/** The jsdi interface. */
	private static JSDInterface jsdiInterface = new JSDInterface();
	
	/**
	 * Instantiates a new jSD interface.
	 */
	private JSDInterface() {
		super(0);
	}
		
	/**
	 * Gets the jSD interface.
	 *
	 * @return the jSD interface
	 */
	public static JSDInterface getJSDInterface() {
		return jsdiInterface;
	}
	
	/**
	 * Gets the web view.
	 *
	 * @return the web view
	 */
	public WebView getWebView() {
		return webView;
	}

	/**
	 * Sets the web view.
	 *
	 * @param webView the new web view
	 */
	public void setWebView(WebView webView) {
		this.webView = webView;
	}

	/**
	 * Gets the activity.
	 *
	 * @return the activity
	 */
	public Activity getActivity() {
		return activity;
	}

	/**
	 * Sets the activity.
	 *
	 * @param activity the new activity
	 */
	public void setActivity(Activity activity) {
		this.activity = activity;
	}

	@Override
	public void notifyBrowser() {
        final Runnable runnable = new Runnable() {
            public void run() {
            	webView.loadUrl("javascript:JsHybugger.processMessages(false)");
            }                
        };
        activity.runOnUiThread(runnable);
	}
	
	@Override
	@JavascriptInterface
	public void sendMsgToWebView(String command, JSONObject data,
			ReplyReceiver receiver) throws JSONException {
		super.sendMsgToWebView(command, data, receiver);
	}
	
	@Override
	@JavascriptInterface
	public String getQueuedMessage(boolean wait) throws InterruptedException {
		return super.getQueuedMessage(wait);
	}
	
	@Override
	@JavascriptInterface
	public void sendReplyToDebugService(int replyId, String data) {
		super.sendReplyToDebugService(replyId, data);
	}
	
	@Override
	@JavascriptInterface
	public void sendToDebugService(String path, String data) {
		super.sendToDebugService(path, data);
	}
	
	
}
