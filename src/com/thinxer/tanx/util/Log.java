package com.thinxer.tanx.util;

@SuppressWarnings("unused")
public class Log {
	public static final int VERBOSE = 0;
	public static final int DEBUG = 1;
	public static final int ERROR = 2;
	public static final int NONE = 99;

	/**
	 * the compiler will optimize those unnecessary outputs with this final
	 * constant.
	 */
	private static final int loggingLevel = DEBUG;

	public static void d(String tag, Object... info) {
		if (loggingLevel <= DEBUG)
			output(tag, info);
	}

	public static void v(String tag, Object... info) {
		if (loggingLevel <= VERBOSE)
			output(tag, info);
	}

	public static void e(String tag, Object... info) {
		if (loggingLevel <= ERROR)
			output(tag, info);
	}

	private static void output(String tag, Object... args) {
		StringBuilder sb = new StringBuilder(128);
		sb.append('[');
		sb.append(Thread.currentThread().getName());
		sb.append("]\t");
		sb.append(tag);
		sb.append('\t');
		for (Object o : args) {
			sb.append(o);
		}
		System.out.println(sb.toString());
	}

	public static int getLoggingLevel() {
		return loggingLevel;
	}

}
