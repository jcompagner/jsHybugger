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
package org.jshybugger.instrumentation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

import android.util.Log;

/**
 * The JsCodeLoader is a helper class to make javascript files debug-able. 
 */
public class JsCodeLoader {

	
	/** The Constant PARSER_THREAD_STACKSIZE. */
	private static final int PARSER_THREAD_STACKSIZE = 32000;
	
	/** The Constant TAG. */
	private static final String TAG = "JsCodeLoader";

	/**
	 * Instrument javascript file.
	 *
	 * @param inputFile the input file
	 * @param scriptUri the script uri
	 * @param outputStream the output stream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void instrumentFile(final InputStream inputFile, final String scriptUri, final OutputStream outputStream) throws IOException  {
	
		final InputStreamReader inputStreamReader = new InputStreamReader(inputFile);
		try {
			final CountDownLatch startSignal = new CountDownLatch(1);
			final List<IOException> parseExceptions = new ArrayList<IOException>();
			
			// parsing must be done in extra thread, because of demand for high stack size by rhino parser 
			ThreadGroup tGroup = new ThreadGroup("JsParserGroup");
			Thread thread = new Thread(tGroup, 
				new Runnable() {
	
					@Override
					public void run() {
						AstRoot ast;
						Parser jsParser = new Parser();
						Log.i(TAG, "Parsing file: " + scriptUri);
						try {
							ast = jsParser.parse(inputStreamReader, scriptUri, 0);
							Log.i(TAG, "Instrumenting file: " + scriptUri);
							ast.visit(new DebugInstrumentator());
							
							Log.i(TAG, "Writing file: " + scriptUri);
							BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
							writer.write(ast.toSource());
							writer.close();
							
						} catch (IOException e) {
							parseExceptions.add(e);
						} finally {
							startSignal.countDown();
						}
					}
				}
			,"ParserThread", PARSER_THREAD_STACKSIZE);
			
			thread.start();
			Log.i(TAG, "Waiting for instrumentation: " + scriptUri);
			startSignal.await();
			
			Log.i(TAG, "Instrumentation finished for file: " + scriptUri);
			if (!parseExceptions.isEmpty()) {
				throw parseExceptions.get(0);
			}
		} catch (InterruptedException e) {
			Log.w(TAG, "Waiting for instrumentation interrupted: " + scriptUri);
		} finally {
			try {
				inputStreamReader.close();
			} catch (IOException e) {
			}
		}
	}

}
