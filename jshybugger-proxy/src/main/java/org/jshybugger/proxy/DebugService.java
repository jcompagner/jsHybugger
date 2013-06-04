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
package org.jshybugger.proxy;

import java.io.BufferedInputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.jshybugger.server.DebugServer;
import org.jshybugger.server.DebugSession;
import org.json.JSONObject;
import org.webbitserver.HttpControl;
import org.webbitserver.HttpHandler;
import org.webbitserver.HttpRequest;
import org.webbitserver.HttpResponse;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * The Class DebugService.
 */
public class DebugService extends Service {

	private DebugSession debugSession;

	private JSDInterface browserInterface;

	private DebugServer debugServer;
	
	/** The logging TAG */
	private static final String TAG = "DebugService";
	
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


	/* (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate()");
		try {
			debugServer = new DebugServer( 8888 );
			debugServer.addHandler("/jshybugger/.*", new JSHybuggerResourceHandler());
			
			debugSession = new ProxyDebugSession(this);
			browserInterface = new JSDInterface();
			debugSession.setBrowserInterface(browserInterface);
			
			debugServer.exportSession(debugSession);
			LogActivity.addMessage("DebugServer listening on port 8888");			
		} catch (UnknownHostException e) {
			LogActivity.addMessage("Starting DebugServer failed: " + e.toString());			
			Log.d(TAG, "onCreate() failed", e);
		} catch (InterruptedException e) {
			LogActivity.addMessage("Starting DebugServer failed: " + e.toString());			
			Log.d(TAG, "onCreate() failed", e);
		}
	}
	
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		browserInterface.stop();
		debugServer.stop();
		LogActivity.addMessage("DebugServer stopped");			
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startid) {
		//Log.d(TAG, "onStart: "+ intent);
	}

	
	/* (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		Log.d(TAG, "onStartCommand: " + intent);
		return START_STICKY;
	}
	
    class JSHybuggerResourceHandler implements HttpHandler {

		@Override
		public void handleHttpRequest(HttpRequest req, HttpResponse res,
				HttpControl control) throws Exception {
			
			if ("OPTIONS".equals(req.method()) ) {
				res.header("Access-Control-Allow-Origin", req.header("Origin"));
				res.header("Access-Control-Allow-Methods", "POST");
				res.header("Access-Control-Allow-Headers", req.header("Access-Control-Request-Headers"));
				res.end();
				
			} else {
			
				String uri = req.uri();
				//Log.d(TAG,  "START: " + req);
				res.header("Access-Control-Allow-Origin", req.header("Origin"));
				if (uri.endsWith("jshybugger.js")) {
					res.header("Cache-control", "no-cache, no-store");
					res.header("Expires", "0");
					
					BufferedInputStream bis = new BufferedInputStream(DebugServer.class.getClassLoader().getResourceAsStream("jshybugger.js"));
					
					ByteBuffer buffer = ByteBuffer.allocate(bis.available());
					bis.read(buffer.array());
					bis.close();
					
					res.content(buffer);
					
				} else if (uri.endsWith("sendToDebugService")) {
										
					JSONObject jsonReq = new JSONObject(req.body());
					browserInterface.sendToDebugService(jsonReq.getString("arg0"), jsonReq.getString("arg1"));
					
				} else if (uri.endsWith("sendReplyToDebugService")) {
										
					JSONObject jsonReq = new JSONObject(req.body());
					browserInterface.sendReplyToDebugService(jsonReq.getInt("arg0"), jsonReq.getString("arg1"));

				} else if (uri.endsWith("getQueuedMessage")) {
					res.chunked();
					
					JSONObject jsonReq = new JSONObject(req.body());
					browserInterface.getQueuedMessage(res, jsonReq.getBoolean("arg0"));
					return;
					
				} else if (uri.endsWith("pushChannel")) {

					res.chunked();
					browserInterface.openPushChannel(res);
					//Log.d(TAG,  "END: " + req);
					return;
					
				} else {
					res.status(204);
				}
				res.end();
			}
		}
    }
}
