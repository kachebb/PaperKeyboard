package edu.wisc.perperkeyboard;

public class KeyStroke {
	private static final int THRESHOLD = 400;
	private static final int CHUNKSIZE= 2000;
	private static final int SAMPLERATE = 48000;
	private static final int DIST = 20000;
	//two parameter for detectStroke and chunkdata_energy
	private int windowSize = 50;
	private int detectSize = 10;
	
	
	public short[][] seperateChannels(short[] dataAll)
	{
		short[][] twoChannels = new short[2][CHUNKSIZE];
		int i;
		int len = dataAll.length/2;
		for(i=0; i < len; i++){
			twoChannels[0][i] = dataAll[2*i];
			twoChannels[1][i] = dataAll[2*i + 1];			
		}
		
		return twoChannels;
	}
	
	
	private int detectStroke_threshold(short[] data){
		int i;
		int len = data.length;
		int win = 100;
		for(i=0;i< len; i++){
			if(data[i] > THRESHOLD || data[i] < -THRESHOLD){
				return i;
			}
		}
		return -1;
	}
	
	
	private int detectStroke_energy( int[] data){
		int TIME = 100;
		int startIdx = 0;
		int endIdx = windowSize - detectSize;
		int i,j;
		int energy1, energy2;
		int ret = -1;
		for(i=0;i < data.length - windowSize - 1; i+= windowSize)
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
			energy1 *= TIME;
			if(energy2 > energy1){
				ret = i;
				break;
			}
		}
		return ret;
	}
	
}
