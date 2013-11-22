package edu.wisc.perperkeyboard;

import java.util.ArrayList;
import java.util.List;

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
	
	//
	//private boolean firstInput;
	
	Statistic(){
		recentResults = new ArrayList<Integer>();
		periodResults = new ArrayList<Integer>();
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
			totalErrorTimes ++;			
			recentResults.remove(periodResults.size() -1);
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
	//	firstInput = false;
		
	}
	/*
	private void commit(){
		if(!firstInput){
			
		}
	}*/
	
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
		this.recentAccuracy = (double)recentErrorTimes/(double)recentResults.size();
		this.periodAccuracy = (double)periodErrorTimes/(double)periodResults.size();
		this.totalAccuracy = (double)totalErrorTimes/(double)totalInputTimes;
	}
	
	/**
	 * make logs about the accuracy
	 */
	private void doLogs(){
		
		
	}
}
