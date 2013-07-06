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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.jshybugger.server.DebugServer;
import org.jshybugger.server.DebugSession;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.webkit.WebView;

/**
 * The Class DebugService.
 */
public class DebugService extends Service {

	/** The Constant MSG_WEBVIEW_ATTACHED. */
	public final static int MSG_WEBVIEW_ATTACHED = 1;
			
    /** Binder given to clients */
    private final IBinder mBinder = new LocalDebugService();
	
	/** The callback handler. */
	private List<Messenger> callbackHandler = new ArrayList<Messenger>();

	private DebugSession debugSession;
	
	/** The logging TAG */
	private static final String TAG = "DebugService";
	
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


	/* (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		try {
			int debugPort = 8888;
			String domainSocketName = "jsHybugger.";
			
			ServiceInfo info = getApplicationContext().getPackageManager().getServiceInfo(new ComponentName(getApplicationContext(), DebugService.class), PackageManager.GET_SERVICES|PackageManager.GET_META_DATA);
			Bundle metaData = info.metaData;
			if (metaData != null) {
				if (metaData.getInt("debugPort", 0) > 0) {
					debugPort = metaData.getInt("debugPort");
				} 
				domainSocketName = metaData.getString("domainSocketName") + "."; // fix because GUI truncates last character
			}			
			
			DebugServer debugServer = new DebugServer( debugPort, domainSocketName );
			debugSession = new DebugSession(this);
			
			debugServer.exportSession(debugSession);
		} catch (UnknownHostException e) {
			Log.e(TAG, "DebugService creation failed: " + e.getMessage());
		} catch (InterruptedException e) {
			Log.e(TAG, "DebugService creation failed: " + e.getMessage());
		} catch (NameNotFoundException e) {
			Log.e(TAG, "DebugService creation failed: " + e.getMessage());
		}
	}
	
	/* (non-Javadoc)
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startid) {
		//Log.d(TAG, "onStart: "+ intent);
		extractCallbackHandler(intent);
	}


	/**
	 * Extract callback handler.
	 *
	 * @param intent the intent
	 */
	private void extractCallbackHandler(Intent intent) {
		if ((intent != null) && (intent.getExtras() != null) && (intent.getExtras().get("callback") != null)) {
			callbackHandler.add((Messenger) intent.getExtras().get("callback"));
		}
	}
	
	
	/* (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		//Log.d(TAG, "onStartCommand: " + intent);
		extractCallbackHandler(intent);
		return Service.START_NOT_STICKY;
	}
	
	/**
	 * Notify all registered handlers to this debug service.
	 *
	 * @param message the message
	 */
	private void notifyHandlers(Message message) {
		
		for (Messenger callback : callbackHandler) {
			try {
				callback.send(message);
			} catch (RemoteException e) {
				Log.e(TAG, "Can't notify service: " + e);
				callbackHandler.remove(callback);
			}
		}
	}

	/**
	 * Attach web view to debug service.
	 *
	 * @param webView the web view to attach
	 * @param activity the activity
	 */
	public void attachWebView(WebView webView, Activity activity) {

		JSDInterface browserInterface = JSDInterface.getJSDInterface();
		browserInterface.setActivity(activity);
		browserInterface.setWebView(webView);
		
		debugSession.setBrowserInterface(browserInterface);
		
		if (Build.VERSION.SDK_INT >= 16) {  
		    Method method;
			try {
				method = WebView.class.getMethod("setAllowUniversalAccessFromFileURLs", boolean.class);
			    if (method != null) {
			        method.invoke(webView.getSettings(), true);
			    }
			} catch (NoSuchMethodException e) {
				//Log.d(TAG, "setAllowUniversalAccessFromFileURLs() for webview failed", e);
			} catch (IllegalArgumentException e) {
				//Log.e(TAG, "setAllowUniversalAccessFromFileURLs() for webview failed", e);
			} catch (IllegalAccessException e) {
				//Log.e(TAG, "setAllowUniversalAccessFromFileURLs() for webview failed", e);
			} catch (InvocationTargetException e) {
				//Log.e(TAG, "setAllowUniversalAccessFromFileURLs() for webview failed", e);
			}
		}		
		notifyHandlers(Message.obtain(null, MSG_WEBVIEW_ATTACHED));
	}
	
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalDebugService extends Binder {
    	
	    /**
	     * Gets the service.
	     *
	     * @return the service
	     */
	    DebugService getService() {
            // Return this instance of DebugService so clients can call public methods
            return DebugService.this;
        }
    }
}
