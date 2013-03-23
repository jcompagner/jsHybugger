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
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

/**
 * Abstract base class for MessageHandler implementations.
 */
abstract public class AbstractMsgHandler implements MessageHandler {

	/** The object name. */
	private String OBJECT_NAME = null;
	
	/** The debug server. */
	final protected DebugSession debugSession;

	/**
	 * Instantiates a new abstract msg handler.
	 *
	 * @param debugSession the debug server
	 * @param objectName the object name
	 */
	public AbstractMsgHandler(DebugSession debugSession, String objectName) {
		OBJECT_NAME = objectName;
		this.debugSession = debugSession;
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.MessageHandler#getObjectName()
	 */
	@Override
	public String getObjectName() {
		return OBJECT_NAME;
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.MessageHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		conn.send(
				new JSONStringer().object()
			.key("id").value(message.getInt("id"))
			.key("error").value(message.getString("method") + " not implemented")
			.key("result").object().endObject()
			.endObject().toString());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((OBJECT_NAME == null) ? 0 : OBJECT_NAME.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractMsgHandler other = (AbstractMsgHandler) obj;
		if (OBJECT_NAME == null) {
			if (other.OBJECT_NAME != null)
				return false;
		} else if (!OBJECT_NAME.equals(other.OBJECT_NAME))
			return false;
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.MessageHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
	}
	
	/**
	 * Send protocol acknowledge message.
	 *
	 * @param conn the websocket conn
	 * @param message the message
	 * @throws JSONException the jSON exception
	 */
	protected void sendAckMessage(WebSocketConnection conn, JSONObject message)
			throws JSONException {
		conn.send(new JSONStringer().object()
			.key("id").value(message.getInt("id"))
			.key("result").object().endObject().endObject().toString());
	}
}
