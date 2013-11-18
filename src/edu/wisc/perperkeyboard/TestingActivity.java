package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
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
	private TextView debugKNN;
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
		
		editText.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		     
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		           // text.setText(v.getText());
		        	charas += v.getText().toString();
		        	//if training set is full, we need to the most far point
		        	mKNN.addTrainingItem(v.getText().toString(), previousFeature);
		        	text.setText(charas);
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
		/****************Init RecBuffer and thread*****************/
		mBuffer = new RecBuffer();
		recordingThread = new Thread(mBuffer);
		clickTimes = 0;
		Log.d(LTAG, "on create called once for main acitivity");
		this.register(mBuffer);
		recordingThread = new Thread(mBuffer);
		recordingThread.start();
		text.requestFocus();		
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
			//		Log.d(LTAG,	"key stroke, data length > chuncksize, data length: "									+ data.length);
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
		// if not halt by user input by screen keyboard, continue catching and showing data
		if(!halt)
			this.dealwithBackSpace(features);
		else Log.d(LTAG, "screen halts audioprocessing, we do nothing");
		//update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				//if(clickTimes < 2){
					//Toast.makeText(getApplicationContext(), "detection result "+charas, Toast.LENGTH_SHORT).show();				
					texthint.setText("click Times:" + String.valueOf(clickTimes));
					text.setText(charas);
					debugKNN.setText(mKNN.getChars());
				//}else
				//{
				//	texthint.setText("click the input box to input the correct char");
				//}
			}
		});
	}
	/**
	 * This is just a function that is to make runAudioProcessing function more clear
	 * It decides what to do according to backspace click time
	 * 
	 * @param features: features that are extracted by runAudiaoProcessing
	 */ 
	
	private void dealwithBackSpace(double[] features){
		String newKey;
		if(clickTimes != 1){ //only one way to make click Time > 1, that is user click backSpace continuously
			newKey = mKNN.classify(features, 1);
			if(onlineTraining)
				mKNN.addTrainingItem(newKey, features);//online training
			charas += newKey;
			Log.d(LTAG, "clockTimes:0, charas: "+charas);
			clickTimes = 0;
		}
		else{
			newKey = mKNN.classify(features, 1);
			if(newKey != previousKey) // we think this is user's input error
			{
				Item currentItem = new Item(features);
				Item[] closest = mKNN.getClosestList();
				//only if distance is greater than a threshold we thinks they are two different key
			//  if(mKNN.findDistance(closest[0], currentItem) > mKNN.DISTTHRE) 
			//	{
					clickTimes = 0;
					Log.d(LTAG, "clockTime:1 different form previous, charas: "+charas);
			//	}
				charas += newKey;
			}else{  //Newkey equals previous key, it might be our error, 
				//if dist(feature, newKey) > threshold, we choose next closest key as output
				//get the nearest 2 nodes
				mKNN.classify(features, 2);
				Item[] closest = mKNN.getClosestList();
				newKey = closest[1].category;
				charas += newKey;
				Log.d(LTAG, "clockTime:1, same as previous, charas: "+charas);
				clickOnceAndSame = true;//pass this value to deal with the condition that user want to click several times of backspace
			}
		}
		//pass previous feature to next stage
		this.previousKey = newKey;
		this.previousFeature = features;
		
	}
	
	/***
	 * This fuction is called when user click backspace button on screen
	 * It remove one character from the displayed characters 
	 * if it is user click backspace input and then click backspace, we regard this as 
	 */
	
	public void onClickButtonBackSpace(View view)
	{
		int len = charas.length();
		if(len > 0)
			charas = charas.substring(0, len-1);
		//this.halt = true;
	    
		text.setText(charas);
		debugKNN.setText(mKNN.getChars());
		clickTimes++;
		texthint.setText("clickTimes:" + String.valueOf(clickTimes));
		if(onlineTraining)
			mKNN.removeLatestInput();
		// SLEEP 1 SECONDS HERE ... But it won't work to stop the detection
		 //  try{ Thread.sleep(1000);}catch(Exception e){}	
		//    this.halt = false;
		if(clickTimes == 2 && clickOnceAndSame)
		{
			this.halt = true;
			if(onlineTraining)
				mKNN.removeLatestInput(); //we did wrong training when testing, remove it
		    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
		        .showSoftInput(editText, InputMethodManager.SHOW_FORCED);
		}
	}
}
