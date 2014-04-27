/**
 * 
 */
package edu.wisc.liyuan.dictionaryCorrection;

/**
 * get correction words from the misspelled text
 * @author liyuan
 * 
 */
public interface DictionaryCorrectior {

	/**
	 * get correction word that have the most possibility from the misspelled text
	 * @author liyuan
	 * 
	 */
	public String getCorrectionWord(String misspell);
	
	
	
	/**
	 * get a list of correction words from the misspelled text
	 * @author liyuan
	 * 
	 */
	public String[] getCorrectionList(String misspell);
}
