jsHybugger - Javascript Debugger for Android Hybrid Apps
========================================================

Maybe you already know how easy it is to debug web pages on your Android device with Google Chrome and the Remote Debugging feature. Unfortunately cou can't use this feature (maybe in future?) to debug a web page which runs inside a native app (within a webview component)? And now, what can i do, to track down my javascript error? The normal approach is to insert a bunch of console.log() calls to your code and use logcat or a remote console tool to watch your debug statements and take some action. If you know this kind of developing, maybe jsHybugger would be an interessting tools, because it offer you a real debugger feeling for android web views.

jsHybugger implements the [Chrome Remote Debugging Protocol](https://developers.google.com/chrome-developer-tools/docs/debugger-protocol) as an android service. You can easily integrate the service component into your existing Android App to enable javascript debugging for webview components. You can even debug PhoneGap apps.

How does it work?

jsHybugger will intercept all resource requests from your webview, and will do an on-the-fly instrumentation of your 
javascript and html files. Javascript files, with the extension ".min.js" will not be instrumented and therefor not debug-able. If you use the non minified version of large libraries i.e. jquery, it's important to start the app on the FIRST run (with jsHybugger) NOT in debug mode. The instrumentation will take some time (jquery about 10sec.), the instrumented files are stored in a cache and are used at next startup. File changes are detected automatically and will trigger an re-instrumentation of the file. jsHybugger will automatically starts a WebSocket-Server on port 8888 on your device. You can use Eclipse together with the [Chrome-DevTools for Java](http://code.google.com/p/chromedevtools/) or you can use the "Chrome Browser" on your notebook/desktop to access the debugging service on your smartphone.

# jsHybugger features

* breakpoints
* watch expressions
* step into/over/out
* break on exception
* call stack navigation
* local variable inspection
* remote console
* javascript syntax and runtime error reporting 

# jsHybugger pictures and videos 

* jsHybugger + eclipse + chrome devtools [slides](http://jshybugger.org/slides/eclipse_phonegap/)
* jsHybugger + eclipse + chrome devtools [video](http://www.youtube.com/watch?v=h7zAj9M-OYo)  
* jsHybugger + Chrome Browser (TBD)

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

3. Connect WebView with Debugging-Service and prefix the url with content://jsHybugger.org/ 

		// attach web view to debugging service 
		DebugServiceClient.attachWebView(webView, this);
		webView.loadUrl("content://jsHybugger.org/file:///android_asset/www/index.html");
			 
5. Ready to launch your app on the phone! 

6. Start debugging frontend (Chrome Browser) - see section "Connect chrome browser to mobile device"

	
Maven Build
===========

1. Make sure that the ANDROID_HOME environment variable is set and refers to your SDK installation.

2. For the PhoneGap example you must first download and install the cordova library to your local maven repository.

	mvn install:install-file -DgroupId=org.apache.cordova -DartifactId=cordova -Dversion=2.2.0 -Dfile=<path to downloaded cordova-2.2.0.jar file> -Dpackaging=jar

3. Build the complete system with the following command: mvn clean install 
