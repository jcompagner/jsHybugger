package org.jshybugger.example.webview;

import org.jshybugger.DebugServiceClient;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
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
		
		CookieSyncManager.createInstance(this);
		CookieManager.getInstance().setAcceptCookie(true);
		CookieManager.setAcceptFileSchemeCookies(true);
		CookieManager.getInstance().setCookie("MyCookie2", "MyVal2");
		CookieSyncManager.getInstance().startSync();
		
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
		DebugServiceClient.attachWebView(webView, this);

		// load html page via JsHybugger content provider 
		// example for remote page loading	
		//webView.loadUrl("content://jsHybugger.org/http://www.jsHybugger.org/angular-phonecat/app/index.html");

		// example for local page loading	
		webView.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
}
