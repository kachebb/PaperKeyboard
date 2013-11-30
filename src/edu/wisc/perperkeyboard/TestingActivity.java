package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
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


public class TestingActivity extends Activity implements RecBufListener{
	/*************constant values***********************/
	private static final String LTAG = "testing activity debug";
	private static final int STROKE_CHUNKSIZE = 2000;
	
	/*************UI ********************************/
	private TextView text;
	private EditText editText;
	private TextView texthint;
	private volatile static String charas = "";
	private volatile static List<String> showDetectResult;
	private TextView debugKNN;
	private TextView totalInputText;
	private TextView totalAccuracyText;
	private ToggleButton ShiftButton;
	private ToggleButton CapsButton;
	private TextView recentAccuracyText;
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
	private Statistic stat;	
	
	/********************gyro helper**************************/
	private GyroHelper mGyro;
	
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
		totalAccuracyText = (TextView) findViewById(R.id.text_errorTimes);
		ShiftButton = (ToggleButton) findViewById(R.id.toggle_shift);
		CapsButton = (ToggleButton) findViewById(R.id.toggle_caps);
		recentAccuracyText = (TextView) findViewById(R.id.text_recentAccuracy);
		
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
		stat = new Statistic();
		
		//get gyro helepr
		this.mGyro=new GyroHelper(this.getApplicationContext());
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
		/*********************check whether gyro agrees that there is a key stroke *******************/
		long curTime=System.nanoTime();
		//first case: screen is being touched
		if (Math.abs(curTime-this.mGyro.lastTouchScreenTime) < mGyro.TOUCHSCREEN_TIME_INTERVAL){
			Log.d("onRecBufFull", "screen touch detected nearby");
			return;
		//2nd case: there is indeed some vibrations on the desk			
		} else if (Math.abs(curTime-this.mGyro.lastTouchDeskTime) >= mGyro.DESK_TIME_INTERVAL){ 
			Log.d("onRecBufFull", "no desk vibration feeled. not valid audio data. lastTouchDesktime: "+this.mGyro.lastTouchDeskTime + " .current time: "+curTime);
			return;
		}
		
		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
			int startIdx = KeyStroke.detectStroke_threshold(data);
			if (-1 == startIdx) { // when there is no stroke
				return;
			} else { // there is an stroke
				// this whole stroke is inside current buffer
				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
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
		stat.addInput(false); //we suppose the input is correct
		/**********caps and shift*******/
		//set shift and caps condition
		final String detectResult = mKNN.classify(features, this.CLASSIFY_K);
		//decide which character to show
		this.updateData(detectResult);
		
		this.shift = false; //clear shift after use
		if(detectResult.equals("LShift") || detectResult.equals("RShift")){
			this.shift = true;
		}
		if(detectResult.equals("Caps")){
			this.caps = !this.caps;
		}
		//add unsure sample to staging area
		mKNN.addToStage(detectResult, features);
		
		//get hints from KNN
		//argument is the number of hints needed
		final String[] labels = mKNN.getHints(4);

		// update UI
		this.runOnUiThread(new Runnable() {
			@SuppressLint("NewApi")
			@Override
			public void run() {
				// if(clickTimes < 2){
				/***Update UI********/
				updateUI();
				
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
							mGyro.lastTouchScreenTime=System.nanoTime();														
							mKNN.correctWrongDetection(((Button) v).getText()
									.toString(),detectResult);
							charas=charas.substring(0,charas.length()-1);
							showDetectResult.remove(showDetectResult.size()-1);
							//update output according to shift and caps
							updateData(((Button)v).getText().toString());
							
							/*******if false recognition result is shift or caps**************/
							if(detectResult.equals("LShift") || detectResult.equals("RShift")){
								shift = false;
							}
							if(detectResult.equals("Caps")){
								caps = !caps;
							}
							
							/******if new correction input is shift or caps**********/
							if(((Button)v).getText().toString().equals("LShift")
									|| ((Button)v).getText().toString().equals("RShift"))
								shift = true;
							if(((Button)v).getText().toString().equals("Caps"))
								caps = true;
							
							
							Log.d("after correction: ", mKNN.toString());
							
							stat.addInput(true);
							//Update UI;
							updateUI();
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

	
	public void onClickButtonFinish(View view){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		stat.doLogs("PaperKeyboard" );//+ dateFormat.format(date));
		android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	/***
	 * when ever new input is changed, use this function to decide which data to be add into the string
	 * this function concerns the SHIFT and CAPSLOCK
	 * @param newData
	 */
	public void updateData(String newData){
		String detectResult = newData;
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
		
	}
	
	
	public void updateUI(){
		/***Update UI********/
		texthint.setText("click Times:" + String.valueOf(clickTimes));
		//text.setText(charas);
		text.setText(showDetectResult.toString());
		totalInputText.setText(String.valueOf(this.stat.totalInputTimes));
		totalAccuracyText.setText(String.valueOf(this.stat.totalAccuracy * 100) + "%");
		if(shift){
			//ShiftButton.animate();
			ShiftButton.setChecked(true);
		}
		else ShiftButton.setChecked(false);
		if(caps){
			CapsButton.setChecked(true);
		}else CapsButton.setChecked(false);
		//totalAccuracyText.setText(String.valueOf(errorInputTimes));
		debugKNN.setText(mKNN.getChars());
		recentAccuracyText.setText(String.valueOf(stat.recentAccuracy*100) + "%");
		
	}
	
	@Override
	  protected void onResume() {
	    super.onResume();
	    if (mGyro != null){
	    	mGyro.register();
	    } else {
	    	Log.d(LTAG, "try to register gyro sensor. but there is no GyroHelper class used");
	    }
	  }

	  @Override
	  protected void onPause() {
	    super.onPause();
	    if (mGyro != null){
	    	mGyro.unregister();
	    } else {
	    	Log.d(LTAG, "try to register gyro sensor. but there is no GyroHelper class used");
	    }
	  }	
}
