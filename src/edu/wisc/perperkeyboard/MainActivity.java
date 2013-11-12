package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import edu.wisc.jj.KNN;
import edu.wisc.jj.SPUtil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;


@SuppressLint("NewApi")
public class MainActivity extends Activity implements RecBufListener{
	public static final String EXTRANAME = "edu.wisc.perperkeyboard.KNN";
	private static final String LTAG = "Kaichen Debug";
	private static final int STROKE_CHUNKSIZE = 2000;
	private static final int TRAINNUM = 5; //how many keystroke we need to get for each key when training 
	public static KNN mKNN;
	private enum InputStatus {
		AtoZ//, NUM, LEFT, RIGtextHT, BOTTOM
	}
	// expected chunk number in each stage
	private final int[] ExpectedInputNum = { 4, 12, 4, 11, 6 };
	private InputStatus inputstatus;
	private static TextView text;
	private static Button mButton;
	private Thread recordingThread;
	private RecBuffer mBuffer;
	private Set<InputStatus> elements;
	Iterator<InputStatus> it; 

	
	private short[] strokeBuffer;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;

	// training item
	private ArrayList<ArrayList<String>> trainingItemName;
	private volatile int curTrainingItemIdx;
	public int numToBeTrained;
	public boolean finishedTraining;
	private int TrainedNum = 0;  // for each key, how many key stroke we have collected

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		text = (TextView) findViewById(R.id.text_showhint);
		mButton = (Button) findViewById(R.id.mButton);
		
		this.inStrokeMiddle = false;
		this.strokeSamplesLeft = 0;

		// iterator for input stage
		elements = EnumSet.allOf(InputStatus.class);
		it = elements.iterator();
		inputstatus = it.next();

		// create knn
		mKNN = new KNN();
		// add training item names
		trainingItemName = new ArrayList<ArrayList<String>>();
		addTrainingItem.addTrainingItems(trainingItemName);
		
		curTrainingItemIdx = 0;
		TrainedNum = 0; 
		this.finishedTraining=false;
		Log.d(LTAG,
				"training item names: "
						+ Arrays.toString(this.trainingItemName.toArray()));
		Log.d(LTAG, "main activity thread id : "
				+ Thread.currentThread().getId());

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
		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
			int startIdx = KeyStroke.detectStroke_threshold(data);
			if (-1 == startIdx) { // when there is no stroke
				return;
			} else { // there is an stroke
				// this whole stroke is inside current buffer
				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
//					Log.d(LTAG,
//							"key stroke, data length > chuncksize, data length: "
//									+ data.length);
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
//					Log.d(LTAG,
//							"key stroke, data length < chuncksize, stroke start idx: "
//									+ startIdx + " stroke data length: "
//									+ String.valueOf(data.length - startIdx)
//									+ " stroke samples left "
//									+ this.strokeSamplesLeft);
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
//				Log.d(LTAG, "key stroke, data length >= samples left "
//						+ " stroke data length: " + String.valueOf(data.length)
//						+ " stroke samples left " + this.strokeSamplesLeft);
			} else { // if the length is smaller than the needed sample left
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2
						- 1 - strokeSamplesLeft, data.length);
				this.inStrokeMiddle = true;
				this.strokeSamplesLeft = this.strokeSamplesLeft - data.length;
//				Log.d(LTAG,
//						"key stroke, data length < samples left size " + " stroke data length: "
//								+ String.valueOf(data.length)
//								+ " stroke samples left "
//								+ this.strokeSamplesLeft);
			}
		}
	}

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
			mKNN.test = 10;
		    startActivity(intent);
		} else {
			if (recordingThread == null) {
				text.setText(inputstatus.toString() + "is recording"+ "trainning:"+"\n" + trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx));
				Toast.makeText(getApplicationContext(),
						"Please Wait Until This disappear", Toast.LENGTH_SHORT)
						.show();
				Log.d(LTAG, "onClickButton starting another recording thread");
				// Init RecBuffer and thread
				mBuffer = new RecBuffer();
				recordingThread = new Thread(mBuffer);
				// register myself to RecBuffer to let it know I'm listening to him
				this.register(mBuffer);
				recordingThread.start();
			} else {
				recordingThread.interrupt();
				recordingThread = null;
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
		Log.d(LTAG, " adding features for item " + trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx));
		mKNN.addTrainingItem(
				trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx),
				features);
		TrainedNum ++;
		if(TrainedNum == TRAINNUM)
		{
			curTrainingItemIdx++;
			TrainedNum = 0;
		}
		//stop the recording thread. Right now only support training characters (A-Z)
		if (this.ExpectedInputNum[inputstatus.ordinal()] == this.curTrainingItemIdx){
			if(it.hasNext())
			{
				Log.d(LTAG, "throwed interrupt in runAudioProcessing");
				inputstatus = it.next();
				curTrainingItemIdx  = 0;
				return;
			}
			else{
				this.finishedTraining=true;
				//kill recording thread (myself)
				recordingThread.interrupt();
			}
		} 
		//update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(!finishedTraining)
					text.setText(inputstatus.toString()  + "is recording. " + "\n"
				+ "current training: "
				+ trainingItemName.get(inputstatus.ordinal()).get(curTrainingItemIdx)+"\n"
				+ String.valueOf(TRAINNUM - TrainedNum) + "left");
				else {
					text.setText("Training finished, click to start testing");
					mButton.setText("Click to Test");
				}
				
			}
		});
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
		// add characters into training item
		for (int idx = 0; idx < 26; idx++)
			AtoZArray.add(String.valueOf((char)('A' + idx)));
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
		// add numbers into training item
		LeftArray.add("~");
		LeftArray.add("Tab");
		LeftArray.add("Caps");
		LeftArray.add("L Shift");
		trainingItemName.add(LeftArray);
		
		
		ArrayList<String> RightArray = new ArrayList<String>();
		// add numbers into training item
		RightArray.add("BackSpace");
		RightArray.add("\\");
		RightArray.add("]");
		RightArray.add("[");
		RightArray.add("Enter");
		RightArray.add("'");
		RightArray.add(";");
		RightArray.add("R Shift");
		RightArray.add("/");
		RightArray.add(".");
		RightArray.add(",");
		trainingItemName.add(RightArray);
		
		ArrayList<String> BottomArray = new ArrayList<String>();
		// add numbers into training item
		BottomArray.add("L Ctrl");
		BottomArray.add("Windows");
		BottomArray.add("L Alt");
		BottomArray.add("Space");
		BottomArray.add("R Alt");
		BottomArray.add("R Ctrl");
		trainingItemName.add(BottomArray);
		
		
	}
}
