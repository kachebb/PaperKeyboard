package edu.wisc.liyuan.dictionaryCorrection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.engine.Word;
import com.swabunga.spell.event.SpellChecker;

public class DictionaryCorrectorImpl implements DictionaryCorrectior {

	private SpellChecker spellChecker;
	private static SpellDictionaryHashMap dictionaryHashMap;
	private String youFilePath;
	private static final Pattern checker = Pattern.compile("^[a-zA-Z]*('|'s)?$");
	public static final String DEFAULFILEPATH;
	public static final String CONFIGFILEPATH = "conf.properties";

	static{
		Properties prop = new Properties();
		try {
			prop.load(new FileReader(new File(CONFIGFILEPATH)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		DEFAULFILEPATH = prop.getProperty("DefaultFilePath");
	}
	
	public DictionaryCorrectorImpl(){
		youFilePath = DEFAULFILEPATH;
		File dict = new File(youFilePath);	
		try {
			dictionaryHashMap = new SpellDictionaryHashMap(dict);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		spellChecker = new SpellChecker(dictionaryHashMap);
	}
	
	public DictionaryCorrectorImpl(String filePath){
		youFilePath = filePath;
		File dict = new File(youFilePath);
		try {
			dictionaryHashMap = new SpellDictionaryHashMap(dict);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		spellChecker = new SpellChecker(dictionaryHashMap);
	}

	@Override
	public String getCorrectionWord(String misspell) {
		String[] resultList = this.getCorrectionList(misspell);
		if(resultList == null || resultList.length==0){
			return null;
		}
		return resultList[0];
	}

	@SuppressWarnings("unchecked")
	@Override
	public String[] getCorrectionList(String misspell) {
		misspell = misspell.trim();
		if(misspell.equals("")){return null;}
		Matcher m = checker.matcher(misspell);
		if(!m.matches()){return null;}
		List<Word> su99esti0ns = spellChecker.getSuggestions(misspell, 0);
		Arrays.sort(su99esti0ns.toArray(),new Word());
		List<String> suggestions = new ArrayList<String>();
		for (Word suggestion : su99esti0ns) {
			suggestions.add(suggestion.getWord());
		}
		if(su99esti0ns.size()==0) return null;
		String[] result = new String[suggestions.size()];
		return suggestions.toArray(result);
	}

}
