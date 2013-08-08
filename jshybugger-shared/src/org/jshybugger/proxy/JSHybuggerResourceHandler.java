package org.jshybugger.proxy;

import java.io.BufferedInputStream;
import java.nio.ByteBuffer;

import org.jshybugger.server.BrowserInterface;
import org.jshybugger.server.DebugServer;
import org.json.JSONObject;
import org.webbitserver.HttpControl;
import org.webbitserver.HttpHandler;
import org.webbitserver.HttpRequest;
import org.webbitserver.HttpResponse;

class JSHybuggerResourceHandler implements HttpHandler {

	private final JSDInterface browserInterface;

	/**
	 * @param debugService
	 */
	JSHybuggerResourceHandler(JSDInterface browserInterface) {
		this.browserInterface = browserInterface;
	}

	@Override
	public void handleHttpRequest(HttpRequest req, HttpResponse res,
			HttpControl control) throws Exception {
		res.header("Access-Control-Allow-Origin", "*");
		res.header("Allow", "GET, DELETE, POST, PUT, OPTIONS");
		if ("OPTIONS".equals(req.method()) ) {
			res.header("Access-Control-Allow-Methods", "GET, DELETE, POST, PUT, OPTIONS");
			String headers = req.header("Access-Control-Request-Headers");
			if (headers == null)
			{
				headers = "Allow";
			}
			else if (!headers.contains("Allow"))
			{
				headers += ", Allow";
			}
			res.header("Access-Control-Allow-Headers", headers);
			res.header("Access-Control-Expose-Headers", headers);
			res.end();
		} else {
		
			String uri = req.uri();
			//Log.d(TAG,  "START: " + req);
			if (uri.endsWith("jshybugger.js")) {
				res.header("Cache-control", "no-cache, no-store");
				res.header("Expires", "0");
				
				BufferedInputStream bis = new BufferedInputStream(DebugServer.class.getClassLoader().getResourceAsStream("jshybugger.js"));
				
				ByteBuffer buffer = ByteBuffer.allocate(bis.available());
				bis.read(buffer.array());
				bis.close();
				
				res.content(buffer);
				
			} else if (uri.endsWith("sendToDebugService")) {
									
				JSONObject jsonReq = new JSONObject(req.body());
				this.browserInterface.sendToDebugService(jsonReq.getString("arg0"), jsonReq.getString("arg1"));
				
			} else if (uri.endsWith("sendReplyToDebugService")) {
									
				JSONObject jsonReq = new JSONObject(req.body());
				this.browserInterface.sendReplyToDebugService(jsonReq.getInt("arg0"), jsonReq.getString("arg1"));

			} else if (uri.endsWith("getQueuedMessage")) {
				res.chunked();
				
				JSONObject jsonReq = new JSONObject(req.body());
				this.browserInterface.getQueuedMessage(res, jsonReq.getBoolean("arg0"));
				return;
				
			} else if (uri.endsWith("pushChannel")) {

				res.chunked();
				this.browserInterface.openPushChannel(res);
				//Log.d(TAG,  "END: " + req);
				return;
				
			} else {
				res.status(204);
			}
			res.end();
		}
	}
}