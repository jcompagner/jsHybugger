package org.jshybugger.proxy;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class StartActivity extends Activity {

	private Intent debugServiceIntent;
	private Intent proxyServiceIntent;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
	    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		final EditText host = (EditText) findViewById(R.id.tcp_host);
		final EditText port = (EditText) findViewById(R.id.tcp_port);
		final EditText resourceURI = (EditText)findViewById(R.id.resource_uri);
		final Button stopButton = (Button)findViewById(R.id.btn_stop_service);
		final Button startButton = (Button)findViewById(R.id.btn_start_service);
		final Button openBrowser = (Button)findViewById(R.id.btn_open_browser);
		final Button clearCache = (Button)findViewById(R.id.btn_clear_cache);

		host.setText(preferences.getString("host", "www.jshybugger.org"));
		port.setText(String.valueOf(preferences.getInt("port", 80)));
		resourceURI.setText(preferences.getString("uri", "/angular-phonecat/app/index.html"));
		
		startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        		int portNumber = Integer.parseInt(port.getText().toString());
        		String hostname = host.getText().toString();

        		if ((portNumber <= 0) || (portNumber > 65535)) {
        			showAlert("Please enter a valid port number");
        			return;
        		}
        		if ((hostname == null) || (hostname.length() == 0)) {
        			showAlert("Please enter a valid hostname or ip address");
        			return;
        		}
        		
        		Editor edit = preferences.edit();        		
        		edit.putString("host", hostname);
        		edit.putInt("port", portNumber);
        		edit.putString("uri", resourceURI.getText().toString());
        		edit.commit();
        		
        		proxyServiceIntent = new Intent(StartActivity.this, ProxyService.class);
				proxyServiceIntent.putExtra("port", portNumber);
				proxyServiceIntent.putExtra("host", hostname);
        		startService(proxyServiceIntent);
        		
        		debugServiceIntent = new Intent(StartActivity.this, DebugService.class);
        		startService(debugServiceIntent);
        		
        		
        		stopButton.setEnabled(true);
        		startButton.setEnabled(false);
        		host.setEnabled(false);
        		port.setEnabled(false);
        		openBrowser.setEnabled(true);
            }
        });

		stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        		stopService(proxyServiceIntent);
        		stopService(debugServiceIntent);
        		
        		startButton.setEnabled(true);
        		stopButton.setEnabled(false);
        		host.setEnabled(true);
        		port.setEnabled(true);
        		openBrowser.setEnabled(false);
            }
        });
		stopButton.setEnabled(false);
		openBrowser.setEnabled(false);

		openBrowser.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

        		startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, 
			       Uri.parse("http://localhost:8080" + resourceURI.getText().toString())), "Choose browser"));
            	
            }
        });
		
		clearCache.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

        		File filesDir = getApplicationContext().getFilesDir();
        		for (File file : filesDir.listFiles()) {
        			file.delete();
        		}
            }
        });

		if (ProxyService.isRunning()) {
    		debugServiceIntent = new Intent(StartActivity.this, DebugService.class);
    		proxyServiceIntent = new Intent(StartActivity.this, ProxyService.class);

    		stopButton.setEnabled(true);
    		startButton.setEnabled(false);
    		host.setEnabled(false);
    		port.setEnabled(false);
    		openBrowser.setEnabled(true);
    	}
	}
	
	private void showAlert(String message) {
		
	    AlertDialog ad = new AlertDialog.Builder(this).create();  
	    ad.setCancelable(false); // This blocks the 'BACK' button  
	    ad.setMessage(message);  
	    ad.setButton("OK", new DialogInterface.OnClickListener() {  
	        @Override  
	        public void onClick(DialogInterface dialog, int which) {  
	            dialog.dismiss();                      
	        }  
	    });  
	    ad.show();  		
	}
}
