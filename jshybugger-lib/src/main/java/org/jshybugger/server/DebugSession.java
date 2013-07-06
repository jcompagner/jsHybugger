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

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.jshybugger.DebugContentProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebSocketConnection;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;


/**
 * The DebugServer is the heart of the whole system. 
 * It's the mediator between the app webview and the debugging frontend.
 */
public class DebugSession extends BaseWebSocketHandler {

	/** The Constant TAG. */
	private static final String TAG = "DebugServer";
	
	/** The message handler list. */
	private final HashMap<String,MessageHandler> HANDLERS = new HashMap<String,MessageHandler>(); 
	
	/** The application context. */
	protected Context application;
	
	/** The client connection list. */
	private List<WebSocketConnection> connections = new ArrayList<WebSocketConnection>(); 		
	
	/** The browser API interface. */
	private BrowserInterface browserInterface;
	
	public final String PROVIDER_PROTOCOL;

	private final String sessionId;
	
	
	/**
	 * Instantiates a new debug server.
	 *
	 * @param application the application context
	 * @throws UnknownHostException the unknown host exception
	 */
	public DebugSession( Context application ) throws UnknownHostException {
		this.application = application;
		PROVIDER_PROTOCOL = DebugContentProvider.getProviderProtocol(application);
		
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
		
		//msgHandler = new DOMMsgHandler(this);
		//HANDLERS.put(msgHandler.getObjectName(), msgHandler);

		//msgHandler = new CssMsgHandler(this);
		//HANDLERS.put(msgHandler.getObjectName(), msgHandler);
		sessionId = UUID.randomUUID().toString();
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
			MessageHandler handler = getMessageHandler(method[0]);
			
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
		MessageHandler handler = getMessageHandler(method[0]);
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
	 * @param encode true to use base64 encoding
	 * @return the file resource content 
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public String loadScriptResourceById(String scriptUri, boolean encode) throws IOException {
		
		Log.d(TAG, "loadScriptResourceById: " + scriptUri);
		
		Cursor cursor = application.getContentResolver().query(Uri.parse(PROVIDER_PROTOCOL + scriptUri), 
				new String[] { encode ? "scriptSourceEncoded" : "scriptSource" }, 
				DebugContentProvider.ORIGNAL_SELECTION, 
				null, 
				null);
		
		String resourceContent=null;
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				resourceContent = cursor.getString(0);
			}
			cursor.close();
		}
		
		Log.d(TAG, "loadScriptResourceById - length: " + (resourceContent != null ? resourceContent.length() : 0));
		
		return resourceContent;
	}

	public String getSessionId() {
		return sessionId;
	}
}
