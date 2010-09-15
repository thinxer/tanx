package com.thinxer.tanx.util;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class Geom {
	public static Point2D.Float getIntersectionPoint(Line2D line1, Line2D line2) {
		if (!line1.intersectsLine(line2))
			return null;
		double px = line1.getX1(), py = line1.getY1(), rx = line1.getX2() - px, ry = line1.getY2() - py;
		double qx = line2.getX1(), qy = line2.getY1(), sx = line2.getX2() - qx, sy = line2.getY2() - qy;

		double det = sx * ry - sy * rx;
		if (det == 0) {
			return null;
		} else {
			double z = (sx * (qy - py) + sy * (px - qx)) / det;
			if (z == 0 || z == 1)
				return null; // intersection at end point!
			return new Point2D.Float((float) (px + z * rx), (float) (py + z * ry));
		}
	} 
}
