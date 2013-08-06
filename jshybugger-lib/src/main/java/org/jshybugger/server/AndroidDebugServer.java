package org.jshybugger.server;

import java.io.IOException;

public class AndroidDebugServer extends DebugServer {

	private DomainSocketServer domainSocketServer;

	public AndroidDebugServer(int debugPort, String domainSocketName)
			throws IOException {
		super(debugPort);
		
		domainSocketServer =  new DomainSocketServer(domainSocketName, debugPort);
		domainSocketServer.start();
	}

	@Override
	public void stop() {
		super.stop();
		if (domainSocketServer != null) {
			domainSocketServer.stopSocketServer();
		}
	}

}
