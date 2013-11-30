package edu.wisc.perperkeyboard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.os.Environment;

public class Statistic{
	final int RECENTSIZE = 20;
	final int PERIODSIZE = 40;
	int totalInputTimes;
	int totalErrorTimes;
	List<Integer> recentResults;  //a buffer records recent input results, 
								  //used to calculate recent input accuracy
								  //value equals 1 means the corresponding input is an error
	List<Integer> periodResults; //similar to recentResults, the buffer size can be diffrent
	int previousStage;
	double recentAccuracy;
	double periodAccuracy;
	double totalAccuracy;
	
	//to log the result
	List<Double> recentAccuList;
	List<Double> totalAccuList;
	//
	//private boolean firstInput;
	
	Statistic(){
		recentResults = new ArrayList<Integer>();
		periodResults = new ArrayList<Integer>();
		recentAccuList = new ArrayList<Double>();
		totalAccuList = new ArrayList<Double>();
		totalInputTimes = 0;
		totalErrorTimes = 0;
		previousStage = 0;
		//firstInput = true;
	}
	
	/**
	 * add Input Result to the input result buffer 
	 * if E is true, this function remove the last input result in the buffer, which has been added wrongly
	 *        and then add a 1 into the buffer
	 * if E is false, this function remove the first input in the buffer and add a 0 into the buffer
	 * @param E if the input is an error
	 */
	public void addInput(boolean E){
		totalInputTimes++;
		if(E){
			totalInputTimes --;
			totalErrorTimes ++;			
			recentResults.remove(recentResults.size() - 1);
			recentResults.add(1); 
			periodResults.remove(periodResults.size()-1);
			periodResults.add(1);
		}else{
			if(recentResults.size() == RECENTSIZE){
				recentResults.remove(0); //remove the first input result
				recentResults.add(0); //add the recent input result
			}else{
				recentResults.add(0);
			}
			
			if(periodResults.size() == PERIODSIZE){
				periodResults.remove(0);
				periodResults.add(0);
			}else{
				periodResults.add(0);
			}			
		}
	//	commit();
		calculateAccuracy();
		if(E){
			recentAccuList.remove(recentAccuList.size()-1);
			recentAccuList.add(recentAccuracy);
			totalAccuList.remove(totalAccuList.size()-1);
			totalAccuList.add(totalAccuracy);
		}else{
			recentAccuList.add(recentAccuracy);
			totalAccuList.add(totalAccuracy);
		}
		
	}
	
	
	/**
	 * this function calculate the accuracy from the buffer
	 */
	private void calculateAccuracy(){
		int recentErrorTimes = 0;
		int periodErrorTimes = 0;
		int i =0;
		for(i = 0; i < recentResults.size();i++){
			recentErrorTimes += recentResults.get(i);
		}
		for(i = 0; i< periodResults.size(); i++){
			periodErrorTimes += periodResults.get(i);
		}
		this.recentAccuracy = 1 - (double)recentErrorTimes/(double)recentResults.size();
		this.periodAccuracy = 1 - (double)periodErrorTimes/(double)periodResults.size();
		this.totalAccuracy = 1 - (double)totalErrorTimes/(double)totalInputTimes;
	}
	
	/**
	 * make logs about the accuracy
	 * @param fileName : Log's name
	 */
	public boolean doLogs(String fileName){
		//String logName = Environment.getExternalStorageDirectory().getAbsolutePath()+ "PaperKeyboardLog/" + fileName + "_log";
		String accuName = "/sdcard/PaperKeyboardLog/" + fileName + "_accuracy";
		File mFile_log = new File(accuName);
	//	mFile_log.mkdir();
		FileOutputStream outputStream_log = null;
	//	File mFile_accu = new File(accuName);
	//	FileOutputStream outputStream_accu = null;
		try {
			// choose to append
			if(!mFile_log.exists()){
				mFile_log.createNewFile();
			}
			outputStream_log = new FileOutputStream(mFile_log, true);
			OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			DecimalFormat df = new DecimalFormat("0.0000");
			String header = "paper keyboard test result: \n"
					+ "file created time: " + dateFormat.format(date) + "\n"
					+ "totalAccuracy             recentAccuracy\n";
			osw.write(header);
			for (int i = 0; i < totalAccuList.size(); i++) {
				osw.write(df.format(totalAccuList.get(i))+ "            " + df.format(recentAccuList.get(i)));	
			}
			osw.flush();
			osw.close();
			osw = null;
		} catch (FileNotFoundException e) {
			// System.out.println("the directory doesn't exist!");
			return false;
		} catch (IOException e) {
			// System.out.println("IOException occurs");
			return false;
		}
		return true;
		
	}
	
}
