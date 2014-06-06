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
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.wisc.jj.BasicKNN;
import edu.wisc.jj.SPUtil;
import edu.wisc.liyuan.dictionaryCorrection.DictionaryControllor;
import edu.wisc.liyuan.dictionaryCorrection.DictionaryControllorImpl;

public class TestingActivity extends Activity implements RecBufListener{
	/*************constant values***********************/
	private static final String LTAG = "testing activity debug";
	private static int STROKE_CHUNKSIZE;
	private final Context context = this;
	/*************UI ********************************/
	private TextView text;
	private EditText editText;
//	private TextView textInputRate;
	private volatile static String charas = "";
	private volatile static List<String> showDetectResult;
//	private TextView debugKNN;
	private TextView totalInputText;
//	private TextView totalAccuracyText;
	private ToggleButton ShiftButton;
	private ToggleButton CapsButton;
//	private TextView recentAccuracyText;
	private Button dictButton;
	/*************Audio Processing*******************/
	private BasicKNN mKNN;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	private Thread recordingThread;
	private RecBuffer mBuffer ;	
	private short[] strokeBuffer;

	/************self-correction and online training control**************/
	private int clickTimes = 0;
//	private boolean onlineTraining = true;
	private String previousKey = "";
	private boolean halt = false;
	private boolean clickOnceAndSame = false;
	private int CLASSIFY_K = 1;
	private volatile List<Button> hintButtonList;
	private volatile List<Button> CorrectionButtonList;
	
	/********************Shift and caps*****************************/
	private int shift;
	private boolean caps;
	/********************statistics**************************/
	private Statistic stat;	
	
	/********************gyro helper**************************/
	private static GyroHelper mGyro;

	/********************dictionary**************************/
	private Dictionary mDict;
	//use WORD_SPLITTER to separate words from words
	//should be " ". right now for training simplicity, used an arbitrary character
	private static final String WORD_SPLITTER = " ";	
	private boolean dictStatus = true;
	
	/********************DictionaryCorrector****************/
	private DictionaryControllor dictionaryControllor;
	

	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//Debug.startMethodTracing("Creation");
		super.onCreate(savedInstanceState);
		/************init UI************************/
		setContentView(R.layout.activity_testing);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		text = (TextView) findViewById(R.id.text_detectionResult);
	//	textInputRate = (TextView) findViewById(R.id.text_detection);
		editText = (EditText) findViewById(R.id.inputChar);
	//	debugKNN = (TextView) findViewById(R.id.text_debugKNN);
		totalInputText = (TextView) findViewById(R.id.text_inputTimes);
		//totalAccuracyText = (TextView) findViewById(R.id.text_errorTimes);
		ShiftButton = (ToggleButton) findViewById(R.id.toggle_shift);
		CapsButton = (ToggleButton) findViewById(R.id.toggle_caps);
	//	recentAccuracyText = (TextView) findViewById(R.id.text_recentAccuracy);
		dictButton = (Button) findViewById(R.id.button_Dict);
		editText.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View v) {
		   // 	halt = true;
		    }
		});
		editText.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		        //halt = true;
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		    
		        	mKNN.correctWrongDetection( v.getText()
							.toString(),previousKey);
					charas=charas.substring(0,charas.length()-1);
					showDetectResult.remove(showDetectResult.size()-1);
					//update output according to shift and caps
					updateData(v.getText().toString());
		        	halt = false;
		        	stat.addInput(2,v.getText().toString());
		        	updateUI();
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
//		debugKNN.setText(mKNN.getChars());
		showDetectResult = new ArrayList<String>();
		dictStatus = false;
		if(dictStatus) dictButton.setText("Using Dict");
		else dictButton.setText("Non-Dict");
		
		this.STROKE_CHUNKSIZE = MainActivity.STROKE_CHUNKSIZE;
		/****************Init RecBuffer and thread*****************/
		mBuffer = new RecBuffer();
		recordingThread = new Thread(mBuffer);
		clickTimes = 0;
		Log.d(LTAG, "on create called once for main acitivity");
		this.register(mBuffer);
		//recordingThread = new Thread(mBuffer);
		Toast.makeText(getApplicationContext(),
				"Please Wait Until This disappear", Toast.LENGTH_SHORT).show();
		recordingThread.start();
		text.requestFocus();
		stat = new Statistic(System.currentTimeMillis());
		
		/********************gyro helper**************************/				
		this.mGyro=new GyroHelper(this.getApplicationContext());
		this.mGyro.GYRO_DESK_THRESHOLD = MainActivity.mGyro.GYRO_DESK_THRESHOLD;
		/********************dictionary**************************/
		//use dict_2of12inf in resource/raw folder
		this.mDict=new Dictionary(getApplicationContext(), R.raw.dict_2of12inf);
		
		/****************dictionaryInit****************************/
		dictionaryControllor = new DictionaryControllorImpl(Environment.getExternalStorageDirectory() + getString(R.string.DictionaryFilePath));
	//	Debug.stopMethodTracing();
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
		
		/*******************smooth the data******************/
		SPUtil.smooth(data);
		
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
					if(!halt)
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
				if(!halt)
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
	String[] results;
	public void runAudioProcessing() {
		// get the audio features from this stroke and add it
		// to the training set, do it in background
		short[] audioStroke = this.strokeBuffer;
		// separate left and right channel
		short[][] audioStrokeData = KeyStroke.seperateChannels(audioStroke);
		this.strokeBuffer = null;
		// get features
		double[] features= SPUtil.getAudioFeatures(audioStrokeData);
		// if not halt by user input by screen keyboard, continue catching and
		// showing data-- jj. not using complex online learning right now
		// if(!halt)
		// this.dealwithBackSpace(features);
		// else Log.d(LTAG, "screen halts audioprocessing, we do nothing");
		
		
		
		/*********get hints from dictionary*****************/
		List<String> hintsFromDict=null;
		if (this.dictStatus){
			String historyLower=charas.toLowerCase();
			Log.d(LTAG,"history lower :" +historyLower);
			int splitterIndex=historyLower.lastIndexOf(WORD_SPLITTER.charAt(0));
			int endIndex=(historyLower.length() >0)? historyLower.length():0;
			Log.d(LTAG,"start index: "+(splitterIndex+1));		
			Log.d(LTAG,"end index: "+endIndex);
			String unfinishedWord="";
			if ((splitterIndex+1) <=endIndex)
				unfinishedWord=historyLower.substring(splitterIndex+1, endIndex);
			Log.d(LTAG,"to dictionary:" +unfinishedWord);		
			hintsFromDict=this.mDict.getPossibleChar(unfinishedWord);
		}
		
		/********** detect using KNN *******/		
		final String detectResult = mKNN.classify(features, this.CLASSIFY_K,hintsFromDict);
		this.previousKey =  detectResult;	
		long startTime = System.currentTimeMillis();

//		Debug.startMethodTracing("Corrector",67108864);
		if(detectResult.equals(" ")){
			dictionaryControllor.clear();
		}else{
			dictionaryControllor.attach(detectResult.charAt(0));
		}
		results =  dictionaryControllor.getCorrectionList();
		long endTime   = System.currentTimeMillis();
		long totalTime = endTime - startTime;
//		Debug.stopMethodTracing();
		Log.d("CalTime",String.valueOf(totalTime));
		Log.d("REACH","5");
		

		//add unsure sample to staging area
		mKNN.addToStage(detectResult, features);
		
		/**********statistic***************/
		stat.addInput(0,detectResult); //we suppose the input is correct		

		
		/**********caps and shift*******/
		//set shift and caps condition
		if(this.shift>0)
			this.shift --; //clear shift after use
		if(detectResult.equals("LShift") || detectResult.equals("RShift")){
			this.shift = 2;
		}
		if(detectResult.equals("Caps")){
			this.caps = !this.caps;
		}
		
		//get hints from KNN with regarding to the dictionary result
		//argument is the number of hints needed
		final List<String> labels = mKNN.getHints(5,hintsFromDict);
		//always show word_splitter as a hint
		labels.add(WORD_SPLITTER);
		Log.d("REACH","6");
		/************* update UI ********************/
		//decide which character to show
		this.updateData(detectResult);
		
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
				for (int i = 0; i < labels.size(); i++) {
					Button myButton = new Button(getApplicationContext());
					hintButtonList.add(myButton);
					myButton.setText(labels.get(i));
					myButton.setId(100 + i);
					myButton.setTextColor(Color.BLACK);
					myButton.setWidth(30);
					myButton.setHeight(30);
					myButton.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							String correctionString = ((Button)v).getText().toString();
							if(charas.contains(" ")){
								String strs[] = charas.split(" ");
								strs[strs.length-1] = correctionString;
								StringBuffer sb = new StringBuffer();
								for(String s:strs){
									sb.append(s);
									sb.append(" ");
								}
								charas=sb.toString();
							}
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
			
			
				Log.d("REACH","7");
				long startTime = System.currentTimeMillis();
				
			/********correction buttons*************/		
			// rm all existing hint buttons on screen
			if (null == CorrectionButtonList) {
				CorrectionButtonList = new LinkedList<Button>();
			} else {
				for (Button mButton : CorrectionButtonList) {
					rl.removeView(mButton);
				}
				CorrectionButtonList.clear();
			}
			if(results != null){
			// create new hint buttons
				int length;
				if((results.length) > 9 ){
					length=9;
				}else{
					length=results.length;
				}
				
				for (int i = 0; i < length; i++) {
						Button myButton = new Button(getApplicationContext());
						CorrectionButtonList.add(myButton);
						myButton.setText(results[i]);
						myButton.setId(100 + i);
						myButton.setTextColor(Color.BLACK);
						myButton.setWidth(30);
						myButton.setHeight(30);
						
						RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(
								LayoutParams.WRAP_CONTENT,
								LayoutParams.WRAP_CONTENT);
						rp.addRule(RelativeLayout.ABOVE, R.id.inputChar);
						if (0 != i) {
							rp.addRule(RelativeLayout.RIGHT_OF,
									myButton.getId() - 1);
						}
						myButton.setLayoutParams(rp);
						rl.addView(myButton);
					}
				}
			Log.d("REACH","8");
			long endTime   = System.currentTimeMillis();
			long totalTime = endTime - startTime;
			Log.d("UIUpdateTime",String.valueOf(totalTime));
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
	/*
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
*/


	/***
	 * This fuction is called when user click backspace button on screen It
	 * remove one character from the displayed characters if it is user click
	 * backspace input and then click backspace, we regard this as
	 */
	public void onClickButtonBackSpace(View view) {
		dictionaryControllor.deleteLastChar();
		int len = charas.length();
		//deal with KNN		
		//deal with UI
		if(len > 0)
			charas = charas.substring(0, len-1);
			mKNN.removeLatestInput();
		int len1 = showDetectResult.size();
		if(len1 > 0)
			showDetectResult.remove(len1-1);
		updateUI();
		stat.addInput(3, "backspace");
	}

	/***
	 * This fuction is called when user click Save button on screen
	 * It saves current KNN training set to file
	 */
//	public void onClickSave(View view) {
//		LayoutInflater li = LayoutInflater.from(context);
//		View promptsView = li.inflate(R.layout.dialog_savingknn, null);
//
//		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
//				context);
//
//		// set prompts.xml to alertdialog builder
//		alertDialogBuilder.setView(promptsView);
//
//		final EditText userInput = (EditText) promptsView
//				.findViewById(R.id.editTextFileName);
//
//		// set dialog message
//		alertDialogBuilder
//			.setCancelable(false)
//			.setPositiveButton("OK",
//			  new DialogInterface.OnClickListener() {
//			    public void onClick(DialogInterface dialog,int id) {
//				// get user input and set it to result
//				// edit text
//			    	String fileName = userInput.getText().toString();
//					
//					String state = Environment.getExternalStorageState();
//				    if (!Environment.MEDIA_MOUNTED.equals(state)) {
//				    	Log.e("save KNN", "Directory not created");
//				    }
//				    File sdCard = Environment.getExternalStorageDirectory();
//				    File dir = new File (sdCard.getAbsolutePath() + "/UbiK");
//				    File file = new File(dir,   fileName);	    	
//				   
//				    
//				    
//				    boolean b = mKNN.save(file);
//				    String msg;
//				    if(b== true){
//				    	msg = "Save kNN file successfully! You can load it in future.";
//				    }else{
//				    	msg = "Something wrong happens when saving kNN file!";
//				    }
//				    new AlertDialog.Builder(context)
//				    .setTitle("Save kNN")
//				    .setMessage(msg)
//				    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
//				        public void onClick(DialogInterface dialog, int which) { 
//				            // continue with delete
//				        }
//				     })
//				     .show();
//			    }
//			  })
//			.setNegativeButton("Cancel",
//			  new DialogInterface.OnClickListener() {
//			    public void onClick(DialogInterface dialog,int id) {
//				dialog.cancel();
//			    }
//			  });
//		AlertDialog alertDialog = alertDialogBuilder.create();
//		 
//		// show it
//		alertDialog.show();		
//		
//	}

	
	
	/**
	 * finish testing, save logs to file
	 * @param view
	 */
	public void onClickButtonFinish(View view){
		
		stat.doLogs("PaperKeyboard");
		android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	/**
	 * when click button Dict
	 * @param view
	 */
	public void onClickDict(View view){
		this.dictStatus = !this.dictStatus;
		Button button = (Button) findViewById(R.id.button_Dict);
		if(dictStatus)
			button.setText("Use-Dict");
		else
			button.setText("Non-Dict");
			
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
			if(this.shift == 0){
				charas+= cap.transWhenCaps(detectResult);
				showDetectResult.add(cap.transWhenCaps(detectResult));
			}else {
				charas+= detectResult;
				showDetectResult.add(detectResult);
			}
		}else{
			if(this.shift == 1){
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
//		textInputRate.setText("input rate:" + String.valueOf(stat.inputRate)+ "ch/s") ;
		text.setText(charas);
		//text.setText(showDetectResult.toString());
		totalInputText.setText(String.valueOf(this.stat.totalInputTimes));
	//	totalAccuracyText.setText(String.valueOf(this.stat.totalAccuracy * 100) + "%");
		if(shift == 2){
			//ShiftButton.animate();
			ShiftButton.setChecked(true);
		}
		else ShiftButton.setChecked(false);
		if(caps){
			CapsButton.setChecked(true);
		}else CapsButton.setChecked(false);
		//totalAccuracyText.setText(String.valueOf(errorInputTimes));
		//debugKNN.setText(mKNN.getChars());
	//	recentAccuracyText.setText(String.valueOf(stat.recentAccuracy*100) + "%");
		
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
