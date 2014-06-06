package edu.wisc.perperkeyboard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import edu.wisc.jj.BasicKNN;
import edu.wisc.jj.SPUtil;


@SuppressLint("NewApi")
public class MainActivity extends Activity implements RecBufListener{
	/**********constant values****************/
	public static final String EXTRANAME = "edu.wisc.perperkeyboard.KNN";
	private static final String LTAG = "Kaichen Debug";

	//used to synchronize STROKE_CHUNKSIZE in test activity
	static final int STROKE_CHUNKSIZE = 2000;
	private static final int SAMPLING_RATE= 48000;
	private static final int CHANNEL_COUNT = 2;	
	private static int TRAINNUM = 5; //how many keystroke we need to get for each key when training

	public static BasicKNN mKNN;
	private enum InputStatus {
		AtoZ,//All,//AtoZ, NUM,LEFT, RIGHT, BOTTOM
	}
	
	private Context context = this;
	
	/****to track input stage*************/
	// expected chunk number in each stage
//	private final int[] ExpectedInputNum = { 26, 12, 4, 11, 5 };
//	private final int[] ExpectedInputNum = { 3, 1, 4, 11, 6 };
//	private final int[] ExpectedInputNum = {3};
	private final int[] ExpectedInputNum = {27};	

	private InputStatus inputstatus;
	private Set<InputStatus> elements;
	Iterator<InputStatus> it; 
	
	/*************UI********************/
	private static TextView text;
	private static Button mButton;
	private static EditText waveThre;
	private static EditText gyroThre;
	private Thread recordingThread;
	private RecBuffer mBuffer;
	//private static TextView debugKNN;
	/************audio collection***********/
//	private short[] strokeBuffer;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	private AudioBuffer strokeAudioBuffer;
	
	/***********training*****************/
	private ArrayList<ArrayList<String>> trainingItemName;
	private volatile int curTrainingItemIdx;
	//public int numToBeTrained;
	public boolean finishedTraining;
	private int TrainedNum = 0;  // for each key, how many key stroke we have collected
	
	/***********************gyro helper********************/
	public static GyroHelper mGyro;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/**********init UI****************/
		setContentView(R.layout.activity_main);
		text = (TextView) findViewById(R.id.text_showhint);
		mButton = (Button) findViewById(R.id.mButton);
		//debugKNN = (TextView) findViewById(R.id.text_debugKNN);
		waveThre = (EditText) findViewById(R.id.input_waveThreshold);
		gyroThre = (EditText) findViewById(R.id.input_gyroThreshold);
		waveThre.setText(String.valueOf(KeyStroke.THRESHOLD));
		
		waveThre.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		        //halt = true;
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		        	String value = v.getText().toString();
		        	KeyStroke.THRESHOLD = Integer.parseInt(value);	        	
		        }
		        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		        imm.toggleSoftInput(0, 0);
		        return handled;
		    }
		});
		waveThre.clearFocus();
		gyroThre.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        boolean handled = false;
		        //halt = true;
		        if (actionId == EditorInfo.IME_ACTION_SEND) {
		        	String value = v.getText().toString();
		        	mGyro.GYRO_DESK_THRESHOLD = Float.parseFloat(value);        	
		        }
		        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		        imm.toggleSoftInput(0, 0);
		        return handled;
		    }
		});
		gyroThre.clearFocus();
		
		/******init values*************/
		this.inStrokeMiddle = false;
		this.strokeSamplesLeft = 0;
		curTrainingItemIdx = 0;
		this.finishedTraining=false;		
		// iterator for input stage
		elements = EnumSet.allOf(InputStatus.class);
		it = elements.iterator();
		inputstatus = it.next();
		
		/****************audio buffer to store key strokes*****************/
		this.strokeAudioBuffer=new AudioBuffer(SAMPLING_RATE,CHANNEL_COUNT,STROKE_CHUNKSIZE);
		
		/*********create knn*************/
		mKNN = new BasicKNN();


		// add training item names
		trainingItemName = new ArrayList<ArrayList<String>>();
		addTrainingItem.addTrainingItems(trainingItemName);
		
		Log.d(LTAG,
				"training item names: "
						+ Arrays.toString(this.trainingItemName.toArray()));
		Log.d(LTAG, "main activity thread id : "
				+ Thread.currentThread().getId());
		
		//get gyro helper
		this.mGyro=new GyroHelper(getApplicationContext());
		gyroThre.setText(String.valueOf(mGyro.GYRO_DESK_THRESHOLD));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	@Override
	public void onStop(){
		Log.d(LTAG,"training activity stopped");
		if (this.recordingThread!=null){
			//make sure the recording thread is stopped
			recordingThread.interrupt();
			recordingThread=null;
		}
		super.onStop();
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
	 * full. It will do audio processing to get features. And also add these features to KNN
	 * 
	 * @param data
	 *            : audio data recorded. For stereo data, data at odd indexes
	 *            belongs to one channel, data at even indexes belong to another
	 * @throws FileNotFoundException
	 */
	@Override
	public void onRecBufFull(short[] data) {

//		Log.d(LTAG, "inside onRecBuf full");
		/*******************smooth the data******************/
		SPUtil.smooth(data);

		/**************** put recording to buffer ********************/
		this.strokeAudioBuffer.add(data);
		
		/*********************check whether gyro agrees that there is a key stroke *******************/
		long curTime=System.nanoTime();
		//first case: screen is being touched
		if (Math.abs(curTime-this.mGyro.lastTouchScreenTime) < mGyro.TOUCHSCREEN_TIME_INTERVAL){
			Log.d("onRecBufFull", "screen touch detected nearby");
			return;
		} 
		
		/*****************when gyro feels some shake on the desk,check audio hints******/
		//if there is such an audio data ready for processing
		if (this.strokeAudioBuffer.hasKeyStrokeData()){
			//check whether gyro agrees or not
			// this assumption holds, since we are using 2000 
			// data samples, (40ms) gyro will be updated around 4 times 
			if (Math.abs(curTime-this.mGyro.lastTouchDeskTime) >= mGyro.DESK_TIME_INTERVAL){ 
				Log.d("onRecBufFull", "no desk vibration feeled. not valid audio data. lastTouchDesktime: "+this.mGyro.lastTouchDeskTime + " .current time: "+curTime);
				this.strokeAudioBuffer.clearValidIdx();
				return;
			}
			Log.d("onRecBufFull", "audio has stroke");								
			this.runAudioProcessing(this.strokeAudioBuffer.getKeyStrokeAudioForFeature());
		} else {
			//no audio data ready yet
			// 2 cases:
			// 1. detected a stroke, waiting for more audio
			// 2. no stroke detected yet 
			if (this.strokeAudioBuffer.needMoreAudio()){
//				Log.d("onRecBufFull", "need more audio");
				//do nothing when there's already a key stroke in place
				//we are just waiting for more data
				return;
			} else {
				int startIdx = KeyStroke.detectStroke_threshold(data);
				if (-1 == startIdx) { // when there is no stroke
					return;
				} else {
					Log.d("onRecBufFull", "find a threadhold -- valid");																
					//detect a new key stroke
					//revert the startIdx back, so that we get info before the strong peak
					this.strokeAudioBuffer.setValidIdx(startIdx-200, data.length);
				}
			}
		}
	}
		
/////////////////////////////////////////////////////////////////////////////////////////////////	
///		Log.d(LTAG, "inside onRecBuf full");
//		/*********************check whether gyro agrees that there is a key stroke *******************/
//		long curTime=System.nanoTime();
//		//first case: screen is being touched
//		
//		if (Math.abs(curTime-this.mGyro.lastTouchScreenTime) < mGyro.TOUCHSCREEN_TIME_INTERVAL){
//			Log.d("onRecBufFull", "screen touch detected nearby");
//			return;
//		//2nd case: there is indeed some vibrations on the desk			
//		} else if (Math.abs(curTime-this.mGyro.lastTouchDeskTime) >= mGyro.DESK_TIME_INTERVAL){ 
//			Log.d("onRecBufFull", "no desk vibration feeled. not valid audio data. lastTouchDesktime: "+this.mGyro.lastTouchDeskTime + " .current time: "+curTime);
//			return;
//		}
//		
//		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
//			int startIdx = KeyStroke.detectStroke_threshold(data);
//			if (-1 == startIdx) { // when there is no stroke
//				return;
//			} else { // there is an stroke
//				// this whole stroke is inside current buffer
//				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
//					Log.d(LTAG,
//							"key stroke, data length > chuncksize, data length: "
//									+ data.length);
//					this.inStrokeMiddle = false;
//					this.strokeSamplesLeft = 0;
//					this.strokeBuffer = Arrays.copyOfRange(data, startIdx,
//							startIdx + STROKE_CHUNKSIZE * 2);
//					this.runAudioProcessing();
//				} else { // there are some samples left in the next buffer
//					this.inStrokeMiddle = true;
//					this.strokeSamplesLeft = STROKE_CHUNKSIZE * 2
//							- (data.length - startIdx);
//					this.strokeBuffer = new short[STROKE_CHUNKSIZE * 2];
//					System.arraycopy(data, startIdx, strokeBuffer, 0,
//							data.length - startIdx);
//				}
//			}
//		} else { // if in the middle of a stroke
//			if (data.length >= strokeSamplesLeft) {
//				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
//						- 1 - strokeSamplesLeft, strokeSamplesLeft);
//				this.inStrokeMiddle = false;
//				this.strokeSamplesLeft = 0;
//				this.strokeBuffer= Arrays.copyOf(this.strokeBuffer,
//						STROKE_CHUNKSIZE * 2);
//				// get the audio features from this stroke and add it to the
//				// training set, do it in background
//				this.runAudioProcessing();				
//			} else { // if the length is smaller than the needed sample left
//				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
//						- 1 - strokeSamplesLeft, data.length);
//				this.inStrokeMiddle = true;
//				this.strokeSamplesLeft = this.strokeSamplesLeft - data.length;
//			}
//		}
/////////////////////////////////////////////////////////////////////////////////////////////////	

	/***
	 * this function is called when button is click
	 * 
	 * @param view
	 */
	public void onClickButton(View view) {
		if(this.finishedTraining){
			//KNN knn = new KNN();
			Intent intent = new Intent(this, TestingActivity.class);
			//intent.putExtra("SampleObject", testKNN);
//			mKNN.test = 10;
		    startActivity(intent);
		} else {
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					text.setText(inputstatus.toString() + "is recording"+ "trainning:"+"\n" + trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx));
					Toast.makeText(getApplicationContext(),
							"Please Wait Until This disappear", Toast.LENGTH_SHORT)
							.show();
					Log.d(LTAG, "onClickButton starting another recording thread");
				}
			});
			if (recordingThread == null) {				
				// Init RecBuffer and thread
				mBuffer = new RecBuffer();
				recordingThread = new Thread(mBuffer);
				// register myself to RecBuffer to let it know I'm listening to him
				this.register(mBuffer);
				recordingThread.start();
			} else {
				//recordingThread.interrupt();
			//	recordingThread = null;
			}
		}
	}
	
	public void onClickClear(View view){
			     //remove latest input
	     if(curTrainingItemIdx == 0 && TrainedNum == 0){ //means we just got to new input stage
	       //find previous stage
	       Set<InputStatus> tempelements =  EnumSet.allOf(InputStatus.class);;
	       Iterator<InputStatus> newit = elements.iterator();
	       Iterator<InputStatus> previousIt = null;
	       while(newit.hasNext()){
	         if (newit.hashCode() == it.hashCode() && previousIt != null)
	         {
	           it = previousIt;
	           curTrainingItemIdx = ExpectedInputNum[inputstatus.ordinal()]-1;
	           TrainedNum = TRAINNUM-1;
	           mKNN.removeLatestInput();
	           break;
	         }
	         newit.next();
	       }
	    }else if(TrainedNum == 0){ // means we just got to new input key, but not the first key of input stage
	      curTrainingItemIdx --;
	       TrainedNum = TRAINNUM -1;
	      mKNN.removeLatestInput();
	     }else{
	       TrainedNum --;
	       mKNN.removeLatestInput();
	     }
	     //show new info
	     text.setText(inputstatus.toString()  + "is recording. " + "\n"
	         + "current training: "
	         + trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx)+"\n"
	         + String.valueOf(TRAINNUM - TrainedNum) + "left");
	   //  debugKNN.setText(mKNN.getChars());	
	
	    /*	     
		/////////////////Each Key Several Times////////////////////
		//remove latest input
		if(curTrainingItemIdx == 0){ //means we just got to new input stage
			//find previous stage
			Set<InputStatus> elements =  EnumSet.allOf(InputStatus.class);
			Iterator<InputStatus> firstEle = elements.iterator();
			InputStatus fistStatus = firstEle.next();
			if(inputstatus.equals(fistStatus)){
				if(TrainedNum!=0){
					Iterator<InputStatus> newit = elements.iterator();
					//TODO this code might have bug
					while(newit.hasNext()){
						inputstatus = newit.next();
					}
					it = newit;
					int len = ExpectedInputNum.length -1;
					curTrainingItemIdx = ExpectedInputNum[len] -1;
					TrainedNum --;
					mKNN.removeLatestInput();
				}
			}else{
				Iterator<InputStatus> newit = elements.iterator();
				InputStatus previousstatus = newit.next();
				//find previous stage
				while(newit.hasNext() && previousstatus.ordinal() < inputstatus.ordinal()-1)
				{
					previousstatus = newit.next();
				}
				it = newit;
				inputstatus = previousstatus;
				curTrainingItemIdx = ExpectedInputNum[inputstatus.ordinal()] -1;
				mKNN.removeLatestInput();
			}
			
			
		}else{ // means we just got to new input key, but not the first key of input stage
			curTrainingItemIdx --;
			mKNN.removeLatestInput();
		}
		//show new info
		text.setText(inputstatus.toString()  + "is recording. " + "\n"
				+ "current training: "
				+ trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx)+"\n"
				+ String.valueOf(TRAINNUM - TrainedNum) + "left");
		debugKNN.setText(mKNN.getChars());
	*/
	}
	
	public void onClickRestart(View view){
		//clear KNN
		mKNN.clear();
		//clear iterator and inputstatus
		elements = EnumSet.allOf(InputStatus.class);
		it = elements.iterator();
		inputstatus= it.next();
		//clear trained number
		TrainedNum = 0;
		curTrainingItemIdx = 0;
		//show new info
		text.setText(inputstatus.toString()  + "is recording. " + "\n"
				+ "current training: "
				+ trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx)+"\n"
				+ String.valueOf(TRAINNUM - TrainedNum) + "left");
	//	debugKNN.setText(mKNN.getChars());
		if(this.finishedTraining){
			Toast.makeText(getApplicationContext(),
					"Please Wait Until This disappear", Toast.LENGTH_SHORT)
					.show();
			recordingThread.start();
			this.finishedTraining = false;
		}
	}

	
	/**
	 * This is a function to load mKNN from file
	 * @param view
	 */
//	public void onClickLoad(View view){
//		/********UI for load kNN file*********/
//		setContentView(R.layout.dialog_loadknn);
//		
//		
//		setContentView(R.layout.activity_main);
//		
//		LayoutInflater li = LayoutInflater.from(context);
//		View promptsView = li.inflate(R.layout.dialog_loadknn, null);
//
//		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
//				context);
//
//		// set prompts.xml to alertdialog builder
//		alertDialogBuilder.setView(promptsView);
//		List<String> fileList = new ArrayList<String>();
//		   
//		String state = Environment.getExternalStorageState();
//	    if (!Environment.MEDIA_MOUNTED.equals(state)) {
//	    	Log.e("save KNN", "Directory not created");
//	    }
//	    File sdCard = Environment.getExternalStorageDirectory();
//	    File dir = new File (sdCard.getAbsolutePath() + "/UbiK");
//	  
//	     File[] files = dir.listFiles();
//	     fileList.clear();
//	     for (File file : files){
//	      fileList.add(file.getPath());  
//	     }
//	     
//	    
//		ListView listview = (ListView) promptsView.findViewById(R.id.lv);
//		ArrayAdapter<String> directoryList = new ArrayAdapter<String>(this,
//			       android.R.layout.simple_list_item_1, fileList);
//		listview.setAdapter(directoryList); 	
//		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//
//		      @Override
//		      public void onItemClick(AdapterView<?> parent, final View view,
//		          int position, long id) {
//		        final String item = (String) parent.getItemAtPosition(position);
//		        view.animate().setDuration(2000).alpha(0)
//		            .withEndAction(new Runnable() {
//		              @Override
//		              public void run() {
//		                view.setAlpha(1);
//		                File file = new File (item);
//		                mKNN.load(file);
//		                Intent intent = new Intent(context, TestingActivity.class);
//		    		    startActivity(intent);
//		              }
//		            });
//		      }
//
//		});
//		// set dialog message
//		alertDialogBuilder
//			.setCancelable(false)
//			.setPositiveButton("OK",
//			  new DialogInterface.OnClickListener() {
//			    public void onClick(DialogInterface dialog,int id) {
//				// get user input and set it to result
//				// edit text
//			    	dialog.cancel();
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
//		
//	}
//	
	
	/**
	 * audio processing. extract features from audio. Add features to KNN.
	 */
	public void runAudioProcessing(short[] audioStroke) {
		// get the audio features from this stroke and add it
		// to the training set, do it in background
//		short[] audioStroke = this.strokeBuffer;
		// separate left and right channel
		short[][] audioStrokeData = KeyStroke.seperateChannels(audioStroke);
//		this.strokeBuffer=null;
		
		/******************log audio****************************/
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
		Date date = new Date();
		DecimalFormat df = new DecimalFormat("0.0000");
		
		String Name = "/sdcard/PaperKeyboardLog/"+"audio";//+ dateFormat.format(date);
		File mFile_log = new File(Name);
		FileOutputStream outputStream_log = null;
		try {
			// choose to append
			if(!mFile_log.exists()){
				mFile_log.createNewFile();
			}
			outputStream_log = new FileOutputStream(mFile_log, false);
			OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
			String header = "paper keyboard test result: \n"
					+ "file created time: " + dateFormat.format(date) + "\n";
			osw.write(header);
			String form = "totalAccuracy                 recentAccuracy\n";
			osw.write(form);
			for (int i = 0; i < audioStrokeData[0].length; i++) {
				osw.write(String.valueOf(audioStrokeData[0][i])+"\n");	
			}
			osw.flush();
			osw.close();
			osw = null;
		} catch (FileNotFoundException e) {
			// System.out.println("the directory doesn't exist!");
			//return false;
		} catch (IOException e) {
			// System.out.println("IOException occurs");
			//return false;
		}

		// get features
		double[] features = SPUtil.getAudioFeatures(audioStrokeData);

		/******************log features****************************/
		
		Name = "/sdcard/PaperKeyboardLog/"+"feature";//+ dateFormat.format(date);
		mFile_log = new File(Name);
		outputStream_log = null;
		try {
			// choose to append
			if(!mFile_log.exists()){
				mFile_log.createNewFile();
			}
			outputStream_log = new FileOutputStream(mFile_log, false);
			OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
			String header = "paper keyboard test result: \n"
					+ "file created time: " + dateFormat.format(date) + "\n";
			osw.write(header);
			String form = "totalAccuracy                 recentAccuracy\n";
			osw.write(form);
			for (int i = 0; i < features.length; i++) {
				osw.write(String.valueOf(features[i])+"\n");	
			}
			osw.flush();
			osw.close();
			osw = null;
		} catch (FileNotFoundException e) {
			// System.out.println("the directory doesn't exist!");
			//return false;
		} catch (IOException e) {
			// System.out.println("IOException occurs");
			//return false;
		}
		
		
		
		
		Log.d(LTAG, " adding features for item " + trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx));
		mKNN.addTrainingItem(
				trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx),
				features);

		
		//training order each key several times
        TrainedNum ++;
        if(TrainedNum == TRAINNUM)
        {
                curTrainingItemIdx++;
                TrainedNum = 0;
        }
        
        if (this.ExpectedInputNum[inputstatus.ordinal()] == this.curTrainingItemIdx){
                if(it.hasNext())
                {
                        inputstatus = it.next();
                        Log.d(LTAG, "change to next character. next char: "+inputstatus);                                
                        curTrainingItemIdx  = 0;
                }
                else{
                        this.finishedTraining=true;
                        //kill recording thread (myself)
                        recordingThread.interrupt();
                }
        } 
		//training order alphabetical
		/*
		curTrainingItemIdx++;		
		if (this.ExpectedInputNum[inputstatus.ordinal()] == this.curTrainingItemIdx){
			if(it.hasNext())
			{
				inputstatus = it.next();
				Log.d(LTAG, "change to next character. next char: "+inputstatus);				
				curTrainingItemIdx  = 0;
			}
			else{
				TrainedNum ++;
				if(TrainedNum == TRAINNUM)
				{
					inputstatus = it.next();
					Log.d(LTAG, "change to next character. next char: "+inputstatus);				
					//curTrainingItemIdx  = 0;
					TrainedNum = 0;
		
				}
				else{
						this.finishedTraining=true;
						//kill recording thread (myself)
						recordingThread.interrupt();
				}
			}
		} */
		//update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(!finishedTraining)
				{
					text.setText(inputstatus.toString()  + "is recording. " + "\n"
				+ "current training: "
				+ trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx)+"\n"
				+ String.valueOf(TRAINNUM - TrainedNum) + "left");
			//		debugKNN.setText(mKNN.getChars());	
				}
				else {
					text.setText("Training finished, click to start testing");
			//		debugKNN.setText(mKNN.getChars());
					mButton.setText("Click to Test");
				}
			}
		});
	}

	
	/***************************start and stop gyro helper ****************************/
	
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

/**
 * A class to add trainning item to the ArrayList
 * Contains only on function
 * @author kevin
 */
class addTrainingItem {
	public static void addTrainingItems(ArrayList<ArrayList<String>> trainingItemName)
	{
		ArrayList<String> AtoZArray = new ArrayList<String>();
		//TODO This is only used for debug
	//	AtoZArray.add("LShift");
	//	AtoZArray.add("Caps");
		// add characters into training item
		for (int idx = 0; idx < 26; idx++)
			AtoZArray.add(String.valueOf((char)('a' + idx)));
		AtoZArray.add(" ");
		//AtoZArray.add("Lshift");
		trainingItemName.add(AtoZArray);
		ArrayList<String> NumArray = new ArrayList<String>();
		// add numbers into training item
		for(int idx  = 0; idx < 9; idx++)
			NumArray.add(String.valueOf((char)('1'+idx)));
		NumArray.add(String.valueOf('0'));
		NumArray.add(String.valueOf('-'));
		NumArray.add(String.valueOf('='));
		trainingItemName.add(NumArray);
		
		ArrayList<String> LeftArray = new ArrayList<String>();
		// add left into training item
		LeftArray.add("`");
		LeftArray.add("Tab");
		LeftArray.add("Caps");
		LeftArray.add("LShift");
		trainingItemName.add(LeftArray);
		
		
		ArrayList<String> RightArray = new ArrayList<String>();
		// add right into training item
		RightArray.add("BackSpace");
		RightArray.add("]");
		RightArray.add("[");
		RightArray.add("Enter");
		RightArray.add("\\");
		RightArray.add("'");
		RightArray.add(";");
		RightArray.add("RShift");
		RightArray.add("/");
		RightArray.add(".");
		RightArray.add(",");
		trainingItemName.add(RightArray);
		
		ArrayList<String> BottomArray = new ArrayList<String>();
		// add numbers into training item
		BottomArray.add("L Ctrl");
		//BottomArray.add("Windows");
		BottomArray.add("L Alt");
		BottomArray.add(" ");
		BottomArray.add("R Alt");
		BottomArray.add("R Ctrl");
		trainingItemName.add(BottomArray);		
	}
	
}
