package edu.wisc.perperkeyboard;
import android.util.Log;

/**
 * audio buffer for use for key stroke detection
 * @author jj
 *
 */
public class AudioBuffer {
	private final static String MTAG="audioBufferTag";

	// number of channels
	private int channelCount;

	// audio length needed for extracting features
	private int audioLengthForFeature;

	// maximum seconds to buffer
	private int bufferedMilliSeconds = 50;
	private int count =0;
	private volatile short[] buffer;
	private int capacity = 0;
	//next available spot in the buffer
	//the head of this audio buffer when the buffer is full
	private int tail= 0;
	private boolean full=false;

	private int validIdx=-1;
	
	/**
	 * construct an audio buffer
	 * @param samplingRate
	 * @param channelCount
	 * @param audioLengthForFeature
	 */
	public AudioBuffer(int samplingRate, int channelCount, int audioLengthForFeature) {
		// set the number of channels
		this.channelCount = channelCount;
		//multiply by 2 since we have 2 channels
		this.audioLengthForFeature=audioLengthForFeature*2;
		capacity = (samplingRate * channelCount) * bufferedMilliSeconds / 1000 * 2;
		buffer = new short[capacity];
	}

	/**
	 * Returns the number of samples currently in the buffer.
	 * 
	 * @return
	 */
	public int getSamplesCount() {
		return count > 0 ? count / channelCount : 0;
	}

	/**
	 * clear the whole buffer
	 * @return
	 */
	public void clear(){
		for (Short value:buffer){
			value=0;
		}
	}
	
	/**
	 * Adds an array of shorts to the buffer.
	 * 
	 * @param sample
	 * @return true if added successfully and false otherwise
	 */
	public void add(short[] samples) {
		if (samples.length % 2 !=0){
			Log.e(MTAG, "samples added to the audio buffer is not a multiple of 2");
		}
		if (samples.length>this.capacity){
			Log.e(MTAG, "samples added to the audio buffer is larger than aduio buffer itself");
			System.exit(1);
		}
		for (Short value:samples){
			this.buffer[tail]=value;
			tail = tail + 1;
			if (!this.full && tail>=this.capacity){
				this.full=true;
			}
			tail= tail % this.capacity;
		}
	}

	/**
	 * return the buffer
	 *   
	 */
	public short[] getBuffer(){
		short[] result;
		if (!this.full){//if not full, return 0 to tail
			result=new short[tail];
			System.arraycopy(this.buffer, 0, result, 0, tail);
		} else { //if full, return tail to the end and 0 to tail-1
			result=new short[this.capacity];
			System.arraycopy(this.buffer, tail, result, 0, this.capacity-tail);
			System.arraycopy(this.buffer, 0, result, this.capacity-tail, tail);			
		}
		return result;
	}

	/**
	 * whether audio buffer has buffered up enough data for feature extraction
	 * @return
	 */
	public boolean hasKeyStrokeData(){
		// there is no valid starting point that is over audio threshold
		if (this.validIdx==-1)
			return false;
		else {
			//find the length of audio interested
			//check whether the valid length of such audio is larger than
			//trunk size
			int validLength=(this.validIdx>this.tail)? this.capacity - this.validIdx + this.tail : this.tail-this.validIdx;
			if (validLength >= this.audioLengthForFeature)
				return true;
		}
		return false;
	}
	
	/**
	 * get the key stroke audio interval for feature extraction
	 * clear the validIdx
	 * @return null if there is no suitable audio interval to return
	 */
	public short[] getKeyStrokeAudioForFeature(){
		if (!hasKeyStrokeData()){
			return null;
		}
		short[] result=new short[this.audioLengthForFeature];
		if (this.validIdx>this.tail){
			if (this.capacity-this.validIdx < this.audioLengthForFeature){
				System.arraycopy(this.buffer, this.validIdx, result, 0, this.capacity-this.validIdx);
				int remainNum=this.audioLengthForFeature - (this.capacity-this.validIdx);
				System.arraycopy(this.buffer, 0, result, this.capacity-this.validIdx,remainNum);				
			} else {
				System.arraycopy(this.buffer, this.validIdx, result, 0, this.audioLengthForFeature);				
			}
		} else {
			System.arraycopy(this.buffer, this.validIdx, result, 0, this.audioLengthForFeature);
		}
		this.validIdx=-1;
		return result;
	}
	
	/**
	 * set the valid index of  
	 * @param index
	 * @param lengthOfLastAddedData
	 */
	public void setValidIdx(int indexInPrevAddedData, int lengthOfLastAddedData){
		int prevAddedStartIdx= (this.tail-lengthOfLastAddedData >=0)?this.tail-lengthOfLastAddedData : this.tail-lengthOfLastAddedData + this.capacity;
		this.validIdx=(prevAddedStartIdx + indexInPrevAddedData >=0 ) ? prevAddedStartIdx + indexInPrevAddedData: prevAddedStartIdx + indexInPrevAddedData+this.capacity;
	}

	/**
	 * clear valid idx.
	 * this is used when there is a stroke in audio, 
	 * but gyro shows nothing. Therefore we need to delete such stroke
	 */
	public void clearValidIdx(){
		this.validIdx=-1;
	}
	
	/**
	 * @return whether an audio stroke has been already detected, and we're just collecting 
	 * more data 
	 */
	public boolean needMoreAudio(){
		if (this.validIdx!=-1){
			Log.d(MTAG, "need more audio: true. validIdx: "+this.validIdx + " tail: "+this.tail);			
		}
		return (this.validIdx!=-1);
	}
}