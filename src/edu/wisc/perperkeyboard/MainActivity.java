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
	private static final int THRESHOLD = 1500;
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
	private short[][] keyStrokesR;
	private short[][] keyStrokesL;
	
	//two parameter for detectStroke and chunkdata_energy
	private int windowSize = 50;
	private int detectSize = 10;
	
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
	
	/***
	 * detect Stroke in a data array
	 * Method: move a small window in the array, if the energy last several sample in window is much larger than the first several.
	 * Then we think keyStroke starts from current window.
	 * @param idx : test the array from idx to the end
	 * @param data : data array
	 * @return : return the index of keystroke in the data array
	 */
	

	private int detectStroke(int idx, short[] data){
		int TIME = 3;
		int startIdx = 0;
		int endIdx = windowSize - detectSize;
		int i,j;
		int energy1, energy2;
		int ret = -1;
		for(i=idx;i < data.length - windowSize - 1; i++)
		{
			energy1 = 0;
			energy2 = 0;
			startIdx =i;
			endIdx =i+windowSize - detectSize;
			//TODO here it can be faster
			for(j=0;j<detectSize; j++){
				energy1 += data[startIdx+j]*data[startIdx+j];
				energy2 += data[endIdx+j]*data[endIdx+j];	
			}
			if(energy2 > energy1*TIME){
				ret = i;
				break;
			}
		}
		return ret;
	}
	
	/**
	 * this function will divide soundSamples(in shortBuffer) into chunks and save the chunks into keyStrokes
	 * 
	 *  @return :number of chunks
	 */
	@SuppressLint("NewApi")
	private int chunkData_threshold()
	{
		int i;
		int count = 0;
		short[] dataall = soundSamples.array();
		int len = dataall.length/2;
		short[][] data = new short[2][len];
		//divide two channel
		//TODO this can be done when detecting
		for(i = 0; i < len; i++){
			data[0][i] = dataall[2*i];
			data[1][i] = dataall[2*i+1];
		}
		
		for(i=0;i< len; i++){
			if(data[0][i] > THRESHOLD || data[0][i] < -THRESHOLD){
				this.keyStrokesR[count] = Arrays.copyOfRange(data[0], i, i+CHUNKSIZE);
				this.keyStrokesL[count] = Arrays.copyOfRange(data[1], i, i+CHUNKSIZE);
				count++;
				i+= DIST;//find next peak, suppose two peak has at least DIST distance
			}
		}
		return count;
	}
	
	/***
	 * chunk the data in soundSamples into keystroke, both are private value
	 * @return : the chunk number in the sound Samples
	 */
	@SuppressLint("NewApi")
	private int chunkData_energy()
	{
		int index = 0;
		int i;
		int count = 0; //return value: chunk number
		short[] dataall = soundSamples.array();
		int len = dataall.length/2+1;
		short[][] data = new short[2][len];
		//divide two channel
		//TODO this can be done when detecting
		for(i = 0; i < len-1; i++){
			data[0][i] = dataall[2*i];
			data[1][i] = dataall[2*i+1];
		}
		while(index < len - windowSize)
		{
			int peak = detectStroke(index,data[0]);
			if(peak < 0)
				return 0;
			this.keyStrokesR[count] = Arrays.copyOfRange(data[0], peak, peak+CHUNKSIZE);
			this.keyStrokesL[count] = Arrays.copyOfRange(data[1], peak, peak+CHUNKSIZE);
			count++;
			index+= DIST;//find next peak, suppose two peak has at least DIST distance		
		}
		return count;
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
				/**
				// save the chunks to file in order to make sure we chunk right.
				int i;
				for(i = 0;i<num;i++){
					short[] data = new short[CHUNKSIZE];
					data = keyStrokesR[i];
					ByteBuffer myByteBuffer = ByteBuffer.allocate(data.length * 2);
					Log.d(LTAG, String.valueOf(soundSamples.remaining()));
					myByteBuffer.order(ByteOrder.BIG_ENDIAN);
					ShortBuffer myShortBuffer = myByteBuffer.asShortBuffer();
					myShortBuffer.put(data);
					FileChannel out;
					try {
						File mFile = new File("/sdcard/recBuffer/recR"+String.valueOf(i)+ ".pcm");
						if (!mFile.exists())
							mFile.createNewFile();
						out = new FileOutputStream(mFile, false).getChannel();
						out.write(myByteBuffer);
						out.close();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						Log.d(LTAG, e.getMessage());
						e.printStackTrace();
						System.exit(1);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						Log.d(LTAG, e.getMessage());
						e.printStackTrace();
						System.exit(1);
					}
					
					data = keyStrokesL[i];
				//	myByteBuffer = ByteBuffer.allocate(data.length * 2);
					Log.d(LTAG, String.valueOf(soundSamples.remaining()));
					myByteBuffer.clear();
					myByteBuffer.order(ByteOrder.BIG_ENDIAN);
					myShortBuffer = myByteBuffer.asShortBuffer();
					myShortBuffer.put(data);
				
					try {
						File mFile = new File("/sdcard/recBuffer/recL"+String.valueOf(i)+ ".pcm");
						if (!mFile.exists())
							mFile.createNewFile();
						out = new FileOutputStream(mFile, false).getChannel();
						out.write(myByteBuffer);
						out.close();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						Log.d(LTAG, e.getMessage());
						e.printStackTrace();
						System.exit(1);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						Log.d(LTAG, e.getMessage());
						e.printStackTrace();
						System.exit(1);
					}
				}				
				*/
		}
	}

	
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
