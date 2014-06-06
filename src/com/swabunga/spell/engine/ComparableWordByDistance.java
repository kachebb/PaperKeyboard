package com.swabunga.spell.engine;

public class ComparableWordByDistance extends Word implements Comparable<Word> {

	@Override
	public int compareTo(Word another) {
		return this.compare(this, another);
	}

}
