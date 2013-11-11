package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.wisc.jj.KNN;
import edu.wisc.jj.SPUtil;

public class TestingActivity extends Activity implements RecBufListener{
	private static final String LTAG = "testing activity debug";	
	private static final int STROKE_CHUNKSIZE = 2000;
	private KNN mKNN;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	private Thread recordingThread;
	private RecBuffer mBuffer ;
	private TextView text;
	private short[] strokeBuffer;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testing);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		text = (TextView) findViewById(R.id.text_detectionResult);
		Intent i = getIntent();
		//mKNN = (KNN)i.getSerializableExtra("SampleObject");
		mKNN = MainActivity.mKNN;
		text.setText(String.valueOf(mKNN.test));
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
		        imm.toggleSoftInput(0, 0);
		        return handled;
		    }
		});
		//Init RecBuffer and thread
		mBuffer = new RecBuffer();
		recordingThread = new Thread(mBuffer);
				// register myself to RecBuffer to let it know I'm listening to him
		this.register(mBuffer);
		recordingThread = new Thread(mBuffer);
	//	new detectKeyStroke().execute();
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
	
	private class detectKeyStroke extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
                text.setText("pre");
        }

        @Override
        protected Void doInBackground(Void... params) {
               // short[] audioStroke=params[1];
                //separate left and right channel
        	recordingThread.start();
        	try {
				// wait the recording thread in background ( will not block UI thread)
				recordingThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return null;
        }

        @Override
        protected void onPostExecute(Void params) {
        	text.setText("finish");
        	onPreExecute();
        }
	}
	
	/**
     * This method will be called every time the buffer of recording thread is
     * full
     * 
     * @param data
     *            : audio data recorded. For stereo data, data at odd indexes
     *            belongs to one channel, data at even indexes belong to another
     * @throws FileNotFoundException
     */
	
	
    @SuppressLint("NewApi")
	@Override
    public void onRecBufFull(short[] data) {
            Log.d(LTAG, "I'm called at time: " + System.nanoTime()+ " thread id : " + Thread.currentThread().getId());
            if(KeyStroke.detectStroke_threshold(data) > 0){
            	text.setText("detected");
            	recordingThread.interrupt();
            }
            
            
    }
    /**
	 * use this method to register on RecBuffer to let it know who is listening
	 * 
	 * @param r
	 */
	
	@Override
	public void register(RecBuffer r) {
		// set receiving thread to be this class
		r.setReceiver(this);
	}
	

}
