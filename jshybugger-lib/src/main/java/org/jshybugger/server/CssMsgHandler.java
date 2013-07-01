package org.jshybugger.server;

import org.json.JSONException;
import org.json.JSONObject;
import org.webbitserver.WebSocketConnection;

public class CssMsgHandler extends AbstractMsgHandler {

	public CssMsgHandler(DebugSession debugSession) {
		super(debugSession, "CSS");
	}

	/* (non-Javadoc)
	 * @see de.cyberflohrs.jshybugger.server.AbstractMsgHandler#onReceiveMessage(org.webbitserver.WebSocketConnection, java.lang.String, org.json.JSONObject)
	 */
	@Override
	public void onReceiveMessage(WebSocketConnection conn, String method, JSONObject message) throws JSONException {
		
		if ("enable".equals(method)) {
			sendAckMessage(conn, message);
			
		} else if ("getSupportedCSSProperties".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
		
		} else if ("getMatchedStylesForNode".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
		
		} else if ("getInlineStylesForNode".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);

		} else if ("getComputedStyleForNode".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
			
		} else if ("toggleProperty".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
			
		}  else if ("setPropertyText".equals(method)) {
			dispatchToBrowserAndReply(conn, method, message);
		}
	}
}
