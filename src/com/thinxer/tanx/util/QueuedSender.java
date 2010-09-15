package com.thinxer.tanx.util;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * a thread safe string sender
 *
 */
public class QueuedSender implements Runnable {

	private static final String TAG = "MessageSender";
	private PrintStream printer;
	private BlockingQueue<String> sendingMessages;
	private Thread thread;

	public QueuedSender(PrintStream printer) {
		this.printer = printer;
		this.sendingMessages = new LinkedBlockingQueue<String>();
	}

	public boolean sendMessage(String msg) {
		Log.v(TAG, "sendMessage: ", msg);
		return sendingMessages.offer(msg);
	}

	public void start() {
		this.thread = new Thread(this);
		this.thread.start();
		Log.v(TAG, "start thread " + this.thread.getName());
	}

	public void stop() {
		Log.v(TAG, "stop thread " + this.thread.getName());
		this.thread.interrupt();
	}

	@Override
	public void run() {
		try {
			while (true) {
				String msg = sendingMessages.take();
				if (msg != null) {
					printer.println(msg);
					printer.flush();
					Log.v(TAG, "sent: " + msg);
				}
			}
		} catch (InterruptedException e) {
			Log.v(TAG, "interrupted");
			return;
		}
	}

}
