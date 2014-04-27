package edu.wisc.liyuan.dictionaryCorrection;

public interface DictionaryControllor extends DictionaryCorrectior{
	public void refresh();
	public void clear();
	public void attach(char c);
	public void deleteLastChar();
	public String getCorrectionWord();
	public String[] getCorrectionList();
	public boolean isChanged();
	
}
