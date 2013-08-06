package org.jshybugger.server;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.webbitserver.WebSocketConnection;

import android.content.ContentValues;
import android.net.Uri;

public class AndroidDebuggerMsgHandler extends DebuggerMsgHandler {

	public AndroidDebuggerMsgHandler(AndroidDebugSession debugSession)
	{
		super(debugSession);
	}
	
	/**
	 * Process "Debugger.setScriptSource" protocol messages.
	 * Forwards the message to the WebView and returns the result to the debugger frontend. 
	 *
	 * @param conn the websocket connection
	 * @param message the JSON message
	 * @throws JSONException some JSON exception
	 */
	@Override
	protected void setScriptSource(final WebSocketConnection conn,
			final JSONObject message)  throws JSONException {
		
		ContentValues values = new ContentValues();
		values.put("scriptSource", message.getJSONObject("params").getString("scriptSource"));
		
		Uri uri = Uri.parse(((AndroidDebugSession)this.debugSession).PROVIDER_PROTOCOL + message.getJSONObject("params").getString("scriptId"));
		
		try {
			((AndroidDebugSession)debugSession).application.getContentResolver().update(uri, values, null, null);
			AndroidDebuggerMsgHandler.this.sendAckMessage(conn, message);
		} catch (RuntimeException rex) {
			
			conn.send(new JSONStringer().object()
					.key("id").value(message.getInt("id"))
					.key("error").object()
						.key("code").value(-32000)
						.key("message").value(rex.getMessage())
						.endObject()
					.endObject().toString());
		}
		/*
		debugSession.getBrowserInterface().sendMsgToWebView(
				"setPauseOnExceptions",
				new JSONObject().put("params", message.getJSONObject("params")),
				new ReplyReceiver() {

			@Override
			public void onReply(JSONObject data) throws JSONException {
				
				DebuggerMsgHandler.this.sendAckMessage(conn, message);
			}
		});*/				
	}	
}
