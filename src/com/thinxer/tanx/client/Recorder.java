package com.thinxer.tanx.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;

import com.thinxer.tanx.util.Message;

public class Recorder implements Runnable {
	public static final int STOPPED = 0;
	public static final int RECORDING = 1;
	public static final int PLAYING = 2;

	private GameLoop gl;
	private PrintWriter printer;
	private BufferedReader reader;
	private Thread thread;
	private boolean playing;
	private int state;

	public Recorder(GameLoop gl) {
		this.gl = gl;
	}

	public void initRecord() throws IOException {
		stopPlay();

		this.playing = false;

		if (printer != null)
			printer.close();
		File tmpFile = new File("lastReplay.tanxrep");
		this.printer = new PrintWriter(tmpFile);
		this.state = RECORDING;
	}

	public synchronized void record(String msg) {
		if (this.getState() == RECORDING)
			this.printer.println(System.currentTimeMillis() + " " + msg);
	}

	public void saveAs(String filename) throws IOException {
		this.printer.flush();
		copyFile(new File("lastReplay.tanxrep"), new File(filename));
	}

	public void stopRecord() throws IOException {
		if (printer != null)
			this.printer.close();
	}

	public void play(String filename) throws IOException {
		stopRecord();

		File f = new File(filename);
		this.reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
		this.thread = new Thread(this);
		this.playing = true;
		this.thread.start();
		this.gl.sendEmptyMessage(GameLoop.DISCONNECT_SERVER);
		this.state = PLAYING;
	}

	public void playLast() throws IOException {
		play("lastReplay.tanxrep");
	}

	public void stopPlay() throws IOException {
		if (reader != null)
			this.reader.close();
		this.playing = false;
		if (this.thread != null)
			this.thread.interrupt();
	}

	public static void copyFile(File sourceFile, File destFile) throws IOException {
		if (!destFile.exists()) {
			destFile.createNewFile();
		}

		FileChannel source = null;
		FileChannel destination = null;
		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if (source != null) {
				source.close();
			}
			if (destination != null) {
				destination.close();
			}
		}
	}

	@Override
	public void run() {
		long baseTime = -1;
		long startTime = System.currentTimeMillis();
		try {
			String msg = reader.readLine();
			while (playing) {
				if (msg == null)
					break;
				String[] tokens = msg.split(" ");

				if (baseTime == -1)
					baseTime = Long.parseLong(tokens[0]);

				Message message = new Message();
				if (tokens[2].equals("sync")) {
					message.what = GameLoop.FORCE_SYNC;
					message.obj = Long.valueOf(tokens[1]);
				} else {
					message.what = GameLoop.REPLAY_MESSAGE;
					message.obj = msg.substring(tokens[0].length() + 1);
				}
				gl.sendMessage(message);

				msg = reader.readLine();
				if (msg == null)
					break;
				long nextTime = Long.parseLong(msg.split(" ")[0]);
				try {
					long t = (nextTime - baseTime - (System.currentTimeMillis() - startTime));
					if (t > 0)
						Thread.sleep(t);
				} catch (InterruptedException e) {
					break;
				}
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		gl.sendDisplayMessage("Replay finished.");
		this.state = STOPPED;
	}

	public int getState() {
		return state;
	}
}
