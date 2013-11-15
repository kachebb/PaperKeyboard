package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import android.annotation.SuppressLint;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.wisc.jj.BasicKNN;
import edu.wisc.jj.Item;
import edu.wisc.jj.SPUtil;

public class TestingActivity extends Activity implements RecBufListener, SensorEventListener {
	private static final String LTAG = "testing activity debug";
	private static final int STROKE_CHUNKSIZE = 2000;
	private BasicKNN mKNN;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	private Thread recordingThread;
	private RecBuffer mBuffer;
	private TextView text;
	private EditText editText;
	private short[] strokeBuffer;
	private volatile static String charas = "";
	private TextView texthint;
	private int clickTimes = 0;
	private String previousKey = "";
	private boolean halt = false;
	private boolean clickOnceAndSame = false;
	private double[] previousFeature;
	private int CLASSIFY_K = 4;
	private volatile List<Button> hintButtonList;

	//UI thread update, recording thread check
	private volatile long lastTouchScreenTime; // record the system time
														// when touch screen is
														// touched last time
	private SensorManager mSensorManager;
	private Sensor mGyro;
	private final float GYRO_TOUCHSCRREN_THRESHOLD=(float) 0.15; 
	private final long TOUCHSCREEN_TIME_INTERVAL=400000000;//400ms 	
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testing);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		text = (TextView) findViewById(R.id.text_detectionResult);
		texthint = (TextView) findViewById(R.id.text_detection);
		halt = false;
		// Intent i = getIntent();
		// mKNN = (KNN)i.getSerializableExtra("SampleObject");
		mKNN = MainActivity.mKNN;
		text.setText("Training Size:" + String.valueOf(mKNN.getTrainingSize()));
		editText = (EditText) findViewById(R.id.inputChar);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					// text.setText(v.getText());
					charas += v.getText().toString();
					// if training set is full, we need to remove the most far
					// point
					mKNN.addTrainingItem(v.getText().toString(),
							previousFeature);
					text.setText(charas);
					clickTimes = 0;
					halt = false;
					clickOnceAndSame = false;
					handled = true;
				}
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.toggleSoftInput(0, 0);
				lastTouchScreenTime=System.nanoTime();
				return handled;
			}
		});
		editText.clearFocus();

		// Init RecBuffer and thread
		mBuffer = new RecBuffer();
		recordingThread = new Thread(mBuffer);
		clickTimes = 0;
		Log.d(LTAG, "on create called once for main acitivity");
		this.register(mBuffer);
		recordingThread = new Thread(mBuffer);
		recordingThread.start();
		text.requestFocus();
		
		//register gyro sensor
	    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	    mGyro= mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
	    this.lastTouchScreenTime=System.nanoTime();
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
		//check if there is a screen touch nearby
		long curTime=System.nanoTime();
		if (Math.abs(curTime-this.lastTouchScreenTime) < this.TOUCHSCREEN_TIME_INTERVAL){
			Log.d("onRecBufFull", "screen touch detected nearby");
			return;
		}
		
		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
			int startIdx = KeyStroke.detectStroke_threshold(data);
			if (-1 == startIdx) { // when there is no stroke
				return;
			} else { // there is an stroke
				// this whole stroke is inside current buffer
				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
					// Log.d(LTAG,
					// "key stroke, data length > chuncksize, data length: " +
					// data.length);
					this.inStrokeMiddle = false;
					this.strokeSamplesLeft = 0;
					this.strokeBuffer = Arrays.copyOfRange(data, startIdx,
							startIdx + STROKE_CHUNKSIZE * 2);
					this.runAudioProcessing();
				} else { // there are some samples left in the next buffer
					this.inStrokeMiddle = true;
					this.strokeSamplesLeft = STROKE_CHUNKSIZE * 2
							- (data.length - startIdx);
					this.strokeBuffer = new short[STROKE_CHUNKSIZE * 2];
					System.arraycopy(data, startIdx, strokeBuffer, 0,
							data.length - startIdx);
				}
			}
		} else { // if in the middle of a stroke
			if (data.length >= strokeSamplesLeft) {
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
						- 1 - strokeSamplesLeft, strokeSamplesLeft);
				this.inStrokeMiddle = false;
				this.strokeSamplesLeft = 0;
				this.strokeBuffer = Arrays.copyOf(this.strokeBuffer,
						STROKE_CHUNKSIZE * 2);
				// get the audio features from this stroke and add it to the
				// training set, do it in background
				this.runAudioProcessing();
			} else { // if the length is smaller than the needed sample left
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
						- 1 - strokeSamplesLeft, data.length);
				this.inStrokeMiddle = true;
				this.strokeSamplesLeft = this.strokeSamplesLeft - data.length;
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
		this.strokeBuffer = null;
		// get features
		double[] features = SPUtil.getAudioFeatures(audioStrokeData);
		// if not halt by user input by screen keyboard, continue catching and
		// showing data-- jj. not using complex online learning right now
		// if(!halt)
		// this.dealwithBackSpace(features);
		// else Log.d(LTAG, "screen halts audioprocessing, we do nothing");

		String detectResult = mKNN.classify(features, this.CLASSIFY_K);
		charas += detectResult;
		mKNN.addTrainingItem(detectResult, features);
		// test on showing hints. need structural re-organization of code
		final Item[] lastDectections = mKNN.getClosestList();
		List<String> allLabels = mKNN.getLabelsFromItems(lastDectections);
		if (!allLabels.remove(detectResult)) {
			Log.d(LTAG,
					"the all labels returned don't contain the detect label. WRONG！ exiting");
			System.exit(1);
		}
		final String[] labels = allLabels.toArray(new String[allLabels.size()]);

		// update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// if(clickTimes < 2){

				texthint.setText("click Times:" + String.valueOf(clickTimes));
				text.setText(charas);

				RelativeLayout rl = (RelativeLayout) findViewById(R.id.testActivity_layout);
				// rm all existing hint buttons on screen
				if (null == hintButtonList) {
					hintButtonList = new LinkedList<Button>();
				} else {
					for (Button mButton : hintButtonList) {
						rl.removeView(mButton);
					}
					hintButtonList.clear();
				}
				// create new hint buttons
				for (int i = 0; i < labels.length; i++) {
					Button myButton = new Button(getApplicationContext());
					hintButtonList.add(myButton);
					myButton.setText(labels[i]);
					myButton.setId(100 + i);
					myButton.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							// race condition, UI is updating KNN, need to make
							// sure the background thread will not change mKNN
							mKNN.changeSampleLabel(((Button) v).getText()
									.toString(), 0);
							lastTouchScreenTime=System.nanoTime();							
						}
					});
					RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(
							LayoutParams.WRAP_CONTENT,
							LayoutParams.WRAP_CONTENT);
					rp.addRule(RelativeLayout.BELOW, R.id.text_detectionResult);
					if (0 != i) {
						rp.addRule(RelativeLayout.RIGHT_OF,
								myButton.getId() - 1);
					}
					myButton.setLayoutParams(rp);
					rl.addView(myButton);
				}

				// }else
				// {
				// texthint.setText("click the input box to input the correct char");
				// }
			}
		});
	}

	/**
	 * This is just a function that is to make runAudioProcessing function more
	 * clear It decides what to do according to backspace click time
	 * 
	 * @param features
	 *            : features that are extracted by runAudiaoProcessing
	 */
	private void dealwithBackSpace(double[] features) {
		String newKey;
		if (clickTimes != 1) { // only one way to make click Time > 1, that is
								// user click backSpace continuously
			newKey = mKNN.classify(features, this.CLASSIFY_K);
			mKNN.addTrainingItem(newKey, features);// online training
			charas += newKey;
			Log.d(LTAG, "clockTimes:0, charas: " + charas);
			clickTimes = 0;
		} else {
			newKey = mKNN.classify(features, this.CLASSIFY_K);
			if (newKey != previousKey) // we think this is user's input error
			{
				Item currentItem = new Item(features);
				Item[] closest = mKNN.getClosestList();
				// if distance is greater than a threshold, we choose the next
				// closest
				if (mKNN.findDistance(closest[0], currentItem) > mKNN.DISTTHRE) {
					clickTimes = 0;
					Log.d(LTAG, "clockTime:1 different form previous, charas: "
							+ charas);
				}
				charas += newKey;
			} else { // Newkey equals previous key, it might be our error,
				// if dist(feature, newKey) > threshold, we choose next closest
				// key as output
				// get the nearest 2 nodes
				mKNN.classify(features, this.CLASSIFY_K);
				Item[] closest = mKNN.getClosestList();
				newKey = closest[1].category;
				charas += newKey;
				Log.d(LTAG, "clockTime:1, same as previous, charas: " + charas);
				clickOnceAndSame = true;// pass this value to deal with the
										// condition that user want to click
										// several times of backspace
			}
			// pass previous feature to next stage
			this.previousKey = newKey;
			this.previousFeature = features;
		}

	}

	/***
	 * This fuction is called when user click backspace button on screen It
	 * remove one character from the displayed characters if it is user click
	 * backspace input and then click backspace, we regard this as
	 */

	public void onClickButtonBackSpace(View view) {
		int len = charas.length();
		charas = charas.substring(0, len - 1);
		text.setText(charas);
		clickTimes++;
		texthint.setText("clickTimes:" + String.valueOf(clickTimes));
		if (clickTimes == 2 && clickOnceAndSame) {
			this.halt = true;
			mKNN.removeLatestInput(); // we did wrong training when testing,
										// remove it
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
					.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
		}
	}

	//use sensors to detect whether user is touching screen
	//touching screen may likely to cause an detection of key. Try to avoid such case
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//do nothing on accuracy changed
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		//running on UI thread. double check
		float[] values=event.values;
//		Log.d(LTAG, "on sensor changed. time: "+System.nanoTime());
		for (float value: values){
			if (Math.abs(value) > this.GYRO_TOUCHSCRREN_THRESHOLD){
//				Log.d(LTAG, "touch screen!!!!! "+value);				
				this.lastTouchScreenTime=System.nanoTime();
				break;
			}
		}
	}
	
	@Override
	  protected void onResume() {
	    super.onResume();
	    mSensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_GAME);
	  }

	  @Override
	  protected void onPause() {
	    super.onPause();
	    mSensorManager.unregisterListener(this);
	  }	
}
