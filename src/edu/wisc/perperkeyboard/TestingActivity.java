package edu.wisc.perperkeyboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.wisc.jj.KNN;

public class TestingActivity extends Activity {

	
	private TextView text;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testing);
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		 // Make sure we're running on Honeycomb or higher to use ActionBar APIs
		text = (TextView) findViewById(R.id.text_detectionResult);
		Intent i = getIntent();
		KNN knn = (KNN)i.getSerializableExtra("SampleObject");
		
		text.setText(String.valueOf(knn.test));
		EditText editText = (EditText) findViewById(R.id.inputChar);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		            text.setText(v.getText());
		            handled = true;
		        }
		        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		   //     imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		        imm.toggleSoftInput(0, 0);
		        return handled;
		    }
		});
		
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Show the Up button in the action bar.
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.testing, menu);
		return true;
	}
	
	

}
