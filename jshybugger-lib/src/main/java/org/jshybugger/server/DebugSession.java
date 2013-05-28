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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebSocketConnection;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;


/**
 * The DebugServer is the heart of the whole system. 
 * It's the mediator between the app webview and the debugging frontend.
 */
public class DebugSession extends BaseWebSocketHandler {

	/** The Constant TAG. */
	private static final String TAG = "DebugServer";
	
	/** The Constant ANDROID_ASSET_URL. */
	private static final String ANDROID_ASSET_URL = "file:///android_asset/";
	
	/** The message handler list. */
	private final HashMap<String,MessageHandler> HANDLERS = new HashMap<String,MessageHandler>(); 
	
	/** The application context. */
	protected Context application;
	
	/** The client connection list. */
	private List<WebSocketConnection> connections = new ArrayList<WebSocketConnection>(); 		
	
	/** The browser API interface. */
	private BrowserInterface browserInterface;
	
	/**
	 * Instantiates a new debug server.
	 *
	 * @param application the application context
	 * @throws UnknownHostException the unknown host exception
	 */
	public DebugSession( Context application ) throws UnknownHostException {
		this.application = application;
		
		MessageHandler msgHandler = new DebuggerMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);

		msgHandler = new ConsoleMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);
		
		msgHandler = new RuntimeMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);

		msgHandler = new PageMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);

		msgHandler = new DOMStorageMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);

		msgHandler = new DatabaseMsgHandler(this);
		HANDLERS.put(msgHandler.getObjectName(), msgHandler);
	}
	
	public void setBrowserInterface(BrowserInterface browserInterface) {
		browserInterface.setDebugSession(this);
		this.browserInterface = browserInterface;
	}	
	
	/**
	 * Gets the browser interface.
	 *
	 * @return the browser interface
	 */
	public BrowserInterface getBrowserInterface() {
		return browserInterface;
	}
	
	/* (non-Javadoc)
	 * @see org.webbitserver.BaseWebSocketHandler#onOpen(org.webbitserver.WebSocketConnection)
	 */
	@Override
	public void onOpen( final WebSocketConnection conn ) {
		System.out.println( conn.httpRequest().remoteAddress() + " entered the debugger space!" );
		connections.add(conn);

		try {
			getBrowserInterface().sendMsgToWebView(
					"ClientConnected",
					new JSONObject(),
					null);
			
		} catch (JSONException e) {
			Log.e(TAG, "Notify ClientConnected failed", e);
		}		
		
	}

	/* (non-Javadoc)
	 * @see org.webbitserver.BaseWebSocketHandler#onClose(org.webbitserver.WebSocketConnection)
	 */
	@Override
	public void onClose( WebSocketConnection conn) {
		System.out.println( conn + " has left the debugger space!" );
		connections.remove(conn);
	}

	/* (non-Javadoc)
	 * @see org.webbitserver.BaseWebSocketHandler#onMessage(org.webbitserver.WebSocketConnection, java.lang.String)
	 */
	@Override
	public void onMessage( WebSocketConnection conn, String strMessage ) {
		try {
			JSONObject message = new JSONObject(strMessage);
				
			String[] method = message.getString("method").split("[\\.]");
			MessageHandler handler = HANDLERS.get(method[0]);
			
			if (handler != null) {
				handler.onReceiveMessage(conn, method[1], message);
						
			} else {
				conn.send(
						new JSONStringer().object()
					.key("id").value(message.getInt("id"))
					.key("result").object().key("result").value(false).endObject()
					.endObject().toString());
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Dispatch message to message handlers.
	 *
	 * @param handlerMethod the handler method
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	public void sendMessage(String handlerMethod, JSONObject message ) throws JSONException {
		String[] method = handlerMethod.split("[\\.]");
		MessageHandler handler = HANDLERS.get(method[0]);
		if (handler != null) {
			sendHandlerMessage(message, method[1], handler);
		} else if (method.length == 1) {
			for (MessageHandler allHandler : HANDLERS.values()) {
				sendHandlerMessage(message, method[0], allHandler);
			}
		} else {
			Log.e(TAG, "sendMessage no handler found: " + handlerMethod);
		}
	}
	
	/**
	 * Gets the message handler by name.
	 *
	 * @param handlerName the handler name
	 * @return the message handler
	 */
	public MessageHandler getMessageHandler(String handlerName) {
		return HANDLERS.get(handlerName);
	}

	/**
	 * Send message to message handler.
	 *
	 * @param message the message to process
	 * @param method the handler method name 
	 * @param handler the handler reference
	 * @throws JSONException some JSON exception
	 */
	private void sendHandlerMessage(JSONObject message, String method,
			MessageHandler handler) throws JSONException {
		if (connections.isEmpty()) {
			handler.onSendMessage(null, method, message);
		} else {
			for (WebSocketConnection conn : connections) {
				handler.onSendMessage(conn, method, message);
			}
		}
	}

	/**
	 * Load script resource by URI.
	 *
	 * @param scriptUri the script URI to load
	 * @return the javascript content 
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public String loadScriptResourceById(String scriptUri) throws IOException {
		
		Log.d(TAG, "loadScriptResourceById: " + scriptUri);
		
		InputStream resource = openInputFile(scriptUri);
		BufferedReader br = new BufferedReader(new InputStreamReader(resource));
		
		String strLine;
		StringBuffer sb = new StringBuffer();

		//Read File Line By Line
		while ((strLine = br.readLine()) != null)   {
			sb.append(strLine);
			sb.append("\r\n");
		}
		br.close();
		Log.d(TAG, "loadScriptResourceById - length: " + sb.length());
		
		return sb.toString();
	}
	
	/**
	 * Open file resource as stream.
	 *
	 * @param url the file url
	 * @return the buffered input stream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected BufferedInputStream openInputFile(String url) throws IOException {

		BufferedInputStream resource = null;
        if (url.startsWith(ANDROID_ASSET_URL)) {
        	url = url.substring(ANDROID_ASSET_URL.length());
    		resource = new BufferedInputStream(application.getAssets().open(url,AssetManager.ACCESS_STREAMING));

        } else if (url.indexOf(":") < 0) {
    		resource = new BufferedInputStream(application.getAssets().open(url,AssetManager.ACCESS_STREAMING));

        } else {
    		resource = new BufferedInputStream(new URL(url).openStream());
        }
		return resource;
	}


}
