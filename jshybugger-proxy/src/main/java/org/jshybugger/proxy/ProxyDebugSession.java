package org.jshybugger.proxy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import org.jshybugger.server.DebugSession;

import android.content.Context;

public class ProxyDebugSession extends DebugSession {

	public ProxyDebugSession(Context application) throws UnknownHostException {
		super(application);
	}

	/**
	 * Open file resource as stream.
	 *
	 * @param url the file url
	 * @return the buffered input stream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected BufferedInputStream openInputFile(String url) throws IOException {
		
		return new BufferedInputStream(
				application.openFileInput(DebugInstrumentationHandler.getInstrumentedFileName(url, null)));
	}
}
