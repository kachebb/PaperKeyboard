package edu.wisc.perperkeyboard;

//import edu.wisc.paperkeyboard.R;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

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

public class MainActivity extends Activity implements RecBufListener {

	private static final String LTAG = "Kaichen Debug";
	private static final int THRESHOLD = 200;
	private static final int CHUNKSIZE= 2000;
	private static final int SAMPLERATE = 48000;
	private static final int DIST = 20000;
	private enum InputStatus{AtoZ, NUM, LEFT, RIGHT, BOTTOM}
	//expected chunk number in each stage
	private final int[] ExpectedChunkNum = {26, 12, 4, 11, 7};
	
	private InputStatus inputstatus;
	
	private boolean finish = false;
	private boolean AsycTaskRunning = false; 
	
	private static TextView text;
	private static Button mButton;
	private Thread recordingThread;
	private static int cnt;
	private RecBuffer mBuffer ;
	private Set<InputStatus> elements;
	Iterator<InputStatus> it; 
	
	private ShortBuffer soundSamples;
	private int bufferSize = 0;
	private short[][] keyStrokesR;
	private short[][] keyStrokesL;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		text = (TextView) findViewById(R.id.text_showhint);
		mButton = (Button) findViewById(R.id.mButton);
		
		//Init RecBuffer and thread
		mBuffer = new RecBuffer();
		recordingThread = new Thread(mBuffer);
		// register myself to RecBuffer to let it know I'm listening to him
		this.register(mBuffer);
		
		soundSamples = ShortBuffer.allocate(SAMPLERATE*26*2);
		//TODO here we suppose sound samples are at most 26s, which make the app not robust....
		
		keyStrokesR = new short[50][CHUNKSIZE];
		keyStrokesL = new short[50][CHUNKSIZE];
		//TODO here we suppose sound samples are at most 50 key stroke, which make the app not robust....
		
		
		//iterator for input stage
		elements = EnumSet.allOf(InputStatus.class);
		it = elements.iterator();
		inputstatus = it.next();
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
	 * This method will be called every time the buffer of
	 * recording thread is full
	 * 
	 * @param data      : audio data recorded. For stereo data, data at odd indexes belongs to one channel, data at even indexes belong to another
	 * @throws FileNotFoundException
	 */
	
	@Override
	public void onRecBufFull(short[] data) {
		//cnt++;
		Log.d(LTAG, "I'm called at time: " + System.nanoTime() + "cnt number : " + cnt);
		soundSamples.put(data);
		bufferSize += data.length;
	}
	
	
	private class StartRecTask extends AsyncTask<Void, Void, Void> {
		/**
		 * this method is done before calling doInBackground. This method
		 * belongs to the UI thread Therefore, we can update UI components on
		 * this thread
		 */
		@Override
		protected void onPreExecute() {
			text.setText(inputstatus.toString()+ "\n"+"is recording");
			keyStrokesR = new short[26][CHUNKSIZE];
			keyStrokesL = new short[26][CHUNKSIZE];
			Toast.makeText(getApplicationContext(), "Please Wait Until This disappear",
					Toast.LENGTH_SHORT).show();
		}

		/**
		 * this method will be done in background. No access to UI thread
		 * components. cannot directly update anything on the phone screen
		 * directly
		 */
		@Override
		protected Void doInBackground(Void...params) {
			recordingThread = new Thread(mBuffer);
			recordingThread.start();
			try {
				// wait the recording thread in background ( will not block UI thread)
				recordingThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return null;
			
		}

		/**
		 * this method is done after doInBackground. This method belongs to the
		 * UI thread Therefore, we can update UI components on this thread
		 */
		@Override
		protected void onPostExecute(Void params) {
			//set inputs status to next and test if finished
			int num = chunkData_threshold();
			//TODO add trainning code here
			
			soundSamples = ShortBuffer.allocate(SAMPLERATE*26*2);
			bufferSize = 0;
			//TODO actually here I need to clear the buffer
			if(num != ExpectedChunkNum[inputstatus.ordinal()]){
				text.setText("expect " + String.valueOf(ExpectedChunkNum[inputstatus.ordinal()])+ "input but we got" + String.valueOf(num)+"\n Please input"+ inputstatus.toString() + " again:");
				finish = false;
			} 
			else if(it.hasNext()){
				InputStatus oldstatus = inputstatus;
				inputstatus = it.next();
				text.setText(oldstatus.toString() +" finished"+"\n" + "Click to input"+"\n" +inputstatus.toString());
			}else{
				text.setText("Finish Input Trainning Data");
			}
		}
	}

	/***
	 * this function is called when button is click
	 * @param view
	 */
	public void onClickButton(View view){		
		if(AsycTaskRunning){
			AsycTaskRunning = false;
			recordingThread.interrupt();
			if(!it.hasNext()){
				finish = true;
			}
		}
		else{
			if(finish){
				finish();
				System.exit(0);
			}
			new StartRecTask().execute();
			//TODO do we need to delete this after use?
			AsycTaskRunning =true;
			
		}
	}

}
