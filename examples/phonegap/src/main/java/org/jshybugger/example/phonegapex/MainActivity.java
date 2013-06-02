package org.jshybugger.example.phonegapex;

import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;

import android.os.Bundle;
import org.jshybugger.DebugServiceClient;

public class MainActivity extends DroidGap {

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// load html page via JsHybugger content provider 
		// example for local page loading	
		super.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");
		//super.loadUrl("content://jsHybugger.org/http://jshybugger.org/angular-phonecat/app/index.html");
	}

	@Override
	public void init(CordovaWebView webView,
			CordovaWebViewClient webViewClient,
			CordovaChromeClient webChromeClient) {
		super.init(webView, webViewClient, webChromeClient);

		// attach web view to debugging service 
		DebugServiceClient.attachWebView(webView, this);
	}
}
