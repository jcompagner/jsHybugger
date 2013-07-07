package org.jshybugger.example.webview;

import org.jshybugger.DebugContentProvider;
import org.jshybugger.DebugServiceClient;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;

public class MainActivity extends Activity {
	private WebView webView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		

		webView = (WebView) findViewById(R.id.webView1);
		webView.setWebChromeClient(new WebChromeClient() {
		    @Override
		    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
		        long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
		        quotaUpdater.updateQuota(estimatedSize * 2);
		    }
		});
		
		WebSettings settings = webView.getSettings();
		
		settings.setJavaScriptEnabled(true);
		settings.setAllowFileAccess(true);
		
		settings.setAppCacheEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setAllowContentAccess(true);

		settings.setDatabaseEnabled(true); 
		String databasePath = this.getApplicationContext().getDir("database", 
		                       Context.MODE_PRIVATE).getPath(); 
		settings.setDatabasePath(databasePath); 
		
		// attach web view to debugging service 
		DebugServiceClient dbgClient = DebugServiceClient.attachWebView(webView, this);

		// load remote html page via JsHybugger content provider 
		//webView.loadUrl("content://jsHybugger.org/http://www.jsHybugger.org/angular-phonecat/app/index.html");

		// example for local page loading	
		webView.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");

		// example for dynamic page loading	
		// loadDataWithBaseURL(dbgClient);
	}

	private void loadDataWithBaseURL(DebugServiceClient dbgClient) {
		// argument one: valid javascript fragment
		// argument two: resource name or null (will generate a name based on the file content - MD5 hash) 
		String helloWorldUri = dbgClient.processJSCode("function helloWorld() {\nalert('hello world');\n}", null);

		StringBuilder htmlContent = new StringBuilder();
		htmlContent.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");

		// Important: enable jsHybugger debugging for this page
		htmlContent.append("<script src='" + dbgClient.getJsHybuggerURL() + "'></script>");

		// add on-the-fly generated helloWorld.js resource to this page
		htmlContent.append("<script src='" + helloWorldUri + "'></script>");

		htmlContent.append("</head><body>");  
		htmlContent.append("<button onclick='hello()'>hello world</button>");
		htmlContent.append("</body></html>");

		webView.loadDataWithBaseURL("null", htmlContent.toString(), "text/html", "UTF-8", null);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
}
