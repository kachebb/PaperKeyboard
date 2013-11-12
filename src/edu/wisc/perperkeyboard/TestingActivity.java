package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
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
	private static String charas = "";
	private TextView texthint;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testing);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		text = (TextView) findViewById(R.id.text_detectionResult);
		texthint = (TextView) findViewById(R.id.text_detection);
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
		//new detectKeyStroke().execute();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Show the Up button in the action bar.
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
		recordingThread = new Thread(mBuffer);
		recordingThread.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.testing, menu);
		return true;
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
	public void onRecBufFull(short[] data) {
		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
			int startIdx = KeyStroke.detectStroke_threshold(data);
			if (-1 == startIdx) { // when there is no stroke
				return;
			} else { // there is an stroke
				// this whole stroke is inside current buffer
				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
					Log.d(LTAG,
							"key stroke, data length > chuncksize, data length: "
									+ data.length);
					this.inStrokeMiddle = false;
					this.strokeSamplesLeft = 0;
					this.strokeBuffer = Arrays.copyOfRange(data, startIdx,
							startIdx + STROKE_CHUNKSIZE * 2);
//					synchronized (syncObj) {
//						syncObj.notify();
//					}
					this.runAudioProcessing();
				} else { // there are some samples left in the next buffer
					this.inStrokeMiddle = true;
					this.strokeSamplesLeft = STROKE_CHUNKSIZE * 2
							- (data.length - startIdx);
					this.strokeBuffer = new short[STROKE_CHUNKSIZE * 2];
					System.arraycopy(data, startIdx, strokeBuffer, 0,
							data.length - startIdx);
					Log.d(LTAG,
							"key stroke, data length < chuncksize, stroke start idx: "
									+ startIdx + " stroke data length: "
									+ String.valueOf(data.length - startIdx)
									+ " stroke samples left "
									+ this.strokeSamplesLeft);
				}
			}
		} else { // if in the middle of a stroke
			if (data.length >= strokeSamplesLeft) {
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
						- 1 - strokeSamplesLeft, strokeSamplesLeft);
				this.inStrokeMiddle = false;
				this.strokeSamplesLeft = 0;
				this.strokeBuffer= Arrays.copyOf(this.strokeBuffer,
						STROKE_CHUNKSIZE * 2);
				// get the audio features from this stroke and add it to the
				// training set, do it in background
				this.runAudioProcessing();				
				Log.d(LTAG, "key stroke, data length >= samples left "
						+ " stroke data length: " + String.valueOf(data.length)
						+ " stroke samples left " + this.strokeSamplesLeft);
			} else { // if the length is smaller than the needed sample left
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
						- 1 - strokeSamplesLeft, data.length);
				this.inStrokeMiddle = true;
				this.strokeSamplesLeft = this.strokeSamplesLeft - data.length;
				Log.d(LTAG,
						"key stroke, data length < samples left size " + " stroke data length: "
								+ String.valueOf(data.length)
								+ " stroke samples left "
								+ this.strokeSamplesLeft);
			}
		}
	}
	
	/**
	 * audio processing. extract features from audio. Add features to KNN.
	 */
	public void runAudioProcessing() {
		// get the audio features from this stroke and add it
		// to the training set, do it in background
		short[] audioStroke = this.strokeBuffer;
		// separate left and right channel
		short[][] audioStrokeData = KeyStroke.seperateChannels(audioStroke);
		this.strokeBuffer=null;
		// get features
		double[] features = SPUtil.getAudioFeatures(audioStrokeData);
		charas += mKNN.classify(features, 1);
		Log.d(LTAG, "detecting result"+charas);
		
		
		//update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				text.setText(charas);
			}
		});
	}
	/***
	 * This fuction is called when user click backspace button on screen
	 * It remove one character from the displayed characters 
	 */
	
	public void onClickButtonBackSpace(View view)
	{
		int len = charas.length();
		charas = charas.substring(0, len-1);
		text.setText(charas);
	}

}
