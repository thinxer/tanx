package com.thinxer.tanx.util;

public class Message {
	public int what;
	public Object obj;
	public int arg1;
	public int arg2;

	@Override
	public String toString() {
		return what + ":" + obj.toString() + " " + arg1 + " " + arg2;
	}
}
