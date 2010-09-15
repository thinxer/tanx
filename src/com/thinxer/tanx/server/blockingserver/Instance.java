package com.thinxer.tanx.server.blockingserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.List;

import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.QueuedSender;

public class Instance implements Runnable {
	private static final String TAG = "Instance";

	private static final int SOCKET_TIMEOUT = 600000;

	private static int idCounter = 0;

	private Socket socket;
	private BlockingServer server;
	private List<Instance> instances;
	private Thread thread;
	private PrintStream printer;
	private int id;

	private QueuedSender sender;

	public Instance(BlockingServer server, Socket clientSocket, List<Instance> instances) throws IOException {
		this.socket = clientSocket;
		this.server = server;
		this.instances = instances;
		this.printer = new PrintStream(socket.getOutputStream());
		this.id = idCounter++;
	}

	public void serve() {
		this.thread = new Thread(this);
		this.sender = new QueuedSender(printer);
		this.sender.start();
		this.thread.start();
	}

	public void shutdown() throws IOException {
		this.sender.stop();
		this.printer.close();
		this.thread.interrupt();
	}

	@Override
	public void run() {
		try {
			socket.setSoTimeout(SOCKET_TIMEOUT);
			server.processMessage(id, "init");

			InputStreamReader in = new InputStreamReader(socket.getInputStream());
			BufferedReader bf = new BufferedReader(in);
			while (true) {
				String msg = bf.readLine();
				if (msg == null)
					break;
				server.processMessage(id, msg);
			}

		} catch (IOException e) {
			// e.printStackTrace();
			Log.e(TAG, "connection closed");
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
			}
			sender.stop();
			printer.close();
			synchronized (instances) {
				instances.remove(this);
			}
			server.processMessage(id, "leave");
		}
	}

	public void sendMessage(String message) {
		Log.v(TAG, "sendMessage: ", message);
		this.sender.sendMessage(message);
	}

	public int getId() {
		return id;
	}

}
