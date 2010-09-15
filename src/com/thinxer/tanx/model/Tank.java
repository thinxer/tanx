package com.thinxer.tanx.model;

public class Tank {
	public static final int SHOOT_COOLDOWN[] = { 20 };

	public static final int RADIUS[] = { 20 };
	public static final int GUN_RADIUS[] = { 15 };
	public static final int MAX_LIFE[] = { 1000 };

	public static final int STOPPED = 0;
	public static final int MOVING = 1;
	public static final int DEAD = 2;
	public static final int JUSTBORN = 3;

	public int state;

	public int mana;
	public int life;
	public String name = "NONAME";
	public int type = 0;
	public int gunType = 0;
	public int team = -1;

	public float x;
	public float y;
	public float targetX;
	public float targetY;
	public long startMoveTime = -1;
	public int remainedMoveTime = -1;

	public double angle;
	public double targetAngle;
	public long startRotateTime = -1;
	public int remainedRotateTime = -1;

	public double gunAngle;
	public double targetGunAngle;
	public long startGunRotateTime = -1;
	public int remainedGunRotateTime = -1;

	public int shootCoolDown = -1;
}
