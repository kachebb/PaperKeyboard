package edu.wisc.jj;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

/**
 * utility class to use dictionary
 * @author jj
 *
 */
public class Dict {
	private HashSet<String> mDict;
	
	public Dict(){
		this.mDict= new HashSet<String>();
	}
	
	/**
	 * load dictionary file into memory
	 * The format of that file should be one word per line
	 * @param dictFile
	 * @return
	 */
	public boolean load(File dictFile){
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(dictFile));
			String line;
			while ((line = br.readLine()) != null) {
				mDict.add(line);
			}
			br.close();	
		} catch (FileNotFoundException e) {
			System.out.println("failed to read in dictionary, no such file");
			e.printStackTrace();
			return false;			
		} catch (IOException e){
			System.out.println("failed to read in dictionary, IO exception");			
			e.printStackTrace();
			return false;			
		}
		return true;
	}
	
	/**
	 * check whether the dictionary contain a particular string
	 * @param mString
	 * @return
	 */
	public boolean contain(String mString){
		return this.mDict.contains(mString);
	}
	
	public boolean remove(String mString){
		return this.mDict.remove(mString);
	}
	
	public boolean add(String mString){
		return this.mDict.add(mString);
	}
	
}
