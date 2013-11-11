package edu.wisc.perperkeyboard;

import java.io.BufferedInputStream;

import java.io.DataOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

//Modified by Kaichen:
//1. set buffer size as a parameter
//2. 
public class RecBuffer implements Runnable {
	private static final String LTAG = "jjTag";
	DataOutputStream os; // the input for current run time
	private static final int BUFSIZE = 4096;
	// the receiving thread
	private RecBufListener bufReceiver; //assume only one receiver present in the system
	
	public void setReceiver(RecBufListener rbl){
		this.bufReceiver=rbl;
	}

	@Override
	public void run() {
		try {
			Log.d(LTAG, "recording thread id : " + Thread.currentThread().getId());					
			Process p = null;
			p = Runtime.getRuntime().exec("/system/xbin/su");
			this.os = new DataOutputStream(p.getOutputStream());
			Log.d(LTAG, "Starts!");
			SystemClock.sleep(100);

			os.writeBytes("/system/bin/tinymix 27 120\n");
			os.flush();
			SystemClock.sleep(100);
			os.writeBytes("/system/bin/tinymix 54 11\n");
			os.flush();
			SystemClock.sleep(100);
			os.writeBytes("/system/bin/tinymix 55 12\n");
			os.flush();
			SystemClock.sleep(100);
			os.writeBytes("/system/bin/tinymix 76 1\n");
			os.flush();
			SystemClock.sleep(100);
			os.writeBytes("/system/bin/tinymix 77 1\n");
			os.flush();
			SystemClock.sleep(100);

			os.writeBytes("/system/xbin/killall tinycap\n");
			os.flush();
			SystemClock.sleep(500);
			Log.d(LTAG, "killing existing tinycap process before recording!");

			BufferedInputStream reader = new BufferedInputStream(
					p.getInputStream());
			byte[] buffer = new byte[BUFSIZE*2];
			int read;

			// Clear existing stdout buffer
			try {
				while (reader.available() > 0) {
					read = reader.read(buffer);
					Log.d(LTAG, "Trying to clear stdout buffer");
					Log.d(LTAG, new String(buffer));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			Log.d(LTAG, "Start tinycap!");

			// infinite recording
			os.writeBytes("/system/bin/tinycap /sdcard/tmp.wav -D 0 -d 1 -c 2 -r 48000 -b 16\n");
			os.flush();

			while ((read = reader.read(buffer,0,buffer.length)) > 0) {
				//copy data. if odd, then take the floor
				short[] outData = new short[read/2];
				// to turn bytes to shorts 
				ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(outData);								
				//call receiver
				if (null != this.bufReceiver){
					Log.d(LTAG, "real time recorder called receiver. read : " + read);					
					this.bufReceiver.onRecBufFull(outData);
				} else {
					Log.d(LTAG, "no one is listening to me. I'm a sad real time recorder");
				}
				//clear out original data
				for (int i=0;i<read;i++)
					buffer[i]=0;
				//stop the recording thread is signaled
				if (Thread.currentThread().isInterrupted()){
					Log.d(LTAG, "recording get interrupted");					
					stopRecording();
					return;
				}
			}
			Log.d(LTAG, "try to read. bytes read: " + read);								
		} catch (Exception e) {
			e.printStackTrace();
			Log.d(LTAG, "error occured!!");											
			System.exit(1);
		}
	}

	/**
	 * kill tinycap.
	 * @return
	 */
	private boolean stopRecording() {
		if (null == this.os) {
			Log.d(LTAG,
					"try to stop recording thread. But the recording thread is not present.");
			return false;
		} else {
			try {
				os.writeBytes("/system/xbin/killall tinycap\n");
				os.flush();
			} catch (IOException e) {
				Log.e(LTAG,
						"IO error occur when trying to stop recording thread");
				e.printStackTrace();
				return false;
			}
			Log.d(LTAG, "tinycap killed after recording!");
		}
		return true;
	}
}