package com.thinxer.tanx.server.blockingserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import com.thinxer.tanx.server.Server;
import com.thinxer.tanx.server.ServerLogic;
import com.thinxer.tanx.util.Log;

public class BlockingServer extends Server implements Runnable {

	private static final String TAG = "BlockingServer";

	private int port;
	ServerSocket serverSocket;
	private ServerLogic logic;
	private Thread thread;
	private boolean running;
	private List<Instance> instances;
	private long startTime;

	public BlockingServer(int port) {
		this.port = port;
		this.logic = new ServerLogic(this);
		this.running = false;
		this.instances = new LinkedList<Instance>();
	}

	@Override
	public void start() {
		thread = new Thread(this);
		startTime = System.currentTimeMillis();
		running = true;
		logic.start();
		thread.start();
	}

	@Override
	public void stop() {
		running = false;
		logic.stop();
		try {
			if (serverSocket != null)
				serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (thread != null)
			thread.interrupt();
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < 10; i++) {
				try {
					serverSocket = new ServerSocket(port);
				} catch (java.net.BindException e) {
					port++;
					continue;
				}
				break;
			}
			Log.d(TAG, "Listening on: " + serverSocket.getLocalSocketAddress());
			while (running) {
				Socket clientSocket;
				try {
					clientSocket = serverSocket.accept();
					Log.v(TAG, "new connection: " + clientSocket.getRemoteSocketAddress().toString());
					Instance instance = new Instance(this, clientSocket, instances);
					synchronized (instances) {
						instances.add(instance);
					}
					instance.serve();
				} catch (SocketException e) {
					Log.e(TAG, "SocketClosed");
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			synchronized (instances) {
				for (Instance i : instances) {
					try {
						i.shutdown();
					} catch (IOException ee) {
					}
				}
			}
		}
	}

	@Override
	public void sendMessage(int id, Object... args) {
		sendMessage(getTime(), id, args);
	}

	@Override
	public void sendMessage(long timestamp, int id, Object... args) {
		if (args.length > 0) {
			StringBuilder sb = new StringBuilder(128);
			// delay 100 ms, not necessary
			sb.append(timestamp);
			sb.append(" ");
			for (Object o : args) {
				sb.append(o);
				sb.append(" ");
			}
			String msg = sb.substring(0, sb.length() - 1);
			synchronized (instances) {
				for (Instance ins : instances) {
					if (ins.getId() == id || id == ALL_CLIENTS)
						ins.sendMessage(msg);
				}
			}
		}
	}

	public void processMessage(int id, String msg) {
		logic.putMessage(id, msg);
	}

	@Override
	public long getTime() {
		return (System.currentTimeMillis() - startTime) / 10;
	}

	@Override
	public int getListeningPort() {
		if (serverSocket != null)
			return this.serverSocket.getLocalPort();
		else
			return -1;
	}

	@Override
	public void loadMap(String map) throws IOException {
		logic.loadMap(map);
	}

	@Override
	public String getMap() {
		return logic.getMap();
	}

}
