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
import java.util.concurrent.locks.Lock;

public class Statistic{
	private final int RECENTSIZE = 20;	
	/**********for accuracy*****************/
	public volatile int totalInputTimes;
	private volatile int totalErrorTimes;
	private List<Integer> recentResults;  //a buffer records recent input results, 
								  //used to calculate recent input accuracy
								  //value equals 1 means the corresponding input is an error
	public double recentAccuracy;
	public double totalAccuracy;
	
	//to log the result
	private List<Double> recentAccuList;
	private List<Double> totalAccuList;
	/*******for correction**************/
	private int correctionTimes;
	
	/***********for input rate**************/
	//date
//	Date startTime;
	public double inputRate;
	private long startTime;
	private List<Double> inputRatesList;
	private int lastInputTimes;
	private final int inputRateUpdateTime = 2000;
	
	/************for log*******************/
	class Operation{
		int op;
		String value;
	}
	List<Operation> operationList;
	
	//a thread to track input rate
	Thread timeThread = new Thread()
	{
	    @Override
	    public void run() {
	        try {
	            while(true) {
	                Thread.sleep(inputRateUpdateTime);
	                synchronized(this){      
		                inputRate = ((double)totalInputTimes-(double)lastInputTimes)/(double)inputRateUpdateTime*1000;
		                inputRatesList.add(inputRate);
		                lastInputTimes = totalInputTimes;
	                }
	            }
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
	    }
	};

	
	Statistic(long startT){
		startTime = startT;
		recentResults = new ArrayList<Integer>();
		//periodResults = new ArrayList<Integer>();
		recentAccuList = new ArrayList<Double>();
		totalAccuList = new ArrayList<Double>();
		inputRatesList = new ArrayList<Double>();
		operationList = new ArrayList<Operation>();
		totalInputTimes = 0;
		totalErrorTimes = 0;
		lastInputTimes = 0;
		//previousStage = 0;
		timeThread.start();
		//lock = new Lock();
		//firstInput = true;
	}
	
	/**
	 * add Input Result to the input result buffer 
	 * if op == 0 , user input a value, this function remove the first input(LRU) in the buffer and add a 0 into
	 * 				 the buffer
	 *       
	 * if op == 1, user correct a input through button,this function remove the last input result in the buffer, 
	 * 			which has been added wrongly  and then add a 1 into the buffer
	 * 
	 * if op == 2, user correct a input through editText
	 * if op == 3, user click backspace
	 * @param E if the input is an error
	 */
	public void addInput(int op, String re){
		synchronized(this){
			totalInputTimes++; //TODO here I count every input, including touch screen as an input
		}
		Operation operate = new Operation();
		if(op == 0){//user input is detected
			if(recentResults.size() == RECENTSIZE){
				recentResults.remove(0); //remove the first input result
				recentResults.add(0); //add the recent input result
			}else{
				recentResults.add(0);
			}
			operate.op = op;
			operate.value = re;
			operationList.add(operate);
		}else if(op == 1){ //corrected by button
			synchronized(this){
				totalInputTimes --;
			}
			totalErrorTimes ++;			
			recentResults.remove(recentResults.size() - 1);
			recentResults.add(1); 
			operate.op = op;
			operate.value = re;
			operationList.add(operate);
		}else if (op == 2){
			synchronized(this){
				totalInputTimes --;
			}
			totalErrorTimes ++;			
			recentResults.remove(recentResults.size() - 1);
			recentResults.add(1); 
			operate.op = op;
			operate.value = re;
			operationList.add(operate);
		}else if(op == 3){ //user click backspace
			correctionTimes ++;
			operate.op = op;
			operate.value = re;
			operationList.add(operate);
		}
		calculateAccuracy();
		if(op == 1 || op == 2){
			recentAccuList.remove(recentAccuList.size()-1);
			recentAccuList.add(recentAccuracy);
			totalAccuList.remove(totalAccuList.size()-1);
			totalAccuList.add(totalAccuracy);
		}else if( op == 0){
			recentAccuList.add(recentAccuracy);
			totalAccuList.add(totalAccuracy);
		}
		
	}
	
	
	/**
	 * this function calculate the accuracy from the buffer
	 */
	private void calculateAccuracy(){
		int recentErrorTimes = 0;
		int i =0;
		for(i = 0; i < recentResults.size();i++){
			recentErrorTimes += recentResults.get(i);
		}
		/*for(i = 0; i< periodResults.size(); i++){
			periodErrorTimes += periodResults.get(i);
		}*/
		this.recentAccuracy = 1 - (double)recentErrorTimes/(double)recentResults.size();
		//this.periodAccuracy = 1 - (double)periodErrorTimes/(double)periodResults.size();
		this.totalAccuracy = 1 - (double)totalErrorTimes/(double)totalInputTimes;
	}
	
	/**
	 * make logs about the accuracy
	 * @param fileName : Log's name
	 */
	public boolean doLogs(String fileName){
		//String logName = Environment.getExternalStorageDirectory().getAbsolutePath()+ "PaperKeyboardLog/" + fileName + "_log";
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
		Date date = new Date();
		DecimalFormat df = new DecimalFormat("0.0000");
		/******************log accuracy****************************/
		String Name = "/sdcard/PaperKeyboardLog/" + fileName + "_accuracy"+ dateFormat.format(date);
		File mFile_log = new File(Name);
		FileOutputStream outputStream_log = null;
		try {
			// choose to append
			if(!mFile_log.exists()){
				mFile_log.createNewFile();
			}
			outputStream_log = new FileOutputStream(mFile_log, true);
			OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
			String header = "paper keyboard test result: \n"
					+ "file created time: " + dateFormat.format(date) + "\n";
			osw.write(header);
			header = "total input times:" + String.valueOf(totalInputTimes) 
					+ "\n" + "error times:" + String.valueOf(totalErrorTimes) +"\n";
			osw.write(header);
			String form = "totalAccuracy                 recentAccuracy\n";
			osw.write(form);
			for (int i = 0; i < totalAccuList.size(); i++) {
				osw.write(df.format(totalAccuList.get(i))+ "            " + df.format(recentAccuList.get(i)) + "\n");	
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
		
		/*****************log inputRate********************/
	    Name = "/sdcard/PaperKeyboardLog/" + fileName + "_inputRate"  + dateFormat.format(date);
		mFile_log = new File(Name);
		outputStream_log = null;
		try {
			// choose to append
			if(!mFile_log.exists()){
				mFile_log.createNewFile();
			}
			outputStream_log = new FileOutputStream(mFile_log, true);
			OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
			String header = "paper keyboard input rate result: \n"
					+ "file created time: " + dateFormat.format(date) + "\n";
			osw.write(header);
			double totalInputRate = (double)totalInputTimes/(double)(System.currentTimeMillis()-startTime)*1000;
			header = "total input rate:" + String.valueOf(totalInputRate)+ "ch/s\n";
			osw.write(header);
			header = "total correction times:" + String.valueOf(correctionTimes) +"\n";
			osw.write(header);
			String form = "Accuracy every" + String.valueOf(inputRateUpdateTime) + "ms\n";
			osw.write(form);
			for (int i = 0; i < inputRatesList.size(); i++) {
				osw.write(df.format(inputRatesList.get(i)) + "\n");	
			}
			osw.flush();
			osw.close();
			osw = null;
		} catch (FileNotFoundException e) {
			System.out.println("the directory doesn't exist!");
			return false;
		} catch (IOException e) {
			System.out.println("IOException occurs");
			return false;
		}
		
		/********************log all operate************************/
		 Name = "/sdcard/PaperKeyboardLog/" + fileName + "_AllOperate" + dateFormat.format(date);
			mFile_log = new File(Name);
			outputStream_log = null;
			try {
				// choose to append
				if(!mFile_log.exists()){
					mFile_log.createNewFile();
				}
				outputStream_log = new FileOutputStream(mFile_log, true);
				OutputStreamWriter osw = new OutputStreamWriter(outputStream_log);
				String header = "paper keyboard test all operates log: \n"
						+ "file created time: " + dateFormat.format(date) + "\n";
				osw.write(header);
				header = "operate explaination: 0--normal input,  1--correct by button, 2--correct by EditText, 3--click backspace";
				osw.write(header);
				header = "total correction times:" + String.valueOf(correctionTimes) +"\n";
				osw.write(header);
				for (int i = 0; i < operationList.size(); i++) {
					osw.write(String.valueOf(operationList.get(i).op) + operationList.get(i).value +"\n");	
				}
				osw.flush();
				osw.close();
				osw = null;
			} catch (FileNotFoundException e) {
				System.out.println("the directory doesn't exist!");
				return false;
			} catch (IOException e) {
				System.out.println("IOException occurs");
				return false;
			}

		return true;
		
	}
	
}
