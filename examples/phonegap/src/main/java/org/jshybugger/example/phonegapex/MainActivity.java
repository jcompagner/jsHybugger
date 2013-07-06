package org.jshybugger.example.phonegapex;

import org.apache.cordova.Config;
import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;
import org.jshybugger.DebugServiceClient;

import android.os.Bundle;

public class MainActivity extends DroidGap {

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// load html page via JsHybugger content provider 
        // Set by <content src="index.html" /> in config.xml
        super.loadUrl(Config.getStartUrl());
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
