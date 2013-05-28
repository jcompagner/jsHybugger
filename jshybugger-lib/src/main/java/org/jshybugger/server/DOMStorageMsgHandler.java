package org.jshybugger.server;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

public class DOMStorageMsgHandler extends AbstractMsgHandler {

	public DOMStorageMsgHandler(DebugSession debugSession) {
		super(debugSession, "DOMStorage");
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		
		if ("enable".equals(method)) {
			sendAckMessage(conn, message);
			
		} else if ("getDOMStorageItems".equals(method)) {
			getDOMStorageItems(conn, message);
		
		} else if ("removeDOMStorageItem".equals(method)) {
			removeDOMStorageItem(conn, message);

		} else if ("setDOMStorageItem".equals(method)) {
			setDOMStorageItem(conn, message);
		}
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message)
			throws JSONException {
		
		if ("domStorageItemAdded".equals(method)) {
			sendDOMStorageItemAdded(conn, message);
			
		} else if ("domStorageItemUpdated".equals(method)) {
			sendDOMStorageItemUpdated(conn, message);
			
		} else if ("domStorageItemRemoved".equals(method)) {
			sendDOMStorageItemRemoved(conn, message);
			
		} else if ("domStorageItemsCleared".equals(method)) {
			sendDOMStorageItemsCleared(conn, message);

		} else {
			super.onSendMessage(conn, method, message);
		}
	}
	
	private void sendDOMStorageItemsCleared(WebSocketConnection conn,
			JSONObject message) throws JSONException {

		if (conn != null) {
			conn.send(new JSONStringer().object()
					.key("method").value("DOMStorage.domStorageItemsCleared")
					.key("params").value(message)
				.endObject()
			.toString());
		}				
	}

	private void sendDOMStorageItemRemoved(WebSocketConnection conn,
			JSONObject message) throws JSONException {
		
		if (conn != null) {
			conn.send(new JSONStringer().object()
					.key("method").value("DOMStorage.domStorageItemRemoved")
					.key("params").value(message)
				.endObject()
			.toString());
		}		
	}

	private void sendDOMStorageItemUpdated(WebSocketConnection conn,
			JSONObject message) throws JSONException {

		if (conn != null) {
			conn.send(new JSONStringer().object()
					.key("method").value("DOMStorage.domStorageItemUpdated")
					.key("params").value(message)
				.endObject()
			.toString());
		}		
	}

	private void sendDOMStorageItemAdded(WebSocketConnection conn,
			JSONObject message) throws JSONException {
		
		if (conn != null) {
			conn.send(new JSONStringer().object()
					.key("method").value("DOMStorage.domStorageItemAdded")
					.key("params").value(message)
				.endObject()
			.toString());
		}		
	}

	private void getDOMStorageItems(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"getDOMStorageItems",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").value(data)
						.endObject().toString());
			}
		});
	}
	
	private void setDOMStorageItem(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"setDOMStorageItem",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").value(data)
						.endObject().toString());
			}
		});
	}

	private void removeDOMStorageItem(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"removeDOMStorageItem",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
						.key("id").value(message.getInt("id"))
						.key("result").value(data)
						.endObject().toString());
			}
		});
	}
	
}
