jsHybugger - Javascript Debugger for Android 
============================================

Maybe you already know how easy it is to debug web pages on your Android device with Google Chrome and the Remote Debugging feature. Unfortunately you can't use this feature (maybe in future?) to debug a web page which runs inside a native app (within a webview component) or the default android browser! And now, what can you do, to track down your javascript errors? The normal approach is to insert a bunch of console.log() calls to your code and use logcat or a remote console tool to watch your debug statements and take some action. If you know this kind of developing, maybe jsHybugger would be an interessting tool for you!

jsHybugger implements the [Chrome Remote Debugging Protocol](https://developers.google.com/chrome-developer-tools/docs/debugger-protocol) as an android service. You can easily integrate the service component into your existing Android App to enable javascript debugging for webview components (you can also debug PhoneGap apps) or you can use the [jsHybugger Debugging App](https://play.google.com/store/apps/details?id=org.jshybugger.proxy) (available over google play store or as [APK](http://jshybugger.org/download/jshybugger-proxy-1.2.1.apk)) to debug web pages which runs in the default android browser.

# jsHybugger 2.0 (coming soon)

* DOM/CSS Inspection


# jsHybugger 1.2 Features 

* View/Edit/Delete Local Storage 
* View/Edit/Delete Session Storage
* View/Edit/Delete WebSQL Database
* View page resources (images, scripts, html)
* line / conditional breakpoints
* watch expressions
* step into/over/out
* continue to here
* break on exception
* call stack navigation
* local variable inspection
* remote console logging
* javascript syntax and runtime error reporting 
* save javascript changes
* on-the-fly javascript instrumentation
 
[Download JAR library 1.2.2](http://jshybugger.org/download/jshybugger-bundle-1.2.2.jar)


# jsHybugger Debugger App

Just install the app and start debugging - no code changes needed to use jsHybugger with your web pages!
Watch the [[video]](http://youtu.be/BOvwcp79ocE) or [[slides]](http://jshybugger.org/slides/default_browser/index.html#s2) and see how easy it is to use jsHybugger.

How does it work?

jsHybugger will intercept all resource requests for your web page, and will do an on-the-fly instrumentation of your 
javascript and html files. Javascript files, with the extension ".min.js" will not be instrumented and therefor not debug-able. If you use the non minified version of large libraries i.e. jquery, it's important to start the app on the FIRST run (with jsHybugger) NOT in debug mode. The instrumentation will take some time (jquery about 10sec.), the instrumented files are stored in a cache and are used at next startup. File changes are detected automatically and will trigger an re-instrumentation of the file. jsHybugger will automatically starts a WebSocket-Server on port 8888 on your device. You can use Eclipse together with the [Chrome-DevTools for Java](http://code.google.com/p/chromedevtools/) or you can use the "Chrome Browser" on your notebook/desktop to access the debugging service on your smartphone.

[Install Debugger App](https://play.google.com/store/apps/details?id=org.jshybugger.proxy) or 
[Download Debugger App](http://jshybugger.org/download/jshybugger-proxy-1.2.1.apk) 

# jsHybugger pictures and videos 

* jsHybugger + default Android-Browser [[slides]](http://jshybugger.org/slides/default_browser/index.html#s2) [[video]](http://youtu.be/BOvwcp79ocE)  
* jsHybugger + eclipse + chrome devtools [[slides]](http://jshybugger.org/slides/eclipse_phonegap/index.html#s2) [[video]](http://youtu.be/P5NSlN8eVyk)  
* jsHybugger + Chrome Browser [[slides]](http://jshybugger.org/slides/chrome_webview/index.html#s2)  [[video]](http://youtu.be/hst6pJH9lRA)

# Example Android App
You can build the example using maven or you can just download the example [APK](http://jshybugger.org/download/jshybugger-webview-ex.apk) and start debugging (skip next lines and continue reading with "Connect chrome browser to mobile device").

Go to jsHybugger/examples/webview and enter the following commands (see Maven-Build section for more info)

	mvn clean install

	# install APK file on device and start it
	mvn android:deploy android:run

# Connect chrome browser to mobile device

1. use ADB tool and forward TCP port: 
	
		adb forward tcp:8888 tcp:8888
		chrome browser: http://localhost:8888

2. directly connect to your smartphones IP address

		chrome browser: http://<phone ip>:8888
		
You should now see the chrome webinspector and your loaded javascripts. Let's set a breakpoint on line 32 and click the calculate button in the example. If everythings works, your debugger should stop automatically on the breakpoint.


# Integrate jsHybugger into your app

1.  Add jshybugger-bundle/target/jshybugger-bundle-1.2.2.jar ([download](http://jshybugger.org/download/jshybugger-bundle-1.2.2.jar)) file to your libs directory

2.	enhance AndroidManifest.xml

		<!-- JsHybugger needs network access -->
		<uses-permission android:name="android.permission.INTERNET" />

		<!--  jsHybugger webview content provider -->
		<provider android:name="org.jshybugger.DebugContentProvider"
				  android:exported="false"
				  android:authorities="jsHybugger.org" />
		  
		<!--  jsHybugger debug service -->
		<service android:name="org.jshybugger.DebugService"
				 android:exported="false"
				 android:enabled="true"/>

3. Connect WebView with Debugging-Service and prefix the url with content://jsHybugger.org/ 

		// attach web view to debugging service 
		DebugServiceClient.attachWebView(webView, this);
		webView.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");
			 
5. Ready to launch your app on the phone! 

6. Start debugging frontend (Chrome Browser) - see section "Connect chrome browser to mobile device"

	
Maven Build
===========

1. Make sure that the ANDROID_HOME environment variable is set and refers to your SDK installation.

2. Build the complete system with the following command: mvn clean install 
