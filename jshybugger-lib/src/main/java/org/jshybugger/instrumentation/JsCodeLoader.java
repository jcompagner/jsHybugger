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
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

import android.util.Log;

/**
 * The JsCodeLoader is a helper class to make javascript files debug-able. 
 */
public class JsCodeLoader {

	/** The Constant INSTRUMENT_STACKSIZE defines the default thread stacksize for code instrumentation . */
	public static final String INSTRUMENT_STACKSIZE = "instrumentStacksize";
	
	/** The Constant PARSER_THREAD_STACKSIZE. */
	public static final int DEFAULT_INSTRUMENT_STACKSIZE = 32000;
	
	/** The Constant TAG. */
	private static final String TAG = "JsCodeLoader";

	/**
	 * Instrument javascript file.
	 * @param scriptUri the script uri
	 * @param inputFile the input file
	 * @param outputStream the output stream
	 * @param properties instrumentation properties
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public synchronized static void instrumentFile(final String scriptUri, final InputStream inputFile, final OutputStream outputStream, Map<String, Object> properties) throws Exception  {
	
		final InputStreamReader inputStreamReader = new InputStreamReader(inputFile);
		try {
			final CountDownLatch startSignal = new CountDownLatch(1);
			final List<Exception> parseExceptions = new ArrayList<Exception>();
			
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
							
						} catch (EvaluatorException e) {
							parseExceptions.add(e);
						} catch (IOException e) {
							parseExceptions.add(e);
						} finally {
							startSignal.countDown();
						}
					}
				}
			,"ParserThread", (Integer)getPropertyValue(properties, INSTRUMENT_STACKSIZE, DEFAULT_INSTRUMENT_STACKSIZE));
			
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

	/**
	 * Gets the property value.
	 *
	 * @param properties the properties map
	 * @param propName the prop name
	 * @param defaultValue the default value
	 * @return the property value
	 */
	private static Object getPropertyValue(Map<String, Object> properties, String propName, Object defaultValue) {
		
		if (properties != null && properties.get(propName) != null) {
			return  properties.get(propName);
		} else {
			return defaultValue;
		}
	}
}
