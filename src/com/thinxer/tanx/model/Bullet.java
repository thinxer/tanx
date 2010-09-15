package com.thinxer.tanx.model;

public class Bullet {
	public static final int DAMAGES[] = { 100 };

	public int type;
	public int owner;

	public float x;
	public float y;
	public float targetX;
	public float targetY;
	public long startMoveTime = -1;
	public int remainedMoveTime = -1;

	public double angle;
}
