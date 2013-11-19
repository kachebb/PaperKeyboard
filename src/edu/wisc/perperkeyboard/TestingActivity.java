package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.widget.ToggleButton;
import edu.wisc.jj.BasicKNN;
import edu.wisc.jj.Item;
import edu.wisc.jj.SPUtil;


public class TestingActivity extends Activity implements RecBufListener, SensorEventListener {
	/*************constant values***********************/
	private static final String LTAG = "testing activity debug";
	private static final int STROKE_CHUNKSIZE = 2000;
	private final float GYRO_TOUCHSCRREN_THRESHOLD=(float) 0.15; 
	private final long TOUCHSCREEN_TIME_INTERVAL=400000000;//400ms 	
	
	/*************UI ********************************/
	private TextView text;
	private EditText editText;
	private TextView texthint;
	private volatile static String charas = "";
	private volatile static List<String> showDetectResult;
	private TextView debugKNN;
	private TextView totalInputText;
	private TextView errorInputText;
	private ToggleButton ShiftButton;
	private ToggleButton CapsButton;
	/*************Audio Processing*******************/
	private BasicKNN mKNN;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	private Thread recordingThread;
	private RecBuffer mBuffer ;	
	private short[] strokeBuffer;

	/************self-correction and online training control**************/
	private int clickTimes = 0;
	private boolean onlineTraining = true;
	private String previousKey = "";
	private boolean halt = false;
	private boolean clickOnceAndSame = false;
	private double[] previousFeature;
	private int CLASSIFY_K = 3;
	private volatile List<Button> hintButtonList;
	/********************Shift and caps*****************************/
	private boolean shift;
	private boolean caps;
	/********************statistics**************************/
	private int totalInputTimes = 0;
	private int errorInputTimes = 0;	
	/************gyro scope sensor*********************/
	//UI thread update, recording thread check
	private volatile long lastTouchScreenTime; // record the system time
														// when touch screen is													// touched last time
	private SensorManager mSensorManager;
	private Sensor mGyro;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/************init UI************************/
		setContentView(R.layout.activity_testing);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		text = (TextView) findViewById(R.id.text_detectionResult);
		texthint = (TextView) findViewById(R.id.text_detection);
		editText = (EditText) findViewById(R.id.inputChar);
		debugKNN = (TextView) findViewById(R.id.text_debugKNN);
		totalInputText = (TextView) findViewById(R.id.text_inputTimes);
		errorInputText = (TextView) findViewById(R.id.text_errorTimes);
		ShiftButton = (ToggleButton) findViewById(R.id.toggle_shift);
		CapsButton = (ToggleButton) findViewById(R.id.toggle_caps);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		     
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		           // text.setText(v.getText());
		        	showDetectResult.add(v.getText().toString());
		        	charas += v.getText().toString();
		        	//if training set is full, we need to the most far point
		        	mKNN.addTrainingItem(v.getText().toString(), previousFeature);
		        	//text.setText(charas);
		        	text.setText(showDetectResult.toString());
		        	debugKNN.setText(mKNN.getChars());
		        	clickTimes = 0;
		        	halt = false;
		        	clickOnceAndSame = false;
		            handled = true;
		        }
		        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		        imm.toggleSoftInput(0, 0);
		        return handled;
		    }
		});
		editText.clearFocus();
		
		/***********init values******************/
		halt = false;
		//Intent i = getIntent();
		//mKNN = (KNN)i.getSerializableExtra("SampleObject");
		mKNN = MainActivity.mKNN;
		text.setText("Training Size:"+String.valueOf(mKNN.getTrainingSize()));
		debugKNN.setText(mKNN.getChars());
		showDetectResult = new ArrayList<String>();
		/****************Init RecBuffer and thread*****************/
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
		/**********statistic***************/
		totalInputTimes++;
		/**********caps and shift*******/
		//set shift and caps condition
		final String detectResult = mKNN.classify(features, this.CLASSIFY_K);
		//decide which character to show
		CapTrans cap = new CapTrans();
		if(this.caps){
			if(!this.shift){
				charas+= cap.transWhenCaps(detectResult);
				showDetectResult.add(cap.transWhenCaps(detectResult));
			}else {
				charas+= detectResult;
				showDetectResult.add(detectResult);
			}
		}else{
			if(this.shift){
				showDetectResult.add(cap.transWhenCaps(detectResult));
				charas+= cap.transWhenCaps(detectResult);
			}
			else{
				charas+=detectResult;
				showDetectResult.add(detectResult);
			}
		}
		this.shift = false; //clear shift after use
		if(detectResult.equals("LShift") || detectResult.equals("RShift")){
			this.shift = true;
		}
		if(detectResult.equals("Caps")){
			this.caps = !this.caps;
		}
		//add unsure sample to staging area
		mKNN.addToStage(detectResult, features);
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
			@SuppressLint("NewApi")
			@Override
			public void run() {
				// if(clickTimes < 2){
				/***Updata UI********/
				texthint.setText("click Times:" + String.valueOf(clickTimes));
				//text.setText(charas);
				text.setText(showDetectResult.toString());
				totalInputText.setText(String.valueOf(totalInputTimes));
				errorInputText.setText(String.valueOf(errorInputTimes));
				if(shift){
					//ShiftButton.animate();
					ShiftButton.setChecked(true);
					texthint.setText("SHIFT");
				}
				else ShiftButton.setChecked(false);
				if(caps){
					CapsButton.setChecked(true);
				}else CapsButton.setChecked(false);
				
				/********hint buttons*************/
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
							lastTouchScreenTime=System.nanoTime();														
							mKNN.correctWrongDetection(((Button) v).getText()
									.toString(),detectResult);
							charas=charas.substring(0,charas.length()-1);
							showDetectResult.remove(showDetectResult.size()-1);
							charas+=((Button) v).getText().toString();
							showDetectResult.add(((Button)v).getText().toString());
							//text.setText(charas);
							text.setText(showDetectResult.toString());
							Log.d("after correction: ", mKNN.toString());
							
							errorInputTimes++;
							errorInputText.setText(String.valueOf(errorInputTimes));
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
		if(len > 0)
			charas = charas.substring(0, len-1);
		//this.halt = true;
//		text.setText(charas);
		int len1 = showDetectResult.size();
		if(len > 0)
			showDetectResult.remove(len1-1);
		text.setText(showDetectResult.toString());
		debugKNN.setText(mKNN.getChars());
		clickTimes++;
		texthint.setText("clickTimes:" + String.valueOf(clickTimes));
		if (clickTimes == 2 && clickOnceAndSame) {
			this.halt = true;
			//mKNN.removeLatestInput(); // we did wrong training when testing,
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
