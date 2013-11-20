package edu.wisc.perperkeyboard;

import java.util.HashMap;
import java.util.Map;

public class CapTrans {
	Map<String,String> map;
	
	public CapTrans(){
		map = new HashMap<String, String>();
		//translate numbers
		map.put("1", "!");
		map.put("2", "@");
		map.put("3", "#");
		map.put("4", "$");
		map.put("5", "%");
		map.put("6", "^");
		map.put("7", "&");
		map.put("8", "*");
		map.put("9", "(");
		map.put("0", ")");
		map.put("-", "_");
		map.put("=", "+");
		//translate right part
		map.put("BachSpace", "BackSpace");
		map.put("\\", "|");
		map.put("]", "}");
		map.put("[", "{");
		map.put("Enter", "Enter");
		map.put("'", "\"");
		map.put(";", ":");
		map.put("RShift", "RShift");
		map.put("/", "?");
		map.put(".", ">");
		map.put(",", "<");
		//translate right part
		map.put("`", "~");
		map.put("Tab", "Tab");
		map.put("Caps", "Caps");
		map.put("LShift", "LShift");
		//translate bottom part
		map.put("L Ctrl", "L Ctrl");
		map.put("L Alt", "L Alt");
		map.put("Space", "Space");
		map.put("R Alt", "R Alt");
		map.put("R Ctrl", "R Ctrl");
		
		
	}
	
	public String transWhenCaps(String input){
		if( input.charAt(0) >= 'a' && input.charAt(0) <= 'z')
			return input.toUpperCase();
		else
			return this.map.get(input);
		
	}
}
