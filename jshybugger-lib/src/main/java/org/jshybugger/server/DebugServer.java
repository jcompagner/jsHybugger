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

import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;

import org.json.JSONException;
import org.json.JSONStringer;
import org.webbitserver.HttpControl;
import org.webbitserver.HttpHandler;
import org.webbitserver.HttpRequest;
import org.webbitserver.HttpResponse;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;


/**
 * The DebugServer is the heart of the whole system. 
 * It's the mediator between the app webview and the debugging frontend.
 */
public class DebugServer {

	private static final String CHROME_DEVTOOLS_FRONTEND = "https://chrome-devtools-frontend.appspot.com/static/27.0.1453.90/devtools.html?ws=%s/devtools/page/%s";
	private WebServer webServer;
	private CountDownLatch debugServerStarted = new CountDownLatch(1);
	
	/**
	 * Instantiates a new debug server.
	 *
	 * @param port the tcp listen port number
	 * @param application the application context
	 * @throws UnknownHostException the unknown host exception
	 */
	public DebugServer( int port) throws UnknownHostException {
		
		Thread webServerThread = new Thread(new Runnable() {

			@Override
			public void run() {
				
				webServer = WebServers.createWebServer( 8888)
	                .add("/", new HttpHandler() {
	
	                    @Override
	                    public void handleHttpRequest(HttpRequest request, HttpResponse response, HttpControl control) {
	                        response.status(301).header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
	                        	.header("Location", String.format(CHROME_DEVTOOLS_FRONTEND, request.header("Host"), "1")).end();
	                    }
	                })
	                .add("/json", new HttpHandler() {
	                    @Override
	                    public void handleHttpRequest(HttpRequest request, HttpResponse response, HttpControl control) {
	                    	try {
								String res = new JSONStringer().array().object()
								
										.key("devtoolsFrontendUrl").value(String.format(CHROME_DEVTOOLS_FRONTEND, request.header("Host"), "1"))
										.key("faviconUrl").value("http://www.google.de/favicon.ico")
									    .key("thumbnailUrl").value("/thumb/")
									    .key("title").value("jsHybugger powered debugging")
									    .key("url").value("http://localhost/")
									    .key("webSocketDebuggerUrl").value("ws://" + request.header("Host") + "/devtools/page/1")
									    
									 .endObject().endArray().toString();
								
								
								response.header("Content-type", "application/json")
									.content(res)
									.end();
								
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
	                    }
	                });
				
		        webServer.start();
		        debugServerStarted.countDown();
			}
		});
		webServerThread.start();
	}

	public void exportSession(DebugSession debugSession) throws InterruptedException {
		debugServerStarted.await();
		webServer.add("/devtools/page/1", debugSession);
	}
	
	public void addHandler(String path, HttpHandler handler) throws InterruptedException {
		debugServerStarted.await();
		webServer.add(path, handler);
	}

	public void stop() {
		webServer.stop();
	}
}
