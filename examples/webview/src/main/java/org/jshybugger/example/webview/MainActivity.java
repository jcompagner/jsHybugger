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

		/* example for loadDataWithBaseURL
		String resUri = dbgClient.processJSCode("function hello() {\nvar x=0;\nalert('hello world');\n}", null);

		final String htmlPre = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"> <script src='" + dbgClient.getJsHybuggerURL() + "'></script> <script src='" + resUri + "'></script></head><body style='margin:0; pading:0; background-color: black;'>";  
        final String htmlCode = "<button onclick='hello()'>hello world</button>";
        final String htmlPost = "</body></html>";
        
        webView.loadDataWithBaseURL("null", htmlPre+htmlCode+htmlPost, "text/html", "UTF-8", null);
        */
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
}
