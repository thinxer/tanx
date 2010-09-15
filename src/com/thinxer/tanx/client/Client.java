package com.thinxer.tanx.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.Message;
import com.thinxer.tanx.util.QueuedSender;

public class Client implements Runnable {
	private static final String TAG = "Client";
	GameLoop gl;
	private String server;
	private int port;
	private Socket socket;
	private BufferedReader reader;
	private PrintStream printer;
	private Thread thread;
	private boolean running;

	private QueuedSender sender;

	public Client(String server, int port) {
		this.server = server;
		this.port = port;
	}

	public void setGameLoop(GameLoop gameLoop) {
		this.gl = gameLoop;
	}

	public void start() {
		thread = new Thread(this);
		running = true;
		thread.start();
	}

	public void stop() {
		running = false;
		if (socket != null)
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		if (sender != null)
			sender.stop();
	}

	@Override
	public void run() {
		try {
			socket = new Socket(server, port);
			Log.d(TAG, "connected to " + socket.getRemoteSocketAddress());
			gl.sendEmptyMessage(GameLoop.CONNECTED);
			gl.sendDisplayMessage("connected to " + socket.getRemoteSocketAddress());
			InputStreamReader isr = new InputStreamReader(socket.getInputStream());
			printer = new PrintStream(socket.getOutputStream());
			sender = new QueuedSender(printer);
			sender.start();
			reader = new BufferedReader(isr);
			while (running) {
				String cmd = reader.readLine();
				Log.v(TAG, "<<< ", cmd);
				if (cmd != null) {
					Message message = new Message();
					message.what = GameLoop.SERVER_MESSAGE;
					message.obj = cmd;
					gl.sendMessage(message);
				}
			}
		} catch (SocketException e) {
			// nothing
			if (sender != null)
				sender.stop();
			Log.e(TAG, "socket exception");
			gl.sendDisplayMessage("lost connection to " + server + ":" + port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			gl.sendDisplayMessage("cannot resolve server address");
		} catch (IOException e) {
			e.printStackTrace();
			gl.sendDisplayMessage("network error");
		} finally {
			if (sender != null)
				sender.stop();
		}
	}

	public void sendMessage(Object... args) {
		if (running && sender != null && args.length > 0) {
			StringBuilder sb = new StringBuilder();
			for (Object o : args) {
				sb.append(o);
				sb.append(' ');
			}
			sender.sendMessage(sb.substring(0, sb.length() - 1));
		}
	}
}
