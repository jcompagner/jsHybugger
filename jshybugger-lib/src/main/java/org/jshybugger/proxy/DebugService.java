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

import java.io.IOException;

import org.jshybugger.server.AndroidDebugServer;
import org.jshybugger.server.AndroidDebugSession;
import org.jshybugger.server.DebugServer;
import org.jshybugger.server.DebugSession;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * The Class DebugService.
 */
public class DebugService extends Service {

	private DebugSession debugSession;

	JSDInterface browserInterface;

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
			int debugPort = 8888;
			String domainSocketName = "jsHybuggerProxy";
		
			browserInterface = new JSDInterface();
			debugServer = new AndroidDebugServer( debugPort, domainSocketName );
			debugServer.addHandler("/jshybugger/.*", new JSHybuggerResourceHandler(browserInterface));
			
			debugSession = new AndroidDebugSession(this);
			debugSession.setBrowserInterface(browserInterface);
			
			debugServer.exportSession(debugSession);
//			LogActivity.addMessage("DebugServer listening on port " + debugPort);			

		} catch (IOException ie) {
//			LogActivity.addMessage("Starting DebugServer failed: " + ie.toString());			
			Log.e(TAG, "onCreate() failed", ie);
		} catch (InterruptedException e) {
//			LogActivity.addMessage("Starting DebugServer failed: " + e.toString());			
			Log.e(TAG, "DebugService.onCreate() failed", e);
		}
	}
}
