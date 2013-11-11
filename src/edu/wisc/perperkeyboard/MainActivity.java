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
	public static KNN mKNN;
	private enum InputStatus {
		AtoZ, NUM, LEFT, RIGHT, BOTTOM
	}
	// expected chunk number in each stage
	private final int[] ExpectedChunkNum = { 26, 12, 4, 11, 7 };
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
	private ArrayList<String> trainingItemName;
	private volatile int curTrainingItemIdx;
	public int numToBeTrained;
	public boolean finishedTraining;

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
		trainingItemName = new ArrayList<String>();
		for (int idx = 0; idx < 26; idx++)
			trainingItemName.add(String.valueOf((char)('A' + idx)));
		curTrainingItemIdx = 0;
		this.numToBeTrained=2;
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
		    startActivity(intent);
		} else {
			if (recordingThread == null) {
				text.setText(inputstatus.toString() + "\n" + "is recording");
				Toast.makeText(getApplicationContext(),
						"Please Wait Until This disappear", Toast.LENGTH_SHORT)
						.show();
				// Init RecBuffer and thread
				mBuffer = new RecBuffer();
				recordingThread = new Thread(mBuffer);
				// register myself to RecBuffer to let it know I'm listening to him
				this.register(mBuffer);
				recordingThread.start();
			} else {
				recordingThread.interrupt();
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
		Log.d(LTAG, " adding features for item " + trainingItemName.get(curTrainingItemIdx));
		mKNN.addTrainingItem(
				trainingItemName.get(curTrainingItemIdx),
				features);
		curTrainingItemIdx++;
		//stop the recording thread. Right now only support training characters (A-Z)
		if (this.numToBeTrained == this.curTrainingItemIdx){
			Log.d(LTAG, "throwed interrupt in runAudioProcessing");
			this.finishedTraining=true;
			//kill recording thread (myself)
			recordingThread.interrupt();
			return;
		} 
		//update UI
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				text.setText(inputstatus.toString() + "\n" + "is recording. "
				+ "next training: "
				+ trainingItemName.get(curTrainingItemIdx));
			}
		});
	}
}
