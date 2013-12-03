package edu.wisc.jj;


import org.oc.ocvolume.dsp.featureExtraction;

import ca.uol.aig.fftpack.RealDoubleFFT;

import android.util.Log;


/**
 * utility class for doing signal processing: fft, MFCC ...
 * @author jj
 *
 */
public class SPUtil {
	// hardCoded the sampling rate 48000Hz
	public static final int SAMPLING_RATE = 48000;
	private static final int FREQ_FEATURE_SZ = 800; // number of frequency bins used as features. targeting <5k hz
	private static final String LTAG = "jj-dspUtil";	
	
	/**
	 * get MFCC coefficients from fft spectrum
	 * 
	 * @param spectrum
	 *            : the fft of signal calculated by "fft" method in this dspUtil
	 *            class
	 * @param dataLength
	 *            : the original length of signal (not the length of spectrum,
	 *            since the spectrum is around a half of the original length)
	 * @return the MFCC coefficient array
	 */
	public static double[] mfcc(double[] spectrum, int dataLength) {
		featureExtraction mFeatureExtraction = new featureExtraction();
		// Mel Filtering
		int cbin[] = mFeatureExtraction
				.fftBinIndices(SAMPLING_RATE, dataLength);
		// get Mel Filterbank
		double fbank[] = mFeatureExtraction.melFilter(spectrum, cbin);
		// Non-linear transformation
		double f[] = mFeatureExtraction.nonLinearTransformation(fbank);
		// Cepstral coefficients
		double cepc[] = mFeatureExtraction.cepCoefficients(f);
		// Add resulting MFCC to array
		double[] mfccFeatures = new double[mFeatureExtraction.numCepstra];
		for (int i = 0; i < mFeatureExtraction.numCepstra; i++) {
			mfccFeatures[i] = cepc[i];
		}
		return mfccFeatures;
	}

	/**
	 * do fft on data
	 * 
	 * @param data
	 *            : the signal to do fft on
	 * @param useWindowFunction
	 *            : true -- apply hanning window before doing fft, false --
	 *            don't use any window functions
	 * @return the fft spectrum. The length of fft spectrum returned is length
	 *         of data/2 + 1 if length of data is even, (length of data + 1) /2
	 *         if length of data is odd. This behavior is trying to reduce the
	 *         storage because of the symmetry of fft
	 */
	public static double[] fft(double[] data, boolean useWindowFunction) {
		double[] fftInput = new double[data.length];
        if (useWindowFunction) {
                int windowSize = data.length;
                double[] windowedInput = applyWindowFunc(data, hanning(windowSize));
                System.arraycopy(windowedInput, 0, fftInput, 0,
                                windowedInput.length);
        } else {
                System.arraycopy(data, 0, fftInput, 0, data.length);
        }
        int dataLength = fftInput.length;
        RealDoubleFFT ftEngine = new RealDoubleFFT(dataLength);
        // coefficient returned: 0 -- 0th bin(real,0); 1,2 -- 1th
        // bin(real,imag); 3,4 -- 2th bin(real,imag)....
        // for even, n -- n/2 bin(real,0)
        ftEngine.ft(fftInput);
        // get the maginude from FFT coefficients
        double[] spectrum;		
		if (dataLength % 2 != 0) {// if odd
			spectrum = new double[(dataLength + 1) / 2];
			spectrum[0] = Math.pow(fftInput[0] * fftInput[0], 0.5);// dc
			// component
			for (int index = 1; index < dataLength; index = index + 2) {
				// magnitude =re*re + im*im
				double mag = fftInput[index] * fftInput[index]
						+ fftInput[index + 1] * fftInput[index + 1];
				spectrum[(index + 1) / 2] = Math.pow(mag, 0.5);
			}
		} else {// if even
			spectrum = new double[dataLength / 2 + 1];
			spectrum[0] = Math.pow(fftInput[0] * fftInput[0], 0.5);// dc
			// component.
			// real only
			for (int index = 1; index < dataLength - 1; index = index + 2) {
				// magnitude =re*re + im*im
				double mag = fftInput[index] * fftInput[index]
						+ fftInput[index + 1] * fftInput[index + 1];
				spectrum[(index + 1) / 2] = Math.pow(mag, 0.5);
			}
			// dc component. real only
			spectrum[spectrum.length - 1] = Math.pow(fftInput[dataLength - 1]
					* fftInput[dataLength - 1], 0.5);
		}
		return spectrum;
	}

	/**
	 * find the maximum value in a 2-D array
	 * @return the maximum value in such 2-D array
	 */
	public static double findMax(double[] data){
		double max=data[0];
		for (int row=0;row<data.length;row++){
				if (data[row] > max)
					max=data[row];
		}
		return max;
	}
	
	/**
	 * convert an array of short into an array of double.
	 * a deep copy is made
	 * @param data
	 * @return
	 */
	private static double[] shortArrayToDouble(short[] data){
		double[] result= new double[data.length];
		for (int row=0;row<data.length;row++){
			result[row]=(double)data[row];
		}
		return result;
	}

	/**
	 * get key stroke features to use as input features for KNN
	 * @param data: 2-D data, row 0 -- left channel, row 1 -- right channel
	 */
	public static double[] getAudioFeatures(short[][] data){
		double[] leftData=SPUtil.shortArrayToDouble(data[0]);
		double[] rightData=SPUtil.shortArrayToDouble(data[1]);
		/*************** pad 0s before doing fft ****************/
		double[] leftData0Pad=new double[leftData.length+2*1000];
		double[] rightData0Pad=new double[rightData.length+2*1000];
		for (int i=0;i<leftData0Pad.length;i++){
			leftData0Pad[i]=0;
			rightData0Pad[i]=0;			
		}
		System.arraycopy(leftData, 0, leftData0Pad, 1000, leftData.length);
		System.arraycopy(leftData, 0, rightData0Pad, 1000, rightData.length);		
		//get fft
		double[] leftFFT=SPUtil.fft(leftData0Pad, true);
		double[] rightFFT=SPUtil.fft(rightData0Pad, true);
		double[] features=null;
		if (leftFFT.length!=rightFFT.length){
			Log.e(LTAG,"length of left channel fft values is different from length of right channel");
			System.exit(1);
		} else { //if they are the same
			//combine two fft together and normalize
			features=new double[FREQ_FEATURE_SZ];
			System.arraycopy(leftFFT,0, features, 0, FREQ_FEATURE_SZ/2);
			System.arraycopy(rightFFT,0, features, FREQ_FEATURE_SZ/2, FREQ_FEATURE_SZ/2);
			double max=SPUtil.findMax(features);
			for (int idx=0;idx<features.length;idx++)
				features[idx]=features[idx]/max;			
		}
		return features;
	}

	
	/**
	 * get the sum of energy over an interval of frequencies 
	 * @param spectrum: the fft spectrum calculated by fft in this class
	 * @param freqResolution: the resolution of fft: fs/N (sampling rate / length of original signal)
	 * @param binLength: the interval of frequency for each bin: i.e. 20 means we sum energy over every 20 Hz
	 * @param binNum: number of energy bins returned: i.e. binLength 20, binNum 30 --- sum over every 20Hz, get 30 numbers (effective frequency 0Hz --- 20*30 Hz)
	 * @return
	 */
	public static double[] fftEnergyBin(double[] spectrum,
			double freqResolution, int binLength, int binNum) {
		double[] energyBin = new double[binNum];
		for (int index = 0; index < spectrum.length; index++) {
			double freq = index * freqResolution + freqResolution / 2.0;
			int binIndex = (int) Math.round(freq / binLength);
			if (binIndex > binNum - 1) {
				break;
			} else {
				energyBin[binIndex] += Math.pow(spectrum[index], 2.0);
			}
		}
		return energyBin;
	}

	/**
	 * apply desired window function to the signal for fft
	 * 
	 * @param signal
	 *            : the signal intended to apply the window on
	 * @param window
	 *            : window function
	 * @return the resulting signal after being applied the window
	 */
	private static double[] applyWindowFunc(double[] signal, double[] window) {
		double[] result = new double[signal.length];
		for (int i = 0; i < window.length; i++) {
			result[i] = signal[i] * window[i];
		}
		return result;
	}

	/**
	 * generate hanning window function
	 * 
	 * @param windowSize
	 * @return the hanning window of a size "windowSize"
	 */
	private static double[] hanning(int windowSize) {
		double h_wnd[] = new double[windowSize]; // Hanning window
		for (int i = 0; i < windowSize; i++) { // calculate the hanning window
			h_wnd[i] = 0.5 * (1 - Math
					.cos(2.0 * Math.PI * i / (windowSize - 1)));
		}
		return h_wnd;
	}

	/**
	 * smooth the curve
	 */
	public static void smooth(short[] data){
		/*********************smoothy the curve*****************************************/
		final double ALPHA = 0.05;
		int i;
		for(i= 0;i < data.length;i++){
			if(i == 0 || i==1)
				continue;
			else{
				data[i] = (short)((1-ALPHA)*(double)data[i-2]+ ALPHA*(double)data[i]);
			}
		}
	}
}
