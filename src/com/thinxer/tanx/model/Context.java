package com.thinxer.tanx.model;

import java.util.HashMap;
import java.util.Map;

import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.Radian;

public class Context {
	private static final String TAG = "Context";
	private long currentTime = -1;
	private long lastTime = -1;
	private long offsetTime = 0; // serverTime - localTime

	public int myTankId = -1;

	public TanxMap map = new TanxMap();
	public Map<Integer, Tank> tanks = new HashMap<Integer, Tank>();
	public Map<Integer, Bullet> bullets = new HashMap<Integer, Bullet>();
	public Map<Integer, Gift> gifts = new HashMap<Integer, Gift>();

	public void clear() {
		Log.v(TAG, "clear");
		tanks.clear();
		bullets.clear();
		gifts.clear();
		myTankId = -1;
	}

	public void makeMove() {

		if (lastTime == -1)
			lastTime = System.currentTimeMillis() / 10;
		currentTime = System.currentTimeMillis() / 10;
		long loopTime = currentTime - lastTime;
		long serverTime = currentTime + offsetTime;

		for (Tank t : tanks.values()) {
			if (t.startGunRotateTime <= serverTime && t.remainedGunRotateTime >= 0) {
				long diffTime = serverTime - t.startGunRotateTime;
				if (diffTime >= t.remainedGunRotateTime) {
					t.gunAngle = t.targetGunAngle;
					t.remainedGunRotateTime = -1;
				} else {
					double diff = Radian.getDiff(t.gunAngle, t.targetGunAngle);
					int dir = Radian.getDirection(t.gunAngle, t.targetGunAngle);

					t.gunAngle += diff * dir * diffTime / t.remainedGunRotateTime;
					t.gunAngle = Radian.round(t.gunAngle);
					t.remainedGunRotateTime -= diffTime;
					t.startGunRotateTime = serverTime;
				}
			}
			if (t.startRotateTime <= serverTime && t.remainedRotateTime >= 0) {
				long diffTime = serverTime - t.startRotateTime;
				if (diffTime >= t.remainedRotateTime) {
					t.angle = t.targetAngle;
					t.remainedRotateTime = -1;
				} else {
					double diff = Radian.getDiff(t.angle, t.targetAngle);
					int dir = Radian.getDirection(t.angle, t.targetAngle);

					t.angle += diff * dir * diffTime / t.remainedRotateTime;
					t.angle = Radian.round(t.angle);
					t.remainedRotateTime -= diffTime;
					t.startRotateTime = serverTime;
				}
			}
			if (t.startMoveTime <= serverTime && t.remainedMoveTime >= 0) {
				long diffTime = serverTime - t.startMoveTime;
				if (diffTime >= t.remainedMoveTime) {
					t.x = t.targetX;
					t.y = t.targetY;

					t.remainedMoveTime = -1;
				} else {
					t.x += (t.targetX - t.x) / t.remainedMoveTime * diffTime;
					t.y += (t.targetY - t.y) / t.remainedMoveTime * diffTime;

					t.remainedMoveTime -= diffTime;
					t.startMoveTime = serverTime;
				}
			}

			if (t.shootCoolDown >= 0)
				t.shootCoolDown -= loopTime;
		}

		for (Bullet b : bullets.values()) {
			if (b.startMoveTime <= serverTime && b.remainedMoveTime >= 0) {
				long diffTime = serverTime - b.startMoveTime;

				if (diffTime >= b.remainedMoveTime) {
					b.x = b.targetX;
					b.y = b.targetY;

					b.remainedMoveTime = -1;
				} else {
					b.x += (b.targetX - b.x) / b.remainedMoveTime * diffTime;
					b.y += (b.targetY - b.y) / b.remainedMoveTime * diffTime;

					b.remainedMoveTime -= diffTime;
					b.startMoveTime = serverTime;
				}
			}
		}

		lastTime = currentTime;
	}

	public void sync(long offset) {
		Log.v(TAG, "sync, offset = " + offset);
		this.offsetTime = offset;
	}

	public Tank getMyTank() {
		return tanks.get(myTankId);
	}
}
