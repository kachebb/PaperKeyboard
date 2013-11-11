package edu.wisc.perperkeyboard;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.wisc.jj.KNN;
import edu.wisc.jj.SPUtil;

import android.os.AsyncTask;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class MainActivity extends Activity implements RecBufListener {

	private static final String LTAG = "Kaichen Debug";
	private static final int THRESHOLD = 200;
	private static final int STROKE_CHUNKSIZE = 2000;
	private static final int SAMPLERATE = 48000;
	private static final int DIST = 20000;
	private KNN mKNN;

	private enum InputStatus {
		AtoZ, NUM, LEFT, RIGHT, BOTTOM
	}

	// expected chunk number in each stage
	private final int[] ExpectedChunkNum = { 26, 12, 4, 11, 7 };

	private InputStatus inputstatus;

	private boolean finish = false;
	private boolean AsycTaskRunning = false;

	private static TextView text;
	private static Button mButton;
	private Thread recordingThread;
	private static int cnt;
	private RecBuffer mBuffer;
	private Set<InputStatus> elements;
	Iterator<InputStatus> it;

	private short[] strokeBuffer;
	private boolean inStrokeMiddle;
	private int strokeSamplesLeft;
	
	//training item
	private static ArrayList<String> trainingItemName;
	private static volatile AtomicInteger curTrainingItemIdx;

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
		
		//create knn
		this.mKNN=new KNN();
		
		//add training item names
		trainingItemName=new ArrayList<String>();
		for (int idx=0;idx<26;idx++)
			trainingItemName.add(String.valueOf('A'+idx));
		curTrainingItemIdx=new AtomicInteger();
		curTrainingItemIdx.set(0);
		Log.d(LTAG, "training item names: " + Arrays.toString(this.trainingItemName.toArray()));
		Log.d(LTAG, "main activity thread id : " + Thread.currentThread().getId());		
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
	 * full
	 * 
	 * @param data
	 *            : audio data recorded. For stereo data, data at odd indexes
	 *            belongs to one channel, data at even indexes belong to another
	 * @throws FileNotFoundException
	 */
	@Override
	public void onRecBufFull(short[] data) {
		Log.d(LTAG, "I'm called at time: " + System.nanoTime()+ " thread id : " + Thread.currentThread().getId());
		if (!this.inStrokeMiddle) { // if not in the middle of a stroke
			int startIdx = KeyStroke.detectStroke_threshold(data);
			if (-1 == startIdx) { // when there is no stroke
				Log.d(LTAG, "no key stroke");
				return;
			} else { // there is an stroke
				//this whole stroke is inside current buffer
				if (data.length - startIdx >= STROKE_CHUNKSIZE * 2) {
					Log.d(LTAG, "key stroke, data length > chuncksize, data length: "+data.length);					
					this.inStrokeMiddle = false;
					this.strokeSamplesLeft = 0;
					short[] stroke=Arrays.copyOfRange(data, startIdx, startIdx+STROKE_CHUNKSIZE*2);
					// get the audio features from this stroke and add it
					// to the training set, do it in background
					AudioToFeatureAsync mGetFeatures= new AudioToFeatureAsync();
					mGetFeatures.execute(stroke);
				} else { // there are some samples left in the next buffer
					this.inStrokeMiddle = true;
					this.strokeSamplesLeft = STROKE_CHUNKSIZE * 2
							- (data.length - startIdx);
					this.strokeBuffer = new short[STROKE_CHUNKSIZE * 2];
					System.arraycopy(data, startIdx, strokeBuffer, 0,
							data.length - startIdx);
					Log.d(LTAG, "key stroke, data length < chuncksize, stroke start idx: "+startIdx + "stroke data length: "+String.valueOf(data.length-startIdx)+ "stroke samples left " + this.strokeSamplesLeft);															
				}
			}
		} else { // if in the middle of a stroke
			if (data.length>=strokeSamplesLeft) {
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2 - 1
						- strokeSamplesLeft, strokeSamplesLeft);
				this.inStrokeMiddle = false;
				this.strokeSamplesLeft = 0;
				short[] stroke=Arrays.copyOf(this.strokeBuffer, STROKE_CHUNKSIZE*2);
				// get the audio features from this stroke and add it to the
				// training set, do it in background
				AudioToFeatureAsync mGetFeatures= new AudioToFeatureAsync();
				mGetFeatures.execute(stroke);
				this.strokeBuffer = null;
				Log.d(LTAG, "key stroke, data length >= samples left "+ "stroke data length: "+String.valueOf(data.length)+ "stroke samples left " + this.strokeSamplesLeft);																			
			} else { //if the length is smaller than the needed sample left
				System.arraycopy(data, 0, strokeBuffer, STROKE_CHUNKSIZE * 2 - 1
						- strokeSamplesLeft, data.length);
				this.inStrokeMiddle = true;
				this.strokeSamplesLeft =this.strokeSamplesLeft-data.length;
				Log.d(LTAG, "key stroke, data length < samples left size, stroke start idx: "+ data.length + "stroke data length: "+String.valueOf(data.length)+ "stroke samples left " + this.strokeSamplesLeft);																			
			}
		}
	}

	/***
	 * this function is called when button is click
	 * 
	 * @param view
	 */
	public void onClickButton(View view) {
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

	/**
	 * do the audio feature extraction (fft) in the background. not to block UI thread
	 * @author jj
	 *
	 */
	private class AudioToFeatureAsync extends AsyncTask<short[], Void, Void> {
		@Override
		protected void onPreExecute() {
			text.setText(text.getText() + "\n" + "find a keyStroke!");
		}

		@Override
		protected Void doInBackground(short[]... params) {
			short[] audioStroke=params[1];
			//separate left and right channel
			short[][] audioStrokeData=KeyStroke.seperateChannels(audioStroke);
			//get features
			double[] features=SPUtil.getAudioFeatures(audioStrokeData);
			//use atomic in such case that several background processes are running at the same time
			mKNN.addTrainingItem(trainingItemName.get(curTrainingItemIdx.getAndIncrement()), features);
			return null;
		}

		@Override
		protected void onPostExecute(Void params) {
			text.setText(inputstatus.toString() + "\n" + "is recording. "+" training: "+ trainingItemName.get(curTrainingItemIdx.get()));
		}

	}

	// private class StartRecTask extends AsyncTask<Void, Void, Void> {
	// /**
	// * this method is done before calling doInBackground. This method
	// * belongs to the UI thread Therefore, we can update UI components on
	// * this thread
	// */
	// @Override
	// protected void onPreExecute() {
	// text.setText(inputstatus.toString()+ "\n"+"is recording");
	// keyStrokesR = new short[26][CHUNKSIZE];
	// keyStrokesL = new short[26][CHUNKSIZE];
	// Toast.makeText(getApplicationContext(),
	// "Please Wait Until This disappear",
	// Toast.LENGTH_SHORT).show();
	// }
	//
	// /**
	// * this method will be done in background. No access to UI thread
	// * components. cannot directly update anything on the phone screen
	// * directly
	// */
	// @Override
	// protected Void doInBackground(Void...params) {
	// recordingThread = new Thread(mBuffer);
	// recordingThread.start();
	// try {
	// // wait the recording thread in background ( will not block UI
	// thread)
	// recordingThread.join();
	// } catch (InterruptedException e) {
	// e.printStackTrace();
	// }
	// return null;
	//
	// }
	//
	// /**
	// * this method is done after doInBackground. This method belongs to
	// the
	// * UI thread Therefore, we can update UI components on this thread
	// */
	// @Override
	// protected void onPostExecute(Void params) {
	// //set inputs status to next and test if finished
	// int num = chunkData_threshold();
	// //TODO add trainning code here
	//
	// soundSamples = ShortBuffer.allocate(SAMPLERATE*26*2);
	// bufferSize = 0;
	// //TODO actually here I need to clear the buffer
	// if(num != ExpectedChunkNum[inputstatus.ordinal()]){
	// text.setText("expect " +
	// String.valueOf(ExpectedChunkNum[inputstatus.ordinal()])+
	// "input but we got" + String.valueOf(num)+"\n Please input"+
	// inputstatus.toString() + " again:");
	// finish = false;
	// }
	// else if(it.hasNext()){
	// InputStatus oldstatus = inputstatus;
	// inputstatus = it.next();
	// text.setText(oldstatus.toString() +" finished"+"\n" +
	// "Click to input"+"\n" +inputstatus.toString());
	// }else{
	// text.setText("Finish Input Trainning Data");
	// }
	// }
	// }

	// if(AsycTaskRunning){
	// AsycTaskRunning = false;
	// recordingThread.interrupt();
	// if(!it.hasNext()){
	// finish = true;
	// }
	// }
	// else{
	// if(finish){
	// finish();
	// System.exit(0);
	// }
	// new StartRecTask().execute();
	// //TODO do we need to delete this after use?
	// AsycTaskRunning =true;
	// }
}
