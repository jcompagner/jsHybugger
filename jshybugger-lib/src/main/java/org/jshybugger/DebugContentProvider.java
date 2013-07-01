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
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
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

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
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


	public static final String X_JS_HYBUGGER_GET = "X-JsHybugger-GET";

	public static final String INSTRUMENTED_FILE_APPENDIX = ".instr";

	public static final String ORIGNAL_SELECTION = "original";

	private static final String FILE_HASH_PREFIX = ".hash";
	
	/** The Constant TAG. */
	private static final String TAG = "DebugContentProvider";
	
	/** The Constant ANDROID_ASSET_URL. */
	private static final String ANDROID_ASSET_URL = "file:///android_asset/";
	
	/** The Constant ANDROID_PRIVATE_URL. */
	private static final String ANDROID_PRIVATE_URL = "file:///data/data/";

	private static final String DEFAULT_PROVIDER_PROTOCOL = "content://jsHybugger.org/";

	/** The debug service started. */
	private CountDownLatch debugServiceStarted = new CountDownLatch(2);
	
	/** The debug service msg handler. */
	private DebugServiceMsgHandler debugServiceMsgHandler = new DebugServiceMsgHandler(debugServiceStarted);
	
	private File CACHE_DIR = null;
	private File CHANGED_CACHE_DIR=null;
	

	/**
	 * Gets the provider protocol.
	 *
	 * @param context the context
	 * @return the provider protocol
	 */
	public static String getProviderProtocol(Context context) {
		ProviderInfo info;
		try {
			info = context.getPackageManager().getProviderInfo(new ComponentName(context, DebugContentProvider.class), PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
			return info != null ? "content://" + info.authority + "/" : DEFAULT_PROVIDER_PROTOCOL;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "getProviderProtocol failed", e);
			return DEFAULT_PROVIDER_PROTOCOL;
		}
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#openFile(android.net.Uri, java.lang.String)
	 */
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) {

		// wait here till sync debug service is started
		prepareStartupAndWait();
		
        String url = uri.getPath().substring(1);
		InputResource resource = null;
		
		try {
			// get original source
			resource = openInputFile(url);

			File cacheFile = null;
			if (resource.isJs()) {
				cacheFile = searchCacheFile(url);
				String resourceHash = calcResourceHash(resource);
				
				if (cacheFile.exists() && isCacheFileValid(resourceHash, cacheFile)) {
					
					File instrumentedFile = getInstrumentedCacheFile(cacheFile);
					if (instrumentedFile.exists()) {
						return ParcelFileDescriptor.open(instrumentedFile, ParcelFileDescriptor.MODE_READ_ONLY);
					} else {
						return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
					}
				} else {
					writeCacheFile(resource, resourceHash, cacheFile);
				}
			}
			
			
			if (url.endsWith("jshybugger.js")) {
				ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
				
				new TransferThread(resource.getInputStream(), new AutoCloseOutputStream(
						pipe[1]), false).start();
				
				return pipe[0];
				
			} else if (resource.isJs() && !url.endsWith(".min.js")) {

				// instrument js code
				File outFile = getInstrumentedCacheFile(cacheFile);
				try {
					JsCodeLoader.instrumentFile(resource.getInputStream(), url, new FileOutputStream(outFile));
					
					// return instrumented js code
					return ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY);

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
					resource.getInputStream().close();
				}
				
			} else {
		        Log.d(TAG, "loading file: " + uri);
				return createParcel(resource);
			}
			
		} catch (IOException e) {
	        Log.e(TAG, "file open failed: " + e);
	        
		} 

		return null;
    }

	private void writeCacheFile(InputResource resource, String resourceHash,
			File cacheFile) throws IOException {
		
		// first write hash file
		FileWriter fw = new FileWriter(new File(cacheFile.getAbsolutePath() + FILE_HASH_PREFIX));
		fw.write(resourceHash);
		fw.close();
		
		// now write original content
		BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(new File(cacheFile.getAbsolutePath())));
		byte buffer[] = new byte[8096];
		int len;
		
		while ((len=resource.inputStream.read(buffer))>0) {
			fout.write(buffer, 0, len);
		}
		fout.close();
		resource.inputStream.reset();
	}

	private File getInstrumentedCacheFile(File resource) {
		return resource.getAbsolutePath().endsWith(INSTRUMENTED_FILE_APPENDIX)
				? resource 
				: new File(resource.getAbsoluteFile() + INSTRUMENTED_FILE_APPENDIX);
	}
	
	private String calcResourceHash(InputResource resource) throws IOException {
		try {
			resource.inputStream.mark(2000000);
			return Md5Checksum.getMD5Checksum(resource.inputStream);
		} finally {
			resource.inputStream.reset();
		}
	}
	
	private boolean isCacheFileValid(String resourceHash, File cacheFile) throws IOException {
		
		String cacheHash = null;

		File hashFile = new File(cacheFile.getAbsoluteFile() + FILE_HASH_PREFIX);
		if (hashFile.exists()) {
			BufferedReader fr = new BufferedReader(new FileReader(hashFile));
			cacheHash = fr.readLine();
			fr.close();
		}
		
		return resourceHash.equals(cacheHash);
	}

	private File searchCacheFile(String url) {
		String loadUrl = getCacheItemName(url);
		File loadUrlFd = new File(CHANGED_CACHE_DIR, loadUrl);
		if (loadUrlFd.exists()) {
			return loadUrlFd;
		}
		loadUrlFd = new File(CACHE_DIR, loadUrl);
		return loadUrlFd;
	}

	private void prepareStartupAndWait() {
		try {
			synchronized (debugServiceStarted) {
				 
				if (debugServiceStarted.getCount() > 0 ) {
					CACHE_DIR = new File(getContext().getFilesDir(), ".jsHybugger");
					if (!CACHE_DIR.exists() && !CACHE_DIR.mkdir()) {
						Log.e(TAG, "Creating jsHybugger cache failed. "  + CACHE_DIR.getAbsolutePath());
					}
					CHANGED_CACHE_DIR = new File(CACHE_DIR, ".changed");
					if (!CHANGED_CACHE_DIR.exists() && !CHANGED_CACHE_DIR.mkdir()) {
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
		
		InputStream inputStream = resource.getInputStream();

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
        	urlConnection.addRequestProperty(X_JS_HYBUGGER_GET, ORIGNAL_SELECTION);
        	
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
	 * @return the instrument file name
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private String getCacheItemName(String url) {
		String loadUrl = null;
		loadUrl = url.replaceAll("[/:?=]", "_");
		return loadUrl;
	}	
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		
		Intent service = new Intent(getContext(), DebugService.class);
		try {
			ProviderInfo info = getContext().getPackageManager().getProviderInfo(new ComponentName(getContext(), DebugContentProvider.class), PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
			Bundle metaData = info.metaData;
			if (metaData != null && metaData.getString("debugServiceClass") != null) {
				service = new Intent(getContext(), Class.forName(metaData.getString("debugServiceClass")));
			} 
			
			Log.d(TAG, "Content provider started: " + info.authority);
		} catch (NameNotFoundException e) {
			Log.e(TAG, "ContentProvider component not found", e);
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "ContentProvider component debug service not found", e );
		}
		
				 
		// initiate debug service start, and pass message handler to receive debug service started message.
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
		
		return saveContent(uri, content.getAsString("scriptSource"), CACHE_DIR);
	}
	
	private Uri saveContent(Uri uri, String scriptSource, File cache) {
		
        String url = uri.getPath().substring(1);
        
		try {
			// get original source
			InputResource resource = 
					new InputResource(true, false, new BufferedInputStream(new ByteArrayInputStream(scriptSource.getBytes())));			

			String resourceHash = calcResourceHash(resource);
			File cacheFile = new File(cache, getCacheItemName(url));

			if (!cacheFile.exists() || !isCacheFileValid(resourceHash, cacheFile)) {

				writeCacheFile(resource, resourceHash, cacheFile);
			
				// instrument js code
				File outFile =  getInstrumentedCacheFile(cacheFile);
				try {
					JsCodeLoader.instrumentFile(resource.getInputStream(), url, new FileOutputStream(outFile));
					return uri;
					
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
					resource.getInputStream().close();
				}
			} else {
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
			File cacheFile = searchCacheFile(url);
			
			if (cacheFile.exists()) {
				
				File instrumentedFile = getInstrumentedCacheFile(cacheFile);
				if (!ORIGNAL_SELECTION.equals(selection) && instrumentedFile.exists()) {
					inputStream = new BufferedInputStream(new FileInputStream(instrumentedFile));
				} else {
					inputStream = new BufferedInputStream(new FileInputStream(cacheFile));
				}
			} else {
				InputResource inputResource = openInputFile(url);
				inputStream = new BufferedInputStream(inputResource.inputStream);
			}
			
			if (inputStream != null) {
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
					
					String resourceStr = byteOut.toString();
					cursor.addRow(new Object[] { resourceStr });
					
				} finally {
					//resourceContent = byteOut.toString();
					
					inputStream.close();
					outStream.close();
				}
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
	public int update(Uri uri, ContentValues content, String arg2, String[] arg3) {
		// wait here till sync debug service is started 
		prepareStartupAndWait();
		
		Uri rUri = saveContent(uri, content.getAsString("scriptSource"), CHANGED_CACHE_DIR);
		return rUri != null ? 1 : 0;
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
		private final BufferedInputStream inputStream;
		
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
			this.inputStream = inputSream;
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
		public BufferedInputStream getInputStream() {
			return inputStream;
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
