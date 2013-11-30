package edu.wisc.perperkeyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



import android.content.Context;
import android.util.Log;

public class Dictionary {
	private final String MTag="dictionaryClass";
	private Map<String, List<String>> dict;
	
	/**
	 * construct a dictionary with raw resource (id: resId)
	 * not case sensitive
	 * @param ctx
	 * @param resId
	 */
	public Dictionary(Context ctx, int resId){
		
		this.dict=new HashMap<String, List<String>>();
		//read in the dictionary from resource
		InputStream inputStream = ctx.getResources().openRawResource(resId);

	    InputStreamReader inputreader = new InputStreamReader(inputStream);
	    BufferedReader buffreader = new BufferedReader(inputreader);
	    String line;
	    try {
	        while (( line = buffreader.readLine()) != null) {
	        	String key=String.valueOf(line.charAt(0));
	        	if (this.dict.containsKey(key)){
	        		this.dict.get(key).add(line.toLowerCase());
	        	} else {
	        		List<String> mList=new ArrayList<String>();
	        		mList.add(line);
	        		this.dict.put(key, mList);
	        	}
	        }
	    } catch (IOException e) {
	    	Log.e(MTag, "fail to open the dictionary file");
	    }
	    Log.d(MTag, "finished reading in dictionary");
	    Log.d(MTag, "dictionary : "+this.dict);
	}
	
	/**
	 * get possible characters following input in our dictionary
	 * @param input
	 * @return
	 */
	public List<String> getPossibleChar(String input){
		Log.d(MTag, "dictionary input: "+input);
		if (input == null || input.length()<1){
			Log.d(MTag, "dictionary input's length is less than 1. return an null objects from dictionary");
			return null;
		}
		//timing
		long curTime=System.nanoTime();
		String key=String.valueOf(input.charAt(0));
		List<String> mList=this.dict.get(key);
		List<String> result=new ArrayList<String>();
		Set<String> mSet=new HashSet<String>();
		if (null == mList){
			Log.e(MTag,"couldn't find such key in the dictionary. ERROR");
			return null;
		} else {
			for (String item:mList){ //search through such list to match input
				if ((item.length()>input.length()+1) && (item.substring(0, input.length()).equals(input))){
					//get the character following input sequence
					mSet.add(String.valueOf(item.charAt(input.length())));
				}
			}
		}
		result.addAll(mSet);
		Log.d(MTag, "search time in (us): "+(System.nanoTime()-curTime)/1000);
		Log.d(MTag, "dictionary output: "+Arrays.toString(result.toArray()));
		return result;	
	}
}
