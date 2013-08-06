package org.jshybugger.server;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

public class DatabaseMsgHandler extends AbstractMsgHandler {

	public DatabaseMsgHandler(DebugSession debugSession) {
		super(debugSession, "Database");
	}
	
	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		if ("enable".equals(method)) {
			sendAckMessage(conn, message);
			debugSession.getBrowserInterface().sendMsgToWebView(
					"Database.enable", new JSONObject(), null);

		} else if ("getDatabaseTableNames".equals(method)) {
			getDatabaseTableNames(conn, message);

		} else if ("executeSQL".equals(method)) {
			executeSQL(conn, message);
			
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
		
		if ("addDatabase".equals(method)) {
			addDatabase(conn, message);
			
		} else {
			super.onSendMessage(conn, method, message);
		}
	}

	private void executeSQL(final WebSocketConnection conn,
			final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"executeSQL",
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

	private void getDatabaseTableNames(final WebSocketConnection conn,
			final JSONObject message) throws JSONException {
		
		debugSession.getBrowserInterface().sendMsgToWebView(
				"getDatabaseTableNames",
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

	private void addDatabase(final WebSocketConnection conn, final JSONObject message) throws JSONException {
		if (conn != null) {
			conn.send(new JSONStringer().object()
					.key("method").value("Database.addDatabase")
					.key("params").value(message)
				.endObject()
			.toString());
		}				
	}	
}
