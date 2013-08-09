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
package org.jshybugger.proxy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jshybugger.server.AbstractBrowserInterface;
import org.webbitserver.HttpResponse;

/**
 * This class is the interface between the browser and the debugging service.
 * 
 */
public class JSDInterface extends AbstractBrowserInterface {

	private boolean notifyBrowser = false;
	private HttpResponse response;
	private ExecutorService executorService;
	
	/**
	 * Instantiates a new jSD interface.
	 */
	public JSDInterface() {
		super(5000);
		executorService = Executors.newFixedThreadPool(1);
	}
		

	@Override
	public void notifyBrowser() {
		synchronized (this) {
			if (response != null) {
			//	Log.d(TAG, "notifyBrowser(): " +response);
				sendNotifyMessage(response, "JsHybugger.processMessages(false);");
				response = null;
			} else {
				notifyBrowser = true;
			}
			return;
		}
	}

	public void openPushChannel(final HttpResponse res) {
		synchronized (this) {
			if (notifyBrowser) {
			//	Log.d(TAG, "notifyBrowser(openPushChannel): " + res);
				sendNotifyMessage(res, "JsHybugger.processMessages(false);");
				notifyBrowser=false;
				response=null;
			} else {
				response = res;
			}
		}
	}

	private void sendNotifyMessage(HttpResponse res, String data) {
		//Log.d(TAG, "sendNotifyMessage: " + data);
		try {
			if (data != null) {
				res.status(200);
				res.content(data);
			} else {
				res.status(204);
			}
	
			res.end();
		} catch (Exception e) {
//			Log.w(TAG, "sendNotifyMessage failed. "  + e);
		}
	}

	public void getQueuedMessage(final HttpResponse res, final boolean wait) {
		if (wait) {
		
			executorService.execute(new Runnable() {

				@Override
				public void run() {
					String queuedMessage = JSDInterface.this.getQueuedMessage(true);
					sendNotifyMessage(res, queuedMessage);
				}
				
			});
		} else {
			String queuedMessage = super.getQueuedMessage(false);
			sendNotifyMessage(res, queuedMessage);
		}
	}


	public void stop() {
		executorService.shutdownNow();
	}
}
