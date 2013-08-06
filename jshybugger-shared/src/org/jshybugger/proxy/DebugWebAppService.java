package org.jshybugger.proxy;

import java.io.IOException;

import org.jshybugger.server.DebugServer;
import org.jshybugger.server.DebugSession;

public class DebugWebAppService {
	public static void main(String... args) throws IOException, InterruptedException {
		int debugPort = 8889;
		JSDInterface browserInterface = new JSDInterface();
		DebugServer debugServer = new DebugServer( debugPort );
		debugServer.addHandler("/jshybugger/.*", new JSHybuggerResourceHandler(browserInterface));
		
		DebugSession debugSession = new DebugSession();
		debugSession.setBrowserInterface(browserInterface);
		
		debugServer.exportSession(debugSession);
	}
}
