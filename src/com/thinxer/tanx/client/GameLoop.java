package com.thinxer.tanx.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.thinxer.tanx.model.Bullet;
import com.thinxer.tanx.model.Context;
import com.thinxer.tanx.model.Effect;
import com.thinxer.tanx.model.Gift;
import com.thinxer.tanx.model.Tank;
import com.thinxer.tanx.model.TanxMap;
import com.thinxer.tanx.util.Base64String;
import com.thinxer.tanx.util.Geom;
import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.Message;

public class GameLoop {

	public static final Color TEAM_COLORS[] = { Color.blue, Color.cyan, Color.pink, Color.green, Color.magenta,
			Color.red, Color.black, Color.gray };

	public static final int DISPLAY_MESSAGE = 0;

	public static final int CHANGE_TEAM = 21;

	public static final int FORCE_SYNC = 31;

	public static final int CONNECT_SERVER = 500;
	public static final int DISCONNECT_SERVER = 501;
	public static final int CONNECTED = 502;
	public static final int SERVER_MESSAGE = 510;

	public static final int REPLAY_MESSAGE = 700;

	public static final int CHAT_MESSAGE = 800;

	public static final int KEY_DOWN = 1000;
	public static final int KEY_UP = 1001;
	public static final int PATH_KEY = 1010;

	public static final int MOUSE_MOVE = 2001;
	public static final int ACTION_FIRE_ON = 2002;
	public static final int ACTION_FIRE_OFF = 2003;
	public static final int ACTION_MOVE_ON = 2004;
	public static final int ACTION_MOVE_OFF = 2005;

	public static final int BUTTON_LEFT = 2011;
	public static final int BUTTON_RIGHT = 2012;

	private static final String TAG = "GameLoop";

	private static final long DISPLAY_MESSAGE_TIME = 10000;
	private static final long DISPLAY_MESSAGE_MAX_ITEM = 10;
	private static final int VIEW_RANGE = 400;

	private static final double MOVE_MIN_STEP = 20;

	// for display
	private Panel panel;
	// for message passing
	private BlockingQueue<Message> messageQueue;
	// running flag
	private boolean running;

	// for FPS calculation
	private double framesPerSecond;
	private long lastTime;
	private int framesCount;

	// for logic
	private int mouseX;
	private int mouseY;
	private long lastLogicTime;

	// display
	private List<TimedMessage> displayMessages;

	// network
	private Client client;
	private long lastSyncTime;
	long loopStartTime;

	// ...
	private String myTankName;
	private boolean connected;

	// recorder
	private Recorder recorder;

	// effects
	private List<Effect> effects;

	// mask
	private boolean maskEnabled;

	// move path
	private boolean pathKeyDown;
	private List<Point2D> movePath;

	// misc
	private long lastRotateTime;
	private long lastFireTime;
	private long lastMoveTime;
	private boolean firing;

	private boolean moving;

	public GameLoop(Panel p) {
		this.panel = p;
		this.running = false;
		this.messageQueue = new LinkedBlockingQueue<Message>();
		this.displayMessages = new LinkedList<TimedMessage>();
		this.setTankName("tanx-" + (int) (Math.random() * 10000));
		this.recorder = new Recorder(this);

		this.effects = new LinkedList<Effect>();
		this.maskEnabled = true;
		this.pathKeyDown = false;
		this.movePath = new LinkedList<Point2D>();
		this.lastRotateTime = -1;
		this.firing = false;
	}

	public void run() {
		Graphics g = panel.getGraphics();
		Image dbImage = panel.createImage(panel.getWidth(), panel.getHeight());
		Graphics2D dbg = (Graphics2D) dbImage.getGraphics();
		Context context = new Context();
		ResourceLoader rc = ResourceLoader.getInstance();

		init(context, dbg);

		running = true;
		loopStartTime = System.currentTimeMillis();
		while (running) {
			long startTime = System.currentTimeMillis();

			// sync every 2 seconds
			if (client != null && startTime - lastSyncTime > 2000) {
				client.sendMessage("sync " + (long) ((startTime - loopStartTime) / 10));
				lastSyncTime = startTime;
			}

			// process messages
			Message msg = messageQueue.poll();
			while (msg != null) {
				processMessage(context, msg);
				msg = messageQueue.poll();
			}

			doClientLogic(context);
			context.makeMove();

			// draw scene
			draw(context, dbg, rc);
			g.drawImage(dbImage, 0, 0, panel);

			// calculate FPS
			long endTime = System.currentTimeMillis();
			framesCount++;
			if (endTime - lastTime > 100) {
				framesPerSecond = framesPerSecond * 0.9 + framesCount;
				framesCount = 0;
				lastTime = endTime;
			}
		}

		if (g != null)
			g.dispose();
		if (dbg != null)
			dbg.dispose();
	}

	private void init(Context context, Graphics2D dbg) {
		ResourceLoader.getInstance().playSound("startup");

		dbg.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
		// dbg.setFont(new Font("Courier", Font.BOLD, 12));
		dbg.setColor(Color.blue);
		try {
			TanxMap.loadMap(context, "startup.txt", panel);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void doClientLogic(Context context) {
		if (lastLogicTime == -1)
			lastLogicTime = System.currentTimeMillis() / 10;
		long curTime = System.currentTimeMillis() / 10;
		long diffTime = curTime - lastLogicTime;

		Iterator<Effect> ite = effects.iterator();
		while (ite.hasNext()) {
			Effect effect = ite.next();
			effect.remainedTime -= diffTime;
			if (effect.remainedTime < 0)
				ite.remove();
		}

		Tank t = context.getMyTank();
		if (t != null && !pathKeyDown && movePath.size() > 0) {
			Point2D next = movePath.get(0);
			if (next.distance(t.x, t.y) <= 4.0f || t.remainedMoveTime < 0) {
				if (next.distance(t.x, t.y) < MOVE_MIN_STEP)
					movePath.remove(0);
				if (client != null && movePath.size() > 0) {
					next = movePath.get(0);
					client.sendMessage("move-to", next.getX(), next.getY());
				}
			}
		}

		if (firing && client != null && curTime - lastFireTime >= 10) {
			client.sendMessage("shoot");
			lastFireTime = curTime;
		}

		if (moving && client != null && curTime - lastMoveTime >= 10) {
			if (pathKeyDown) {
				Point2D p = new Point2D.Float(mouseX, mouseY);
				if (movePath.size() > 0) {
					Point2D lastPoint = movePath.get(movePath.size() - 1);
					if (p.distance(lastPoint) >= MOVE_MIN_STEP) {
						movePath.add(p);
					}
				} else {
					movePath.add(p);
				}
			} else {
				movePath.clear();
				client.sendMessage("move-to", mouseX, mouseY);
			}

			lastMoveTime = curTime;
		}

		if (client != null && curTime - lastRotateTime >= 10) {
			client.sendMessage("rotate-to", mouseX, mouseY);
			lastRotateTime = curTime;
		}

		lastLogicTime = curTime;
	}

	public void stop() {
		running = false;
		if (client != null)
			client.stop();
		if (recorder != null)
			try {
				recorder.stopRecord();
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "error stopping recording");
			}
	}

	public boolean sendMessage(Message message) {
		if (messageQueue == null)
			return false;
		return messageQueue.offer(message);
	}

	public boolean sendEmptyMessage(int what) {
		Message msg = new Message();
		msg.what = what;
		return sendMessage(msg);
	}

	public boolean sendDisplayMessage(String str) {
		Message msg = new Message();
		msg.what = GameLoop.DISPLAY_MESSAGE;
		msg.obj = str;
		return sendMessage(msg);
	}

	private void draw(Context context, Graphics2D g, ResourceLoader rc) {
		g.setComposite(AlphaComposite.SrcAtop);

		// prepare
		g.clearRect(0, 0, panel.getWidth(), panel.getHeight());
		double s2 = Math.sqrt(2);
		Tank myTank = context.tanks.get(context.myTankId);

		// generate mask
		Area mask = new Area(new Rectangle(context.map.width, context.map.height));
		if (maskEnabled && myTank != null) {
			final double inf = 1000000;

			if (myTank != null) {
				for (Tank t : context.tanks.values()) {
					if ((myTank.team == -1 && myTank == t) || (myTank.team != -1 && myTank.team == t.team)) {
						Area invMask = new Area(new Rectangle(context.map.width, context.map.height));
						for (Line2D line : context.map.obstacles) {
							double a1 = Math.atan2(line.getY1() - t.y, line.getX1() - t.x);
							double a2 = Math.atan2(line.getY2() - t.y, line.getX2() - t.x);
							Polygon p = new Polygon();
							p.addPoint((int) line.getX1(), (int) line.getY1());
							p.addPoint((int) line.getX2(), (int) line.getY2());
							p.addPoint((int) (t.x + inf * Math.cos(a2)), (int) (t.y + inf * Math.sin(a2)));
							p.addPoint((int) (t.x + inf * Math.cos(a1)), (int) (t.y + inf * Math.sin(a1)));
							invMask.subtract(new Area(p));
						}
						mask.subtract(invMask);
					}
				}
			}
		} else {
			mask.subtract(mask);
		}
		Area invMask = new Area(new Rectangle(context.map.width, context.map.height));
		invMask.subtract(mask);

		// draw bg
		BufferedImage bg = rc.getImage(context.map.bgfile);
		g.drawImage(bg, 0, 0, null);

		// draw effects, lower level
		for (Effect effect : effects) {
			if (mask.contains(effect.x, effect.y))
				continue;
			if (effect.type != Effect.DESTROY)
				continue;
			BufferedImage ef = rc.getImage("effect_" + effect.type);
			g.drawImage(ef, (int) (effect.x - ef.getWidth() / 2), (int) (effect.y - ef.getHeight() / 2), null);
		}

		// focus on my tank
		if (myTank != null) {
			if (myTank.team == -1)
				g.setColor(Color.white);
			else
				g.setColor(TEAM_COLORS[myTank.team]);

			g.setStroke(new BasicStroke(2));
			g.drawOval((int) (myTank.x - Tank.RADIUS[myTank.type] * s2), (int) (myTank.y - Tank.RADIUS[myTank.type]
					* s2), (int) (Tank.RADIUS[myTank.type] * 2 * s2), (int) (Tank.RADIUS[myTank.type] * 2 * s2));
		}

		// shadow
		if (maskEnabled && myTank != null) {
			Color centerColor = myTank.team >= 0 ? TEAM_COLORS[myTank.team] : Color.white;
			Color middleColor = new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(), 0);

			g.setPaint(new RadialGradientPaint((int) myTank.x, (int) myTank.y, VIEW_RANGE * 2, new float[] { 0, 0.08f,
					1.0f }, new Color[] { centerColor, middleColor, new Color(0, 0, 0, 128) }));
			g.fill(invMask);
		}

		// path
		if (myTank != null && movePath.size() > 0) {
			Polygon poly = new Polygon();
			poly.addPoint((int) myTank.x, (int) myTank.y);
			for (Point2D p : movePath)
				poly.addPoint((int) p.getX(), (int) p.getY());
			g.setColor(Color.darkGray);
			g.drawPolyline(poly.xpoints, poly.ypoints, poly.npoints);
		}

		// draw line
		if (myTank != null) {
			double dis = 400;
			Line2D ll = new Line2D.Double(myTank.x, myTank.y, myTank.x + dis * Math.cos(myTank.gunAngle - Math.PI / 2),
					myTank.y + dis * Math.sin(myTank.gunAngle - Math.PI / 2));
			for (Line2D l : context.map.obstacles) {
				if (l.intersectsLine(ll)) {
					Point2D p = Geom.getIntersectionPoint(l, ll);
					double tdis = Point2D.distance(myTank.x, myTank.y, p.getX(), p.getY());
					if (tdis < dis)
						dis = tdis;
				}
			}
			g.setColor(new Color(192, 192, 192, 192));
			g.drawLine((int) myTank.x, (int) myTank.y,
					(int) (myTank.x + dis * Math.cos(myTank.gunAngle - Math.PI / 2)),
					(int) (myTank.y + dis * Math.sin(myTank.gunAngle - Math.PI / 2)));
		}

		// draw tanks
		for (Tank tank : context.tanks.values()) {
			if (mask.contains(tank.x, tank.y))
				continue;
			BufferedImage t = rc.getImage("tank_" + tank.type);
			BufferedImage gun = rc.getImage("gun_" + tank.gunType);
			// bottom circle
			if (tank.team == -1)
				g.setColor(Color.white);
			else
				g.setColor(TEAM_COLORS[tank.team]);
			g.setStroke(new BasicStroke(2));
			g.drawOval((int) (tank.x - t.getWidth() / s2 + 2), (int) (tank.y - t.getHeight() / s2 + 2),
					(int) (t.getWidth() * s2) - 4, (int) (t.getHeight() * s2) - 4);

			// body
			AffineTransform at;
			at = new AffineTransform();
			at.translate(tank.x, tank.y);
			at.rotate(tank.angle);
			at.translate(-t.getWidth() / 2, -t.getHeight() / 2);
			g.drawImage(t, at, null);

			// gun
			at = new AffineTransform();
			at.translate(tank.x, tank.y);
			at.rotate(tank.gunAngle);
			at.translate(-gun.getWidth() / 2, -gun.getHeight() / 2);
			g.drawImage(gun, at, null);

			// name
			g.setColor(Color.blue);
			String name = "[" + (tank.team >= 0 ? tank.team : "Free") + "]" + tank.name;
			g.drawString(name, tank.x - (int) g.getFontMetrics().getStringBounds(name, g).getWidth() / 2, tank.y
					- (int) (t.getHeight() / s2));

			// life
			g.setColor(Color.white);
			g.fillRect((int) (tank.x - t.getWidth() / 2), (int) (tank.y + t.getHeight() / 2), tank.life * t.getWidth()
					/ 1000, 5);
			if (tank.life > 600)
				g.setColor(Color.blue);
			else if (tank.life > 300)
				g.setColor(Color.yellow);
			else
				g.setColor(Color.red);
			g.fillRect((int) (tank.x - t.getWidth() / 2) + 1, (int) (tank.y + t.getHeight() / 2) + 1,
					tank.life * t.getWidth() / 1000 - 2, 3);

		}

		// target on tank
		for (Tank t : context.tanks.values()) {
			if (t == myTank)
				continue;
			if (myTank != null && myTank.team != -1 && myTank.team == t.team)
				continue;
			if (Point2D.distance(t.x, t.y, mouseX, mouseY) <= Tank.RADIUS[t.type]) {
				g.setColor(Color.red);
				g.setStroke(new BasicStroke(2));
				g.drawOval((int) (t.x - Tank.RADIUS[t.type] * s2), (int) (t.y - Tank.RADIUS[t.type] * s2),
						(int) (Tank.RADIUS[t.type] * 2 * s2), (int) (Tank.RADIUS[t.type] * 2 * s2));
			}
		}

		// draw bullets
		for (Bullet bullet : context.bullets.values()) {
			if (mask.contains(bullet.x, bullet.y))
				continue;
			BufferedImage b = rc.getImage("bullet_" + bullet.type);
			AffineTransform at;
			at = new AffineTransform();
			at.translate(bullet.x, bullet.y);
			at.rotate(bullet.angle);
			at.translate(-b.getWidth() / 2, -b.getHeight() / 2);
			g.drawImage(b, at, null);
		}

		// draw gifts
		for (Gift gift : context.gifts.values()) {
			if (mask.contains(gift.x, gift.y))
				continue;
			BufferedImage gg = rc.getImage("gift_" + gift.type);
			g.drawImage(gg, (int) (gift.x - gg.getWidth() / 2), (int) (gift.y - gg.getHeight() / 2), null);
		}

		// draw effects, higher level
		for (Effect effect : effects) {
			if (mask.contains(effect.x, effect.y))
				continue;
			if (effect.type == Effect.DESTROY)
				continue;
			BufferedImage ef = rc.getImage("effect_" + effect.type);
			g.drawImage(ef, (int) (effect.x - ef.getWidth() / 2), (int) (effect.y - ef.getHeight() / 2), null);
		}

		// draw mask
		if (maskEnabled && myTank != null) {
			g.setPaint(new RadialGradientPaint((int) myTank.x, (int) myTank.y, VIEW_RANGE, new float[] { 0, 1.0f },
					new Color[] { new Color(0, 0, 0, 255), new Color(0, 0, 0, 128) }));
			g.fill(mask);
		}
		// draw strings
		float height = 0;
		g.setColor(Color.blue);
		height += g.getFontMetrics().getLineMetrics("FPS", g).getHeight();
		g.drawString("FPS: " + (int) framesPerSecond, 0, (int) height);
		long currentTime = System.currentTimeMillis();
		while (displayMessages.size() > 0 && (currentTime - displayMessages.get(0).time > DISPLAY_MESSAGE_TIME)) {
			displayMessages.remove(0);
		}
		for (TimedMessage tm : displayMessages) {
			height += g.getFontMetrics().getLineMetrics(tm.msg, g).getHeight() + 5;
			if (DISPLAY_MESSAGE_TIME - (currentTime - tm.time) < 1000) {
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
						(float) (DISPLAY_MESSAGE_TIME - (currentTime - tm.time)) / 1000));
			} else {
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1.0f));
			}
			g.drawString(tm.msg, 0, height);
		}
	}

	private void processMessage(Context context, Message msg) {
		Log.v(TAG, msg.what, ":", msg.arg1, "-", msg.arg2, " ", msg.obj);

		switch (msg.what) {
		case CONNECT_SERVER:
			String[] tokens = ((String) msg.obj).split(":");
			if (tokens.length == 2) {
				int port = Integer.parseInt(tokens[1]);
				sendDisplayMessage("connecting to server " + tokens[0] + " port " + port);
				if (client != null)
					client.stop();
				context.clear();
				movePath.clear();
				try {
					recorder.initRecord();
				} catch (IOException e) {
					e.printStackTrace();
					sendDisplayMessage("Recorder init failed.");
					Log.e(TAG, "recorder init failed");
				}
				client = new Client(tokens[0], port);
				client.setGameLoop(this);
				client.start();
			}
			break;
		case DISCONNECT_SERVER:
			if (client != null)
				client.stop();
			context.clear();
			movePath.clear();
			try {
				recorder.stopRecord();
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.connected = false;
			break;
		case CONNECTED:
			this.connected = true;
			client.sendMessage("sync", (System.currentTimeMillis() - loopStartTime) / 10);
			client.sendMessage("get-status");
			break;
		case SERVER_MESSAGE:
			if (connected)
				processServerMessage(context, (String) msg.obj);
			break;
		case REPLAY_MESSAGE:
			if (!connected)
				processServerMessage(context, (String) msg.obj);
			break;
		case DISPLAY_MESSAGE:
			TimedMessage tm = new TimedMessage();
			tm.time = System.currentTimeMillis();
			tm.msg = (String) msg.obj;
			displayMessages.add(tm);
			if (displayMessages.size() > DISPLAY_MESSAGE_MAX_ITEM)
				displayMessages.remove(0);
			break;
		case ACTION_FIRE_ON:
		case ACTION_MOVE_ON:
		case ACTION_FIRE_OFF:
		case ACTION_MOVE_OFF:
		case MOUSE_MOVE:
			if (recorder.getState() == Recorder.PLAYING)
				return;
			mouseX = msg.arg1;
			mouseY = msg.arg2;
			if (msg.what == ACTION_FIRE_ON) {
				if (client != null) {
					if (context.getMyTank() == null)
						client.sendMessage(String.format("born %s %d %d 0 0", getTankName(), mouseX, mouseY));
					else
						this.firing = true;
				}
			} else if (msg.what == ACTION_FIRE_OFF) {
				this.firing = false;
			} else if (msg.what == ACTION_MOVE_ON) {
				this.moving = true;
			} else if (msg.what == ACTION_MOVE_OFF) {
				this.moving = false;
			}
			break;
		case KEY_DOWN:
			if (msg.arg1 == PATH_KEY && !pathKeyDown) {
				sendDisplayMessage("enter path mode");
				pathKeyDown = true;
				movePath.clear();
				Tank t = context.getMyTank();
				if (t != null && t.remainedMoveTime > 0)
					movePath.add(new Point2D.Float(t.targetX, t.targetY));
			}
			break;
		case KEY_UP:
			if (msg.arg1 == PATH_KEY) {
				sendDisplayMessage("leave path mode");
				if (client != null && movePath.size() > 0) {
					Point2D p = movePath.get(0);
					client.sendMessage("move-to", p.getX(), p.getY());
				}
				pathKeyDown = false;
			}
			break;
		case CHAT_MESSAGE:
			if (connected)
				client.sendMessage("chat " + Base64String.encode(msg.obj.toString()));
			break;
		case CHANGE_TEAM:
			if (connected) {
				int team = msg.arg1;
				client.sendMessage(String.format("team %d", team));
			}
			break;
		case FORCE_SYNC:
			context.sync((Long) msg.obj - System.currentTimeMillis() / 10);
			break;
		default:
		}
	}

	private void processServerMessage(Context context, String msg) {
		ResourceLoader rc = ResourceLoader.getInstance();
		if (recorder != null)
			recorder.record(msg);
		if (msg == null)
			return;
		try {
			String tokens[] = msg.split(" ");
			long timestamp = Long.parseLong(tokens[0]);
			if ("set-id".equals(tokens[1])) {
				int id = Integer.parseInt(tokens[2]);
				context.myTankId = id;
			} else if ("load".equals(tokens[1])) {
				String map = tokens[2];
				TanxMap.loadMap(context, map, panel);
			} else if ("sync".equals(tokens[1])) {
				long time = Long.parseLong(tokens[2]) + loopStartTime / 10;
				long localTime = System.currentTimeMillis() / 10;
				long lag = (localTime - time) / 2;

				context.sync(timestamp - time - lag);
			} else if ("create".equals(tokens[1])) {
				if ("tank".equals(tokens[2])) {
					int id = Integer.parseInt(tokens[3]);
					String name = tokens[4];
					int life = Integer.parseInt(tokens[7]);
					float x = Float.parseFloat(tokens[9]);
					float y = Float.parseFloat(tokens[10]);
					float tankAngle = Float.parseFloat(tokens[11]);
					float gunAngle = Float.parseFloat(tokens[12]);
					Tank tank = new Tank();
					tank.life = life;
					tank.name = name;
					tank.x = x;
					tank.y = y;
					tank.angle = tankAngle;
					tank.gunAngle = gunAngle;
					context.tanks.put(id, tank);

					rc.playSound("born");
				} else if ("bullet".equals(tokens[2])) {
					// create bullet ID Type OwnerID X Y Angle
					int id = Integer.parseInt(tokens[3]);
					int type = Integer.parseInt(tokens[4]);
					int owner = Integer.parseInt(tokens[5]);
					float x = Float.parseFloat(tokens[6]);
					float y = Float.parseFloat(tokens[7]);
					float angle = Float.parseFloat(tokens[8]);
					Bullet b = new Bullet();
					b.angle = angle;
					b.x = x;
					b.y = y;
					b.owner = owner;
					b.type = type;
					context.bullets.put(id, b);

					rc.playSound("gun_" + type);
				} else if ("gift".equals(tokens[2])) {
					// create gift ID Type X Y
					int id = Integer.parseInt(tokens[3]);
					int type = Integer.parseInt(tokens[4]);
					float x = Float.parseFloat(tokens[5]);
					float y = Float.parseFloat(tokens[6]);
					Gift g = new Gift();
					g.x = x;
					g.y = y;
					g.type = type;
					context.gifts.put(id, g);
				}
			} else if ("move".equals(tokens[1])) {
				if ("tank".equals(tokens[2])) {
					int id = Integer.parseInt(tokens[3]);
					float x = Float.parseFloat(tokens[4]);
					float y = Float.parseFloat(tokens[5]);
					float tx = Float.parseFloat(tokens[6]);
					float ty = Float.parseFloat(tokens[7]);
					int time = Integer.parseInt(tokens[8].trim());
					Tank t = context.tanks.get(id);
					if (t != null) {
						t.x = x;
						t.y = y;
						t.targetX = tx;
						t.targetY = ty;
						t.startMoveTime = timestamp;
						t.remainedMoveTime = time;
					}
				} else if ("bullet".equals(tokens[2])) {
					int id = Integer.parseInt(tokens[3]);
					float x = Float.parseFloat(tokens[4]);
					float y = Float.parseFloat(tokens[5]);
					float tx = Float.parseFloat(tokens[6]);
					float ty = Float.parseFloat(tokens[7]);
					int time = Integer.parseInt(tokens[8].trim());
					Bullet t = context.bullets.get(id);
					if (t != null) {
						t.x = x;
						t.y = y;
						t.targetX = tx;
						t.targetY = ty;
						t.startMoveTime = timestamp;
						t.remainedMoveTime = time;
					}
				}
			} else if ("rotate-gun".equals(tokens[1])) {
				int id = Integer.parseInt(tokens[3]);
				double startRad = Double.parseDouble(tokens[4]);
				double endRad = Double.parseDouble(tokens[5]);
				int time = Integer.parseInt(tokens[6].trim());

				Tank t = context.tanks.get(id);
				t.startGunRotateTime = timestamp;
				t.remainedGunRotateTime = time;
				t.gunAngle = startRad;
				t.targetGunAngle = endRad;
			} else if ("rotate-tank".equals(tokens[1])) {
				int id = Integer.parseInt(tokens[3]);
				double startRad = Double.parseDouble(tokens[4]);
				double endRad = Double.parseDouble(tokens[5]);
				int time = Integer.parseInt(tokens[6].trim());

				Tank t = context.tanks.get(id);
				t.startRotateTime = timestamp;
				t.remainedRotateTime = time;
				t.angle = startRad;
				t.targetAngle = endRad;
			} else if ("destroy".equals(tokens[1])) {
				int id = Integer.parseInt(tokens[3]);
				if ("tank".equals(tokens[2])) {
					Tank t = context.tanks.remove(id);

					Effect e = new Effect();
					e.type = Effect.DESTROY;
					e.x = t.x;
					e.y = t.y;
					e.remainedTime = Effect.DURATION[e.type];
					effects.add(e);

					rc.playSound("explosion");
				} else if ("bullet".equals(tokens[2])) {
					Bullet b = context.bullets.remove(id);

					Effect e = new Effect();
					e.type = Effect.HIT;
					e.x = b.x;
					e.y = b.y;
					e.remainedTime = Effect.DURATION[e.type];
					effects.add(e);

				} else if ("gift".equals(tokens[2])) {
					context.gifts.remove(id);
				}
			} else if ("chat".equals(tokens[1])) {
				int id = Integer.parseInt(tokens[2]);
				String chatMsg = Base64String.decode(tokens[3]);
				sendDisplayMessage(context.tanks.get(id).name + ": " + chatMsg);

				rc.playSound("message");
			} else if ("set-life".equals(tokens[1])) {
				// set-life tank ID life
				int id = Integer.parseInt(tokens[3]);
				int life = Integer.parseInt(tokens[4]);
				boolean hit = context.tanks.get(id).life > life;
				context.tanks.get(id).life = life;
				if (hit)
					rc.playSound("hit");
				else
					rc.playSound("hpup");
			} else if ("set-team".equals(tokens[1])) {
				// set-team tank %d team %d
				int id = Integer.parseInt(tokens[3]);
				int team = Integer.parseInt(tokens[5]);
				Tank t = context.tanks.get(id);
				t.team = team;
			}

		} catch (Exception e) {
			sendDisplayMessage("ERROR: " + msg);
			e.printStackTrace();
		}
	}

	public void setTankName(String myTankName) {
		this.myTankName = myTankName;
	}

	public String getTankName() {
		return myTankName;
	}

	public Recorder getRecorder() {
		return recorder;
	}

	public void setMaskEnabled(boolean maskEnabled) {
		this.maskEnabled = maskEnabled;
	}

	public boolean isMaskEnabled() {
		return maskEnabled;
	}
}
