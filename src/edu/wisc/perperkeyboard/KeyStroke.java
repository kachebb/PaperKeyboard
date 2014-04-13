package edu.wisc.perperkeyboard;

public class KeyStroke {
	public static int THRESHOLD = 3000;
	private static final int CHUNKSIZE= 2000;
	private static final int SAMPLERATE = 48000;
	//private static final int DIST = 20000; //TODO modified
	//two parameter for detectStroke and chunkdata_energy
	private static int windowSize = 50;
	private static int detectSize = 10;
	
	
	public static short[][] seperateChannels(short[] dataAll)
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
	
	
	public static int detectStroke_threshold(short[] data){
		int i;
		int len = data.length;
		int win = 100;
		for(i=0;i< len; i++){
			if(data[i] > THRESHOLD || data[i] < -THRESHOLD){
				if (0 == (i % 2))
					if(i-200 <0)
						return 0;
					else
						return i-200;
				else
					if(i-201 < 0)
						return 1;
					else
						return i-201; //make sure we are giving back the index that is even (2 channels)
			}
		}
		return -1;
	}
	
	
	private static int detectStroke_energy( int[] data){
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
