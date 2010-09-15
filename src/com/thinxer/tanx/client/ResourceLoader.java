package com.thinxer.tanx.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import com.thinxer.tanx.util.Log;

public class ResourceLoader {
	private static final String TAG = "ResourceCache";

	private static ResourceLoader _instance = null;

	private Map<String, BufferedImage> images;
	private Map<String, List<Clip>> sounds;
	private Map<String, byte[]> bytes;

	private ResourceLoader() {
		images = new HashMap<String, BufferedImage>();
		sounds = new HashMap<String, List<Clip>>();
		bytes = new HashMap<String, byte[]>();
	}

	public static ResourceLoader getInstance() {
		if (_instance == null)
			_instance = new ResourceLoader();
		return _instance;
	}

	/**
	 * get a cached image
	 * 
	 * @param name
	 *            the image name
	 * @return a BufferedImage
	 */
	public BufferedImage getImage(String name) {
		if (!images.containsKey(name)) {
			try {
				images.put(name, ImageIO.read(getResource("res/" + name + ".png")));
			} catch (IOException e) {
				images.put(name, null);
				Log.e(TAG, "cannot open file: " + name);
				e.printStackTrace();
			}
		}
		return images.get(name);
	}

	/**
	 * play sound clips, create new clip if old clips are running
	 * 
	 * @param name
	 *            the sound to play
	 */
	public void playSound(String name) {
		Clip clip = null;

		Log.v(TAG, "play sound: " + name);
		if (!sounds.containsKey(name))
			sounds.put(name, new LinkedList<Clip>());

		for (Clip c : sounds.get(name)) {
			if (c.isRunning())
				continue;
			c.setFramePosition(0);
			c.start();
			return;
		}

		// no available clips
		try {
			// get cached array
			byte[] buf = bytes.get(name);
			// if null, create a new one
			if (buf == null) {
				InputStream is = getResource("res/" + name + ".wav");
				int size = 1024;
				buf = new byte[size];
				int length = 0;
				while (true) {
					int c = is.read(buf, length, buf.length - length);
					if (c < 0)
						break;
					length += c;
					if (length == size) {
						size *= 2;
						byte[] buff = new byte[size];
						System.arraycopy(buf, 0, buff, 0, length);
						buf = buff;
					}
				}
			}
			ByteArrayInputStream bais = new ByteArrayInputStream(buf, 0, buf.length);
			AudioInputStream sample = AudioSystem.getAudioInputStream(bais);
			clip = AudioSystem.getClip();
			clip.open(sample);
			sounds.get(name).add(clip);
			clip.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public InputStream getResource(String name) {
		return ResourceLoader.class.getClassLoader().getResourceAsStream(name);
	}

}
