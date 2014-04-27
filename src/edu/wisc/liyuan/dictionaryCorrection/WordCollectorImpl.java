package edu.wisc.liyuan.dictionaryCorrection;

public class WordCollectorImpl implements WordCollector {

	private StringBuffer sb;
	
	public WordCollectorImpl(){
		sb = new StringBuffer();
	}
	
	@Override
	public void addToEnd(char input) {
		if( (input>='a'&&input<='z') || (input>'A'&&input<'Z') )
			sb.append(input);
		return;
	}

	@Override
	public void clear() {
		sb.delete(0, sb.length());	
	}

	@Override
	public String getWord() {
		return sb.toString();
	}

	@Override
	public void delLstChr() {
		sb.deleteCharAt(sb.length()-1);
	}
	
	
}
