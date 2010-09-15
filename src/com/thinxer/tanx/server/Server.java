package com.thinxer.tanx.server;

import java.io.IOException;

public abstract class Server {
	public static final int ALL_CLIENTS = -1;

	/**
	 * start this server in a new thread
	 */
	public abstract void start();

	/**
	 * stop this server
	 */
	public abstract void stop();

	/**
	 * send message to clients
	 * 
	 * @param id
	 *            ALL_CLIENTS for all clients, otherwise client id
	 * @param message
	 *            message to be sent
	 */
	public abstract void sendMessage(int id, Object... args);

	/**
	 * send message to clients with the specific timestamp
	 * 
	 * @param id
	 *            ALL_CLIENTS for all clients, otherwise client id
	 * @param message
	 *            message to be sent
	 */
	public abstract void sendMessage(long timestamp, int id, Object... args);

	/**
	 * 
	 * @return the port the server is listening on
	 */
	public abstract int getListeningPort();

	/**
	 * get server clock
	 * 
	 * @return server clock time
	 */
	public abstract long getTime();

	public abstract void loadMap(String map) throws IOException;

	public abstract String getMap();
}