package com.thinxer.tanx.model;

import java.awt.Dimension;
import java.awt.Panel;
import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import com.thinxer.tanx.client.ResourceLoader;
import com.thinxer.tanx.util.Log;

public class TanxMap {
	private static final String TAG = "TanxMap";
	public String mapfile;
	public String bgfile;
	public int width;
	public int height;
	public List<Line2D.Float> obstacles = new LinkedList<Line2D.Float>();

	public static void loadMap(Context context, String filename, Panel p) throws IOException {
		Log.v(TAG, "loading map: ", filename);
		ResourceLoader rl = ResourceLoader.getInstance();
		BufferedReader reader = new BufferedReader(new InputStreamReader(rl.getResource("res/" + filename)));
		String s = null;
		s = reader.readLine();
		String[] dim = s.split(" ");
		TanxMap m = context.map;
		m.obstacles.clear();
		
		m.width = Integer.parseInt(dim[0]);
		m.height = Integer.parseInt(dim[1]);
		m.bgfile = reader.readLine();
		m.mapfile = filename;
		if (m.bgfile.endsWith(".png"))
			m.bgfile = m.bgfile.substring(0, m.bgfile.length() - 4);
		int count = Integer.parseInt(reader.readLine());
		for (int i = 0; i < count; i++) {
			String[] t = reader.readLine().split(" ");
			Line2D.Float r = new Line2D.Float();
			r.x1 = Float.parseFloat(t[0]);
			r.y1 = Float.parseFloat(t[1]);
			r.x2 = Float.parseFloat(t[2]);
			r.y2 = Float.parseFloat(t[3]);
			m.obstacles.add(r);
		}
		m.obstacles.add(new Line2D.Float(-20, -20, m.width + 20, -20));
		m.obstacles.add(new Line2D.Float(-20, -20, -20, m.height + 20));
		m.obstacles.add(new Line2D.Float(-20, m.height + 20, m.width + 20, m.height + 20));
		m.obstacles.add(new Line2D.Float(m.width + 20, -20, m.width + 20, m.height + 20));
		Log.v("TanxMap", count + " loaded.");
		if (p != null) {
			p.setSize(m.width, m.height); // to trigger a resize event
			p.setPreferredSize(new Dimension(m.width, m.height));
		}
		reader.close();
	}

}
