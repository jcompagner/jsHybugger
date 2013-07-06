package org.jshybugger.server;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

public class DOMMsgHandler extends AbstractMsgHandler {

	public DOMMsgHandler(DebugSession debugSession) {
		super(debugSession, "DOM");
		
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		
		if ("getDocument".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
		
		} else if ("requestChildNodes".equals(method)) {
			requestChildNodes(conn, message);
		
		} else if ("removeNode".equals(method)) {
			removeNode(conn, message);

		} else if ("markUndoableState".equals(method) ||
				"highlightNode".equals(method) ||
				"hideHighlight".equals(method)) {
			sendAckMessage(conn, message);
			
		} else {
			super.onReceiveMessage(conn, method, message);
		}
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onSendMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onSendMessage(WebSocketConnection conn, String method, JSONObject message)
			throws JSONException {
		
		if ("GlobalPageLoaded".equals(method)) {
			documentUpdated(conn, message);
			
		} else {
			super.onSendMessage(conn, method, message);
		}
	}

	/**
	 * Process "DOM.documentUpdated" protocol messages.
	 * Forwards the message to the debugger frontend. This message is triggered by loading the jsHybugger.js file. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	private void documentUpdated(WebSocketConnection conn, JSONObject msg) throws JSONException {
		
		if (conn != null) {
			
			conn.send(new JSONStringer().object()
				.key("method").value("DOM.documentUpdated")
			.toString());
		}
	}
	
	private void requestChildNodes(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"requestChildNodes",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
					.key("method").value("DOM.setChildNodes")
						.key("params").value(data)
					.endObject()
				.toString());

				sendAckMessage(conn, message);
			}
		});
	}
	
	private void removeNode(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"removeNode",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				conn.send(new JSONStringer().object()
					.key("method").value("DOM.childNodeRemoved")
						.key("params").value(data)
					.endObject()
				.toString());

				sendAckMessage(conn, message);
			}
		});
	}
}
