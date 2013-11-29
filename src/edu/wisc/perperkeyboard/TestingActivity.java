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
	
	/********************gyro helper**************************/
	private GyroHelper mGyro;

	/********************dictionary**************************/
	private Dictionary mDict;
	private StringBuilder dictBase; //used to store the previous  
	private static final String WORD_SPLITTER = "a";	
	
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
		
		/********************gyro helper**************************/				
		this.mGyro=new GyroHelper(this.getApplicationContext());
		
		/********************dictionary**************************/
		//use dict_2of12inf in resource/raw folder
		this.mDict=new Dictionary(getApplicationContext(), R.raw.dict_2of12inf);
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
//			Log.d("onRecBufFull", "no desk vibration feeled. not valid audio data. lastTouchDesktime: "+this.mGyro.lastTouchDeskTime + " .current time: "+curTime);
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
		totalInputTimes++;
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
		
		
		//get hints from dictionary
		String historyLower=charas.substring(0,(charas.length()-1 >0 )?charas.length()-1:0).toLowerCase();
		Log.d(LTAG,"history lower :" +historyLower);
		int splitterIndex=historyLower.lastIndexOf(WORD_SPLITTER.charAt(0));
		int endIndex=(historyLower.length() >0)? historyLower.length():0;
		Log.d(LTAG,"start index: "+(splitterIndex+1));		
		Log.d(LTAG,"end index: "+endIndex);
		String unfinishedWord="";
		if ((splitterIndex+1) <=endIndex)
			unfinishedWord=historyLower.substring(splitterIndex+1, endIndex);
		Log.d(LTAG,"to dictionary:" +unfinishedWord);		
		List<String> hintsFromDict=this.mDict.getPossibleChar(unfinishedWord);

		//get hints from KNN with regarding to the dictionary result
		//argument is the number of hints needed
		final String[] labels = mKNN.getHints(5,hintsFromDict);
		
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
							
							errorInputTimes++;
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
		totalInputText.setText(String.valueOf(totalInputTimes));
		errorInputText.setText(String.valueOf(errorInputTimes));
		if(shift){
			//ShiftButton.animate();
			ShiftButton.setChecked(true);
		}
		else ShiftButton.setChecked(false);
		if(caps){
			CapsButton.setChecked(true);
		}else CapsButton.setChecked(false);
		errorInputText.setText(String.valueOf(errorInputTimes));
		debugKNN.setText(mKNN.getChars());
		
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
