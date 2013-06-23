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
package org.jshybugger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

import org.jshybugger.instrumentation.JsCodeLoader;
import org.jshybugger.server.Md5Checksum;
import org.mozilla.javascript.EvaluatorException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

/**
 * The DebugContentProvider is responsible for delivering file resources to an client (WebView).
 * Sp  
 */
public class DebugContentProvider extends ContentProvider {

	public static final String PROVIDER_PROTOCOL = "content://jsHybugger.org/";

	private static final String INSTRUMENTED_FILE_APPENDIX = ".instr";

	/** The Constant TAG. */
	private static final String TAG = "DebugContentProvider";
	
	/** The Constant ANDROID_ASSET_URL. */
	private static final String ANDROID_ASSET_URL = "file:///android_asset/";
	
	/** The Constant ANDROID_PRIVATE_URL. */
	private static final String ANDROID_PRIVATE_URL = "file:///data/data/";

	/** The debug service started. */
	private CountDownLatch debugServiceStarted = new CountDownLatch(2);
	
	/** The debug service msg handler. */
	private DebugServiceMsgHandler debugServiceMsgHandler = new DebugServiceMsgHandler(debugServiceStarted);
	
	private static File CACHE_DIR = null;
	private static File CHANGED_CACHE_DIR=null;
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#openFile(android.net.Uri, java.lang.String)
	 */
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) {

		// wait here till sync debug service is started
		prepareStartupAndWait();
		
        String url = uri.getPath().substring(1);
		String loadUrl = null;
        
		try {
			// get original source
			InputResource resource = openInputFile(url);

			if (resource.isJs()) {
				// check if the js file is already instrumented and can be immediately returned
				loadUrl = getCacheFileName(url, resource);
				if (loadUrl != null) {
					// first look in the changed cache
					File loadUrlFd = new File(CHANGED_CACHE_DIR, loadUrl+ INSTRUMENTED_FILE_APPENDIX);
					if (loadUrlFd.exists()) {
						resource.getInputSream().close();
						return ParcelFileDescriptor.open(loadUrlFd, ParcelFileDescriptor.MODE_READ_ONLY);
					}

					// then look in the normal cache
					loadUrlFd = new File(CACHE_DIR, loadUrl+ INSTRUMENTED_FILE_APPENDIX);
					if (loadUrlFd.exists()) {
						resource.getInputSream().close();
						return ParcelFileDescriptor.open(loadUrlFd, ParcelFileDescriptor.MODE_READ_ONLY);
					}
				}
				
				// ensure that stream is still open 
				try {
					resource.getInputSream().reset();
				} catch (IOException ioe) {
					resource = openInputFile(url);
				}
			}
			
			
			if (url.endsWith("jshybugger.js")) {
				ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
				
				new TransferThread(resource.getInputSream(), new AutoCloseOutputStream(
						pipe[1]), false).start();
				
				return pipe[0];
				
			} else if (resource.isJs() && !url.endsWith(".min.js")) {
				// instrument js code
				File outFile = new File(CACHE_DIR, loadUrl + INSTRUMENTED_FILE_APPENDIX);
				try {
					JsCodeLoader.instrumentFile(resource.getInputSream(), url, new FileOutputStream(outFile));
				
				} catch (EvaluatorException e) {
			        Log.d(TAG, "parsing failure while instrumenting file: " + e.getMessage());

			        // delete file - maybe partially instrumented file.
					outFile.delete();
					
					String writeConsole = "console.error('" + e.getMessage().replace("'", "\"") + "')";
					return createParcel(new InputResource(false, false, new BufferedInputStream( new ByteArrayInputStream(writeConsole.getBytes()))));
					
				} catch (Exception e) {
			        Log.d(TAG, "instrumentation failed, delivering original file: " + uri, e);

			        // delete file - maybe partially instrumented file.
					outFile.delete();

					return createParcel(openInputFile(url));
					
				} finally {
					resource.getInputSream().close();
				}
				
				// return instrumented js code
				File loadUrlFd = new File(CACHE_DIR, loadUrl+ INSTRUMENTED_FILE_APPENDIX);
				return ParcelFileDescriptor.open(loadUrlFd, ParcelFileDescriptor.MODE_READ_ONLY);
			} else {
		        Log.d(TAG, "loading file: " + uri);
				return createParcel(resource);
			}
			
		} catch (IOException e) {
	        Log.e(TAG, "file open failed: " + e);
		}

		return null;
    }

	private void prepareStartupAndWait() {
		try {
			synchronized (debugServiceStarted) {
				 
				if (debugServiceStarted.getCount() > 1) {
					CACHE_DIR = new File(getContext().getFilesDir(), ".jsHybugger");
					if (!CACHE_DIR.mkdir()) {
						Log.e(TAG, "Creating jsHybugger cache failed. "  + CACHE_DIR.getAbsolutePath());
					}
					CHANGED_CACHE_DIR = new File(CACHE_DIR, ".changed");
					if (!CHANGED_CACHE_DIR.mkdir()) {
						Log.e(TAG, "Creating jsHybugger changed cache failed. "  + CHANGED_CACHE_DIR.getAbsolutePath());
					}
					
					// clear all files in changed cache
	        		for (File file : CHANGED_CACHE_DIR.listFiles()) {
	        			file.delete();
	        		}
	        		
					debugServiceStarted.countDown();
				}
			}

			debugServiceStarted.await();
		} catch (InterruptedException e1) {
			Log.e(TAG, "Waiting for debug service interrupted");
		}
	}

	private ParcelFileDescriptor createParcel(InputResource resource)
			throws IOException {
		ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
		
		InputStream inputStream = resource.getInputSream();

		new TransferThread(inputStream, new AutoCloseOutputStream(
				pipe[1]), resource.isHtml()).start();
		
		return pipe[0];
	}

	/**
	 * Open local or network file resource
	 *
	 * @param url the resource to open
	 * @return the input resource
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private InputResource openInputFile(String url) throws IOException {

		if (url.endsWith("jshybugger.js")) {
			
        	return new InputResource(
        			false,  // don't mark it as js resource, because we don't want instrumentation for this file 
        			false, 
        			new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("jshybugger.js")));
			
		} else if (url.startsWith(ANDROID_ASSET_URL)) {   // Must be a local file
        	url = url.substring(ANDROID_ASSET_URL.length());

        	return new InputResource(
        			url.endsWith(".js"),
        			url.endsWith(".html"), 
        			new BufferedInputStream(getContext().getAssets().open(url,AssetManager.ACCESS_STREAMING)));
        	
		} else if (url.contains(ANDROID_PRIVATE_URL)) {   // Must be a private app file

        	url = url.substring(url.indexOf("/data/data/"));  // strip file:// 
        	
        	return new InputResource(
        			url.endsWith(".js"),
        			url.endsWith(".html"), 
        			new BufferedInputStream(new FileInputStream(url)));
        	
        } else if (url.indexOf(":") < 0) {  // Must be a local file
        	
        	return new InputResource(
        			url.endsWith(".js"),
        			url.endsWith(".html"), 
        			new BufferedInputStream(getContext().getAssets().open(url,AssetManager.ACCESS_STREAMING)));
        	
        } else { // loading network resource
        	URL urlRes = new URL(url);
        	HttpURLConnection urlConnection = (HttpURLConnection) urlRes.openConnection();
        	urlConnection.connect();
        	
        	String contentType = urlConnection.getContentType();
			Log.d(TAG, url +  ", type: " + contentType);
			return new InputResource(
					url.endsWith(".js") ||
					contentType.contains("application/x-javascript") || 
					contentType.contains("text/javascript") ||
					contentType.contains("text/x-js"),
					contentType.contains("text/html"), 
					new BufferedInputStream(urlConnection.getInputStream()));
        }
	}
	
	/**
	 * Gets the cache file name.
	 *
	 * @param url the url to instrument
	 * @param resource resource input stream
	 * @return the instrument file name
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private String getCacheFileName(String url, InputResource resource) throws IOException {
		String loadUrl = null;
		try {
			loadUrl = url.replaceAll("[/:?=]", "_");
			if (resource != null) {
				resource.getInputSream().mark(1000000);
				loadUrl += Md5Checksum.getMD5Checksum(resource.getInputSream());
			}
			
		} catch (Exception e) {
			Log.e(TAG, "getCacheFileName failed:" + url, e);
		} 
		return loadUrl;
	}	
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		
		// initiate debug service start, and pass message handler to receive debug service started message.
		Intent service = new Intent(getContext(), DebugService.class);
		service.putExtra("callback", new Messenger(debugServiceMsgHandler));
		getContext().startService(service);
		
		return true;
	}
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri arg0) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues content) {
		
		// wait here till sync debug service is started 
		prepareStartupAndWait();
		
        String url = uri.getPath().substring(1);
		String loadUrl = null;
        
		try {
			// get original source
			InputResource resource = openInputFile(url);

			if (resource.isJs()) {
				// check if the js file is already instrumented and can be immediately returned
				loadUrl = getCacheFileName(url, resource);
				
				if (loadUrl != null) {
					File loadUrlFd = new File(CHANGED_CACHE_DIR, getCacheFileName(url, null));
					FileWriter fw = new FileWriter(loadUrlFd);
					fw.write(content.getAsString("scriptSource"));
					fw.close();
					
					resource = new InputResource(true, false, 
		        			new BufferedInputStream(new FileInputStream(loadUrlFd)));					
				}
			
				// instrument js code
				File outFile = new File(CHANGED_CACHE_DIR, loadUrl + INSTRUMENTED_FILE_APPENDIX);
				try {
					JsCodeLoader.instrumentFile(resource.getInputSream(), url, new FileOutputStream(outFile));

				} catch (EvaluatorException e) {
			        Log.d(TAG, "parsing failure while instrumenting file: " + e.getMessage());

			        // delete file - maybe partially instrumented file.
			        outFile.delete();
			        
					String writeConsole = e.getMessage();
					throw new RuntimeException(writeConsole);
				} catch (Exception e) {
			        Log.d(TAG, "instrumentation failed: " + uri, e);

			        // delete file - maybe partially instrumented file.
			        outFile.delete();
			        
					throw new RuntimeException("instrumentation failed: " + uri, e);
					
				} finally {
					resource.getInputSream().close();
				}
				
				return uri;

			}			
		} catch (IOException e) {
	        Log.e(TAG, "file insert failed: " + e);
		}

		return null;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] columns, String selection, String[] selectionArgs,
			String sortOrder) {
		
		MatrixCursor cursor = new MatrixCursor(columns,  1); 
		
		// get original source
        String url = uri.getPath().substring(1);

		try {
	        BufferedInputStream inputStream = null;
	        
			// check if the js file is already instrumented and can be immediately returned
			String localCacheResource;
				localCacheResource = getCacheFileName(url, null);
	
				if (localCacheResource != null) {
				// first look in the changed cache
				File loadUrlFd = new File(CHANGED_CACHE_DIR, localCacheResource);
				if (loadUrlFd.exists()) {
					inputStream = new BufferedInputStream(new FileInputStream(loadUrlFd));
				} else {
	
					// then look in the normal cache
					loadUrlFd = new File(CACHE_DIR, localCacheResource);
					if (loadUrlFd.exists()) {
						inputStream = new BufferedInputStream(new FileInputStream(loadUrlFd));
					} else {
						inputStream = new BufferedInputStream(openInputFile(url).inputSream);
					}
				}
			}
			
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream(inputStream.available());
			OutputStream outStream = null;
			
			if ("scriptSourceEncoded".equals(columns[0])) {
				outStream = new Base64OutputStream(byteOut, Base64.DEFAULT);
			} else {
				outStream = byteOut;
			}
			
			//String resourceContent=null;
			try {
				byte bytesRead[] = new byte[4096];
				int numBytes=0;
				
				//Read File Line By Line
				while ((numBytes = inputStream.read(bytesRead)) > 0)   {
					outStream.write(bytesRead,0,numBytes);
				}
				
				cursor.addRow(new Object[] { byteOut.toString() });
			} finally {
				//resourceContent = byteOut.toString();
				
				inputStream.close();
				outStream.close();
			}
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return cursor;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	/**
	 * The Class InputResource.
	 */
	static class InputResource {
	
		/** The js. */
		private final boolean js;
		
		/** The html. */
		private final boolean html;
		
		/** The input sream. */
		private final BufferedInputStream inputSream;
		
		/**
		 * Instantiates a new input resource.
		 *
		 * @param js the js
		 * @param html the html
		 * @param inputSream the input sream
		 */
		public InputResource(boolean js, boolean html,
				BufferedInputStream inputSream) {
			super();
			this.js = js;
			this.html = html;
			this.inputSream = inputSream;
		}

		/**
		 * Checks if is js.
		 *
		 * @return true, if is js
		 */
		public boolean isJs() {
			return js;
		}

		/**
		 * Checks if is html.
		 *
		 * @return true, if is html
		 */
		public boolean isHtml() {
			return html;
		}

		/**
		 * Gets the input sream.
		 *
		 * @return the input sream
		 */
		public BufferedInputStream getInputSream() {
			return inputSream;
		}
	}
	
	/**
	 * The Class DebugServiceMsgHandler.
	 */
	static class DebugServiceMsgHandler extends Handler {
		
        /** The debug service started. */
        private CountDownLatch debugServiceStarted;

		/**
		 * Instantiates a new debug service msg handler.
		 *
		 * @param debugServiceStarted the debug service started
		 */
		public DebugServiceMsgHandler(CountDownLatch debugServiceStarted) {
        	this.debugServiceStarted = debugServiceStarted;
		}

		/* (non-Javadoc)
		 * @see android.os.Handler#handleMessage(android.os.Message)
		 */
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case DebugService.MSG_WEBVIEW_ATTACHED:
            	debugServiceStarted.countDown();
            	break;
            }
        }
	}
	
	/**
	 * The Class TransferThread is responsible for asynchronous content delivery of this provider.
	 * It will also inject the jsHybugger script to HTML pages with an <head> section. 
	 */
	static class TransferThread extends Thread {
		
		/** The in. */
		private InputStream in;
		
		/** The out. */
		private OutputStream out;
		
		/** The is html. */
		private boolean isHTML;

		/**
		 * Instantiates a new transfer thread.
		 *
		 * @param in the in
		 * @param out the out
		 * @param isHTML the is html
		 */
		TransferThread(InputStream in, OutputStream out, boolean isHTML) {
			this.in = in;
			this.out = out;
			this.isHTML = isHTML;
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			byte[] buf = new byte[1024];
			int len;
			boolean patchDone = !this.isHTML;
			
			try {
				while ((len = in.read(buf)) > 0) {
					if (!patchDone) {
						out.write("<script type=\"text/javascript\" src=\"/jshybugger.js\"></script>".getBytes());
						out.write(buf, 0, len);
						patchDone=true;
					} else {
						out.write(buf, 0, len);
					}
				}
				
				in.close();
				out.flush();
				out.close();
			} catch (IOException e) {
				Log.e(getClass().getSimpleName(),
						"Exception transferring file", e);
			}
		}
	}
}
