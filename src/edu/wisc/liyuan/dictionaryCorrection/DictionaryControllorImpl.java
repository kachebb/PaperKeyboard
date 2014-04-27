package edu.wisc.liyuan.dictionaryCorrection;

public class DictionaryControllorImpl implements DictionaryControllor,WordCollector{

	private DictionaryCorrectior dcImpl;
	private WordCollector wcImpl;
	String[] bufStrList;
	String bufStr;
	boolean hasChanged = false;
	
	public DictionaryControllorImpl(){
		dcImpl = new DictionaryCorrectorImpl();
		wcImpl = new WordCollectorImpl();
	}
	
	public DictionaryControllorImpl(String filePath){
		dcImpl = new DictionaryCorrectorImpl(filePath);
		wcImpl = new WordCollectorImpl();
	}
	
	@Override
	public String getCorrectionWord(String misspell) {
		return dcImpl.getCorrectionWord(misspell);
	}

	@Override
	public String[] getCorrectionList(String misspell) {
		return dcImpl.getCorrectionList(misspell);
	}

	@Override
	public void refresh() {
		if(hasChanged){
			bufStrList = dcImpl.getCorrectionList(wcImpl.getWord());
			if(bufStrList==null){
				bufStr = null;
			}else{
				bufStr = bufStrList[0];
			}
			hasChanged = false;
		}
	}

	@Override
	public void clear() {
		wcImpl.clear();
		hasChanged = true;
	}

	@Override
	public void attach(char c) {
		addToEnd(c);
	}

	@Override
	public void deleteLastChar() {
		delLstChr();
	}

	@Override
	public void addToEnd(char input) {
		wcImpl.addToEnd(input);
		hasChanged = true;
	}

	@Override
	public String getWord() {
		return wcImpl.getWord();
	}

	@Override
	public String getCorrectionWord() {
		refresh();
		return bufStr;
	}

	@Override
	public String[] getCorrectionList() {
		refresh();
		return bufStrList;
	}

	@Override
	public void delLstChr() {
		wcImpl.delLstChr();
		hasChanged = true;
	}

	@Override
	public boolean isChanged() {
		return hasChanged;
	}

}
