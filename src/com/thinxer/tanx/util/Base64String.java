package com.thinxer.tanx.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class Base64String {
	public static String encode(String s) {
		byte[] bytes;
		try {
			bytes = s.getBytes("UTF8");
		} catch (UnsupportedEncodingException e) {
			bytes = s.getBytes();
		}
		if (bytes != null)
			return Base64.encodeBytes(bytes);
		else
			return null;
	}

	public static String decode(String s) {
		byte[] b;
		try {
			b = Base64.decode(s);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String ret;
		try {
			ret = new String(b, 0, b.length, "UTF8");
		} catch (UnsupportedEncodingException e) {
			ret = new String(b);
		}

		return ret;

	}
}