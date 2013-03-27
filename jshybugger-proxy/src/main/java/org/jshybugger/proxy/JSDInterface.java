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

import java.util.Timer;
import java.util.TimerTask;

import org.jshybugger.server.AbstractBrowserInterface;
import org.webbitserver.HttpResponse;

/**
 * This class is the interface between the webview and the debugging service.
 * 
 */
public class JSDInterface extends AbstractBrowserInterface {

	private boolean notifyBrowser = false;

	private Timer timer = null;
	
	/**
	 * Instantiates a new jSD interface.
	 */
	public JSDInterface() {
		super(5000);
		this.timer = new Timer();
	}
		

	@Override
	public void notifyBrowser() {
		synchronized (this) {
			notifyBrowser = true;
			notifyAll();
		}
	}

	public void openPushChannel(final HttpResponse response) {
		synchronized (this) {
			if (notifyBrowser) {
				response.content("JsHybugger.processMessages(false);");
				response.end();
				notifyBrowser=false;
				return;
			}
			
			timer.schedule(new TimerTask() {
	            @Override
	            public void run() {
	            	synchronized (JSDInterface.this) {
	            		try {
							JSDInterface.this.wait(maxWaitTime);
							if (notifyBrowser) {
								response.content("JsHybugger.processMessages(false);");
								notifyBrowser=false;
							}
						} catch (InterruptedException e) {
						} finally {
							response.end();
						}
	            	}
	            }
	        }, 0);
		}
	}

	public void stop() {
		timer.cancel();
	}
}
