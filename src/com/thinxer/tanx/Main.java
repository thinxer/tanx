package com.thinxer.tanx;

import java.io.IOException;

import com.thinxer.tanx.client.GameLoop;
import com.thinxer.tanx.util.Log;

public class Main {
	private static final String TAG = "Main";

	public static void main(String[] args) throws IOException {
		Log.d(TAG, "starting tanx");

		// start game frame
		GameFrame gf = new GameFrame();
		gf.setVisible(true);
		GameLoop gl = new GameLoop(gf.getMainPanel());
		gf.setGameLoop(gl);

		gf.setTitle("Tanx!");

		gl.sendDisplayMessage("Welcome to Tanx");
		gl.sendDisplayMessage("by thinXer");

		try {
			Thread.sleep(500);
		} catch (InterruptedException e1) {
		}

		gl.run();

		Log.d(TAG, "stopping tanx");

		// wait for 1 second and exit..
		// i know this is ugly, but otherwise i don't know how to stop the
		// AWTEvent Thread...
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.exit(0);
			}
		}).start();

	}
}