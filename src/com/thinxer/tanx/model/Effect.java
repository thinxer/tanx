package com.thinxer.tanx.model;

public class Effect {
	public static final int DURATION[] = { 200, 50 };
	
	public static final int DESTROY = 0;
	public static final int HIT = 1;
	
	public int remainedTime;
	public int type;
	public float x;
	public float y;
}
