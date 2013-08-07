package org.jshybugger.proxy;

import java.io.IOException;

public interface ScriptSourceProvider {

	public String loadScriptResourceById(String scriptUri, boolean encode) throws IOException;
}
