package com.thinxer.tanx.util;

public class Radian {
	public static double round(double rad) {
		while (rad > Math.PI)
			rad -= Math.PI * 2;
		while (rad < -Math.PI)
			rad += Math.PI * 2;
		return rad;
	}

	public static int getDirection(double startRad, double finishRad) {
		while (finishRad < startRad)
			finishRad += Math.PI * 2;
		double diff1 = finishRad - startRad;
		while (startRad < finishRad)
			startRad += Math.PI * 2;
		double diff2 = startRad - finishRad;
		if (diff1 < diff2)
			return 1;
		else
			return -1;
	}

	public static double getDiff(double startRad, double finishRad) {
		if (getDirection(startRad, finishRad) > 0)
			return round(finishRad - startRad);
		else
			return round(startRad - finishRad);
	}
}
