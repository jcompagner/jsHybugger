jsHybugger - Javascript debugger for Android WebView
====================================================

Maybe you already know how easy it is to debug web pages on your Android device with Google Chrome and the Remote Debugging feature. But how to debug a web page which runs inside a native app within a webview component? Maybe you would say now, use jConsole or Weinre or .... YES these are all great tools, but what tool offer you real javascript debugging like the chrome webinspector? Now jsHybugger comes to play :-) 

jsHybugger is a debugging service which you can easily integrate in your existing Android App, and which will enable
javascript debugging for webview components inside your app. You can even debug PhoneGap apps.

How does it work?

jsHybugger will intercept all resource requests from your webview, and will do an on-the-fly instrumentation of your 
javascript and html files. Javascript files which filename ends with ".min.js" will not be instrumented and therefor not debug-able. If you use the non minified version of large libraries i.e. jquery, it's important to start the app on the first run (with jsHybugger) not in debug mode. The instrumentation will take some time (jquery about 10sec.), the instrumented files are stored in a cache and are used at next startup. File changes are detected automatically and will trigger an re-instrumentation of the file. jsHybugger will automatically starts an WebSocket-Server on port 8888 on your device. You must use a "Chrome Browser" on your notebook/desktop to access the debugging service on your smartphone. The URL you will find a few lines further down in the text.

Let's start with the WebView example. 

You can build the example using maven or you can just download the example [APK](http://jshybugger.org/download/jshybugger-webview-ex.apk) and start debugging (skip building section).

# building example APK
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


Now the details how to enable the debugging feature for your app.

1.  Add jshybugger-bundle/target/jshybugger-lib-1.0.0-SNAPSHOT_bundle.jar ([download](http://jshybugger.org/download/jshybugger-bundle-1.0.0-SNAPSHOT_bundle.jar)) file to your libs directory

2.	enhance AndroidManifest.xml

		<!-- JsHybugger needs network access -->
		<uses-permission android:name="android.permission.INTERNET" />

		<!--  JSHybugger webview content provider -->
		<provider android:name="org.jshybugger.DebugContentProvider"
				  android:exported="false"
				  android:authorities="jsHybugger.org" />
		  
		<!--  JSHybugger debug service -->
		<service android:name="org.jshybugger.DebugService"
				 android:exported="false"
				 android:enabled="true"/>

3. Connect WebView with Debugging-Service and prefix the url with content://jsHybugger.com/ 

		// attach web view to debugging service 
		DebugServiceClient.attachWebView(webView, this);
		webView.loadUrl("content://jsHybugger.com/file:///android_asset/www/index.html");
			 
5. Ready to launch your app on the phone! 

6. Start debugging frontend (Chrome Browser)

7. Now you have two options to connect your chrome browser with your mobile device

	a. use ADB tool and forward TCP port: 
	
		adb forward tcp:8888 tcp:8888
		chrome browser: http://localhost:8888

	b. directly connect to your smartphones IP address
		chrome browser: http://<phone ip>:8888
		
8. If everything works, you should now see the chrome webinspector and your loaded javascripts.
		
	
	
Maven Build
===========

1. Make sure that ANDROID_HOME environment variable is set and points to your SDK installation.

2. For the PhoneGap example you must first download and install the cordova library to your local maven repository.

mvn install:install-file -DgroupId=org.apache.cordova -DartifactId=cordova -Dversion=2.2.0 -Dfile=<path to downloaded cordova-2.2.0.jar file> -Dpackaging=jar

3. Build the complete system with the following command: mvn clean install 