package edu.wisc.liyuan.dictionaryCorrection;

public class Test {

	public static void main(String[] args){
		DictionaryCorrectior dc = new DictionaryCorrectorImpl();
		System.out.println("\n\n\n\nTest1");
		unitTest(dc, "test");
		
		System.out.println("\n\n\n\nTest2");
		unitTest(dc, " ");
		
		System.out.println("\n\n\n\nTest3");
		unitTest(dc, "tests'");
		
		System.out.println("\n\n\n\nTest4");
		unitTest(dc, "test's");
	}
	
	private static void unitTest(DictionaryCorrectior dc,String input){
		if(dc.getCorrectionList(input)!=null){
			for(String i:dc.getCorrectionList(input)){
				System.out.println(i);
			}
		}
		if(dc.getCorrectionWord(input)!=null){
			System.out.println(dc.getCorrectionWord(input));
		}	
	}
}
