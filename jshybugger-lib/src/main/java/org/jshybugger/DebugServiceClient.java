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

import org.jshybugger.DebugService.LocalDebugService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.webkit.WebView;


/**
 * The Class DebugServiceClient is used by clients to attach a webview to the debugging service.
 */
public class DebugServiceClient {

    /** The Constant TAG. */
    private static final String TAG = "DebugServiceClient";

    /** The web view. */
    private WebView webView;
	
	/** The activity. */
	private Activity activity;
	
    /** Defines callbacks for service binding, passed to bindService(). */
    private ServiceConnection mConnection = new ServiceConnection() {

		private DebugService mService;

		@Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
			Log.d(TAG, "connected to debug service");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalDebugService binder = (LocalDebugService) service;
            mService = binder.getService();
            mService.attachWebView(webView, activity);
            
            activity.unbindService(mConnection);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
			Log.d(TAG, "disconnected from debug service");
			mService = null;
        }
    };

	/**
	 * Instantiates a new debug service client.
	 *
	 * @param webView the web view
	 * @param activity the activity
	 */
	private DebugServiceClient(WebView webView, Activity activity) {
		this.activity = activity;
		this.webView = webView;
	}

	/**
	 * Start debugging service.
	 */
	private void startService()  {
		Intent service = new Intent(activity, DebugService.class);
		activity.startService(service);
		activity.bindService(service, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	/**
	 * Attach web view to debugging service.
	 *
	 * @param webView the web view to attach
	 * @param activity the activity for this webview
	 */
	public static void attachWebView(WebView webView, Activity activity) {
		
		new DebugServiceClient(webView, activity).startService();
		webView.addJavascriptInterface(JSDInterface.getJSDInterface(), "JsHybuggerNI");
	}
}
