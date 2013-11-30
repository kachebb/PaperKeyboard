package edu.wisc.perperkeyboard;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * use reading from gyro to gain additional information of key stroke detection
 * help cases: 1. whether the touch is on screen instead of on desk
 * 2. whether there is indeed a touch on the desk
 * @author jj
 *
 */
public class GyroHelper implements SensorEventListener{
	private static final String LTAG = "gyroHelper";
	
	/********************constants for different gyro readings at different cases *****************/
	public final long TOUCHSCREEN_TIME_INTERVAL=400000000;//400ms 	
	public final long DESK_TIME_INTERVAL=300000000;//400ms
	
	private final float GYRO_TOUCHSCRREN_THRESHOLD=(float) 0.15; 
	private final float GYRO_DESK_THRESHOLD=(float)0.003; //0.012; 	

	/************gyro scope sensor*********************/
	//UI thread update, recording thread check
	public volatile long lastTouchScreenTime; // record the system time
														// when touch screen is													// touched last time
	//record the system time last time when the gyro feel there is a stroke on the desk but not on the screen
	//try to use it to distinguish sound noise with actual key stroke
	public volatile long lastTouchDeskTime;
	private SensorManager mSensorManager;
	private Sensor mGyro;
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		//running on UI thread. double check
		float[] values=event.values;
//		Log.d(LTAG, "on sensor changed. time: "+System.nanoTime());
		for (float value: values){
			if (Math.abs(value) > this.GYRO_TOUCHSCRREN_THRESHOLD){
//				Log.d(LTAG, "touch screen!!!!! "+value);				
				this.lastTouchScreenTime=System.nanoTime();
				break;
			} else if (Math.abs(value) >= this.GYRO_DESK_THRESHOLD){
//				Log.d(LTAG, "touch desk!!!!! "+value);				
				this.lastTouchDeskTime=System.nanoTime();
				break;
			}
		}
	}

	public GyroHelper(Context mContext){
	    mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
	    mGyro= mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
	    this.lastTouchScreenTime=System.nanoTime();
	}
	
	public void register(){
	    mSensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_FASTEST);
		Log.d(LTAG, "registered sensor");
	    
	}
	
	public void unregister(){
	    mSensorManager.unregisterListener(this);
		Log.d(LTAG, "unregistered sensor");
	}
}
