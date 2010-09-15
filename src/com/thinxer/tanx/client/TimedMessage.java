package com.thinxer.tanx.client;

public class TimedMessage implements Comparable<TimedMessage> {
	public long time;
	public String msg;

	@Override
	public int compareTo(TimedMessage o) {
		if (time > o.time)
			return 1;
		else if (time < o.time)
			return -1;
		return 0;
	}
}
