package edu.wisc.liyuan.dictionaryCorrection;

/**
 * collect chars and return words
 * @author liyuan
 * 
 */

public interface WordCollector{
	
	/**
	 * add new char to the end
	 * @author liyuan
	 * 
	 */
	public void addToEnd(char input);
	
	/**
	 * clear the buffer
	 * @author liyuan
	 * 
	 */
	public void clear();
	
	
	
	/**
	 * get Corrent Word
	 * @author liyuan
	 * 
	 */
	public String getWord();
	
	/**
	 * delete the last char
	 * @author liyuan
	 * 
	 */
	public void delLstChr();



}
