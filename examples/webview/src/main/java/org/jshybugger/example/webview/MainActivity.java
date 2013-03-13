package org.jshybugger.example.webview;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.jshybugger.DebugServiceClient;

import org.jshybugger.example.webview.R;

public class MainActivity extends Activity {
	private WebView webView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		

		webView = (WebView) findViewById(R.id.webView1);
		webView.setWebChromeClient(new WebChromeClient());
		
		
		WebSettings settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setAllowFileAccess(true);
		settings.setAppCacheEnabled(true);
		settings.setDomStorageEnabled(true);

		// attach web view to debugging service 
		DebugServiceClient.attachWebView(webView, this);

		// load html page via JsHybugger content provider 
		// example for remote page loading	
		// webView.loadUrl("content://jsHybugger.com/http://192.168.178.37:8080/desktop.html");

		// example for local page loading	
		webView.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
}
