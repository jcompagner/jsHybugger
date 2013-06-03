package org.jshybugger.proxy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.jshybugger.proxy.R.layout;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class LogActivity extends Activity {

	private static final int MAX_MESSAGES = 100;
	private static ArrayAdapter<String> listAdapter = null;
	private static boolean isViewActive = false;
    private static List<String> messages = new ArrayList<String>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log);
		
	}

	@Override
	protected void onResume() {
		super.onResume();
        listAdapter = new ArrayAdapter<String>(getApplicationContext(), layout.log_line, messages);
        final ListView lv = (ListView)findViewById(R.id.logView);

        lv.setAdapter(listAdapter);
		isViewActive=true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isViewActive=false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.log, menu);
		return true;
	}
	
	public static void addMessage(String msg) {
		Calendar cal = Calendar.getInstance();
		String msgFormated = String.format("%02d:%02d:%02d %s", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), msg);

		if (messages.size() > MAX_MESSAGES) {
			messages.remove(0);
		}
		messages.add(msgFormated);
		if (isViewActive) {
			listAdapter.add(msgFormated);
		}
	}
}
