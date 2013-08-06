package org.jshybugger.server;

import java.io.IOException;

public class AndroidDebugServer extends DebugServer {

	public AndroidDebugServer(int debugPort, String domainSocketName)
			throws IOException {
		super(debugPort, domainSocketName);
	}
	
	@Override
	protected AndroidDomainSocketServer createDomainSocketServer(
			String domainSocketName, int debugPort) throws IOException {
		return new AndroidDomainSocketServer(domainSocketName, debugPort);
	}

}
