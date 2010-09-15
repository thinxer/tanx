package com.thinxer.tanx.server;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.thinxer.tanx.model.Bullet;
import com.thinxer.tanx.model.Context;
import com.thinxer.tanx.model.Gift;
import com.thinxer.tanx.model.Tank;
import com.thinxer.tanx.model.TanxMap;
import com.thinxer.tanx.server.blockingserver.BlockingServer;
import com.thinxer.tanx.util.Geom;
import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.Message;
import com.thinxer.tanx.util.Radian;

public class ServerLogic implements Runnable {
	private static final String TAG = "Logic";
	private static final int MAX_TEAM_NUMBER = 8;
	private static final int MAX_GIFT_NUMBER = 3;
	private Server server;
	private Context context;
	private BlockingQueue<Message> messages;
	private boolean running;
	private Thread thread;

	private int bulletId;
	private Set<Integer> bornedTanks;
	private Map<Integer, Integer> tankTeams;

	private int giftId;
	private long lastGiftCheckTime;

	public ServerLogic(Server server) {
		this.server = server;
		this.context = new Context();
		this.messages = new LinkedBlockingQueue<Message>();

		this.bulletId = 0;
		this.giftId = 0;
		this.bornedTanks = new HashSet<Integer>();
		this.tankTeams = new HashMap<Integer, Integer>();
	}

	public void putMessage(int id, String message) {
		Message msg = new Message();
		msg.what = id;
		msg.obj = message;
		this.messages.offer(msg);
	}

	public void start() {
		this.running = true;
		this.thread = new Thread(this);
		this.thread.start();
	}

	public void stop() {
		this.running = false;
		this.thread.interrupt();
	}

	@Override
	public void run() {
		while (running) {
			Message msg;
			msg = messages.poll();
			context.makeMove();
			check();
			giveGifts();
			if (msg != null)
				processMessage(msg);
		}

	}

	private void processMessage(Message msg) {
		try {
			long currentTime = server.getTime();
			context.sync(currentTime - System.currentTimeMillis() / 10);
			int id = msg.what;
			String message = (String) msg.obj;
			Tank tank = context.tanks.get(id);
			Log.v(TAG, msg.toString());
			if (message == null)
				return;
			String[] tokens = message.split(" ");
			if ("init".equals(tokens[0])) {
				server.sendMessage(id, "set-id", id);
				server.sendMessage(id, "load", context.map.mapfile);
			} else if ("get-status".equals(tokens[0])) {
				for (int tid : context.tanks.keySet()) {
					Tank t = context.tanks.get(tid);
					if (t == null || t.life <= 0)
						continue;
					server.sendMessage(id, "create tank", tid, t.name, 0, 0, t.life, t.mana, t.x, t.y, t.angle, t.gunAngle);
					server.sendMessage(id, "set-team tank", tid, "team", t.team);

					if (t.remainedMoveTime >= 0)
						server.sendMessage(id, "move tank", tid, t.x, t.y, t.targetX, t.targetY, t.remainedMoveTime);

					if (t.remainedGunRotateTime >= 0)
						server.sendMessage(id, "rotate-gun tank", tid, t.gunAngle, t.targetGunAngle,
								t.remainedGunRotateTime);

					if (t.remainedRotateTime >= 0)
						server.sendMessage(id, "rotate-tank tank", tid, t.angle, t.targetAngle, t.remainedRotateTime);

				}
				for (int bid : context.bullets.keySet()) {
					Bullet b = context.bullets.get(bid);
					server.sendMessage(id, "create bullet", bid, 0, b.owner, b.x, b.y, b.angle);
					server.sendMessage(id, "move bullet", bid, b.x, b.y, b.targetX, b.targetY, b.remainedMoveTime);
				}
				for (int gid : context.gifts.keySet()) {
					Gift g = context.gifts.get(gid);
					server.sendMessage(id, "create gift", gid, g.type, g.x, g.y);
				}
			} else if ("leave".equals(tokens[0])) {
				if (context.tanks.containsKey(id)) {
					server.sendMessage(BlockingServer.ALL_CLIENTS, "destroy tank", id);
					context.tanks.remove(id);
				}
			} else if ("sync".equals(tokens[0])) {
				// sync TIME
				if (tokens.length != 2)
					return;
				// sync TIME
				server.sendMessage(id, "sync", tokens[1]);
			} else if ("born".equals(tokens[0])) {
				// born NAME X Y TankType GunType
				if (!bornedTanks.contains(id)) {
					// create tank ID NAME TankType GunType Life Mana X Y
					tank = new Tank();
					// tank = context.tanks.get(id);
					tank.name = tokens[1];
					tank.x = Float.parseFloat(tokens[2]);
					tank.y = Float.parseFloat(tokens[3]);
					tank.life = 1000;

					if (tankTeams.get(id) != null)
						tank.team = tankTeams.get(id);
					else
						tank.team = -1;

					for (Tank t : context.tanks.values())
						if (Point2D.distance(t.x, t.y, tank.x, tank.y) <= 20.0)
							return;
					for (Line2D.Float line : context.map.obstacles)
						if (line.ptSegDist(tank.x, tank.y) < 20.0)
							return;

					context.tanks.put(id, tank);
					bornedTanks.add(id);
					server.sendMessage(BlockingServer.ALL_CLIENTS, "create tank", id, tank.name, 0, 0, tank.life, 0,
							tank.x, tank.y, 0, 0);
					server.sendMessage(BlockingServer.ALL_CLIENTS, "set-team", "tank", id, "team", tank.team);
				}
			} else if ("move-to".equals(tokens[0])) {
				// move-to X Y
				// move tank/bullet ID SX SY EX EY Cost
				// rotate-tank tank ID SAngle EAngle Cost
				if (tank != null) {
					double x = Double.parseDouble(tokens[1]) - tank.x;
					double y = Double.parseDouble(tokens[2]) - tank.y;
					double a = Radian.round(Math.atan2(y, x) + Math.PI / 2);

					// rotate-gun tank ID SAngle EAngle Cost
					tank.targetAngle = a;
					double diff = Radian.getDiff(tank.angle, tank.targetAngle);
					tank.startRotateTime = currentTime;
					tank.remainedRotateTime = (int) (diff / Math.PI * 100);
					server.sendMessage(BlockingServer.ALL_CLIENTS, "rotate-tank tank " + id + " " + tank.angle + " "
							+ tank.targetAngle + " " + tank.remainedRotateTime);

					tank.targetX = Float.parseFloat(tokens[1]);
					tank.targetY = Float.parseFloat(tokens[2]);
					tank.startMoveTime = currentTime + tank.remainedRotateTime;
					tank.remainedMoveTime = (int) (Point2D.distance(tank.x, tank.y, tank.targetX, tank.targetY));
					if (tank != null) {
						server.sendMessage(currentTime + tank.remainedRotateTime, BlockingServer.ALL_CLIENTS,
								"move tank", id, tank.x, tank.y, tank.targetX, tank.targetY, tank.remainedMoveTime);
					}
				}
			} else if ("rotate-to".equals(tokens[0])) {
				// rotate-to X Y
				if (tank != null) {
					double x = Double.parseDouble(tokens[1]) - tank.x;
					double y = Double.parseDouble(tokens[2]) - tank.y;
					double a = Radian.round(Math.atan2(y, x) + Math.PI / 2);
				
					// rotate-gun tank ID SAngle EAngle Cost
					tank.targetGunAngle = a;
					double diff = Radian.getDiff(tank.gunAngle, tank.targetGunAngle);
					tank.startGunRotateTime = currentTime;
					tank.remainedGunRotateTime = (int) (diff / Math.PI * 100);
					server.sendMessage(Server.ALL_CLIENTS, "rotate-gun", "tank", id, tank.gunAngle,
							tank.targetGunAngle, tank.remainedGunRotateTime);
				}
			} else if ("chat".equals(tokens[0])) {
				if (tank != null)
					server.sendMessage(Server.ALL_CLIENTS, "chat", id, tokens[1]);
			} else if ("shoot".equals(tokens[0])) {
				// shoot
				// create bullet..
				if (tank != null) {
					if (tank.shootCoolDown >= 0)
						return;
					tank.shootCoolDown = Tank.SHOOT_COOLDOWN[tank.type];
					context.bullets.put(bulletId, new Bullet());
					Bullet b = context.bullets.get(bulletId);
					b.owner = id;
					b.angle = tank.gunAngle;
					b.x = (float) (tank.x + Tank.GUN_RADIUS[tank.gunType] * Math.cos(b.angle - Math.PI / 2));
					b.y = (float) (tank.y + Tank.GUN_RADIUS[tank.gunType] * Math.sin(b.angle - Math.PI / 2));
					b.startMoveTime = currentTime;
					double dis = 400;
					b.targetX = (float) (b.x + dis * Math.cos(b.angle - Math.PI / 2));
					b.targetY = (float) (b.y + dis * Math.sin(b.angle - Math.PI / 2));
					Line2D ll = new Line2D.Float(b.x, b.y, b.targetX, b.targetY);
					for (Line2D l : context.map.obstacles) {
						if (l.intersectsLine(ll)) {
							Point2D p = Geom.getIntersectionPoint(l, ll);
							double tdis = Point2D.distance(b.x, b.y, p.getX(), p.getY());
							if (tdis < dis)
								dis = tdis;
						}
					}
					b.targetX = (float) (b.x + dis * Math.cos(b.angle - Math.PI / 2));
					b.targetY = (float) (b.y + dis * Math.sin(b.angle - Math.PI / 2));
					b.remainedMoveTime = (int) (dis / 1.5);
					server.sendMessage(Server.ALL_CLIENTS, "create", "bullet", bulletId, 0, id, b.x, b.y, b.angle);
					server.sendMessage(Server.ALL_CLIENTS, "move", "bullet", bulletId, b.x, b.y, b.targetX, b.targetY,
							b.remainedMoveTime);
					bulletId++;
				}
			} else if ("team".equals(tokens[0])) {
				int tid = Integer.parseInt(tokens[1]);
				tankTeams.put(id, tid);

				if (tank != null) {
					tank.team = tid;
					if (tank.team >= MAX_TEAM_NUMBER || tank.team < -1)
						tank.team = -1;
					server.sendMessage(Server.ALL_CLIENTS, "set-team", "tank", id, "team", tank.team);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "error processing: " + msg);
		}
	}

	private void check() {
		Iterator<Integer> itb = context.bullets.keySet().iterator();
		while (itb.hasNext()) {
			int bid = itb.next();
			Bullet b = context.bullets.get(bid);
			Tank owner = context.tanks.get(b.owner);
			for (int tid : context.tanks.keySet()) {
				if (b.owner == tid)
					continue;
				Tank t = context.tanks.get(tid);
				if (t.team != -1 && (owner != null && t.team == owner.team))
					continue;
				if (Point2D.distance(b.x, b.y, t.x, t.y) < Tank.RADIUS[t.type]) {
					t.life -= Bullet.DAMAGES[b.type];
					server.sendMessage(Server.ALL_CLIENTS, "set-life tank", tid, t.life);
					b.remainedMoveTime = -1;
				}
			}
			if (b.remainedMoveTime < 0) {
				itb.remove();
				server.sendMessage(Server.ALL_CLIENTS, "destroy bullet", bid);
			}
		}

		Iterator<Integer> itt = context.tanks.keySet().iterator();
		while (itt.hasNext()) {
			int tid = itt.next();
			Tank t = context.tanks.get(tid);
			if (t.life <= 0) {
				itt.remove();
				server.sendMessage(Server.ALL_CLIENTS, "destroy tank", tid);
			} else if (t.remainedMoveTime >= 0) {
				// check gifts
				Iterator<Integer> ig = context.gifts.keySet().iterator();
				while (ig.hasNext()) {
					int gid = ig.next();
					Gift g = context.gifts.get(gid);
					if (Point2D.distance(t.x, t.y, g.x, g.y) < Tank.RADIUS[t.type] + Gift.RADIUS[g.type]) {
						ig.remove();
						server.sendMessage(Server.ALL_CLIENTS, "destroy gift", gid);
						switch (g.type) {
						case Gift.HPUP100:
							t.life += 100;
							if (t.life > Tank.MAX_LIFE[t.type])
								t.life = Tank.MAX_LIFE[t.type];
							server.sendMessage(Server.ALL_CLIENTS, "set-life tank", tid, t.life);
							break;
						}
					}
				}
				// check tank collisions
				for (Tank tt : context.tanks.values()) {
					if (t == tt)
						continue;
					if (Point2D.distance(t.x, t.y, tt.x, tt.y) < Tank.RADIUS[t.type] + Tank.RADIUS[tt.type]) {
						if (Math.abs(Radian.round(Math.atan2(tt.y - t.y, tt.x - t.x) + Math.PI / 2 - t.angle)) < Math.PI / 3) {
							t.remainedMoveTime = 0;
							t.targetX = t.x;
							t.targetY = t.y;
							server.sendMessage(Server.ALL_CLIENTS, "move tank", tid, t.x, t.y, t.targetX, t.targetY, 0);
						}
					}
				}
				// check obstacles
				for (Line2D l : context.map.obstacles) {
					double d1 = l.ptSegDist(t.x, t.y);
					if (d1 <= Tank.RADIUS[t.type]) {
						double d2 = l.ptSegDist(t.x + 4 * Math.cos(t.targetAngle - Math.PI / 2),
								t.y + 4 * Math.sin(t.targetAngle - Math.PI / 2));
						if (d2 < d1) {
							t.remainedMoveTime = 0;
							t.targetX = t.x;
							t.targetY = t.y;
							server.sendMessage(Server.ALL_CLIENTS, "move tank", tid, t.x, t.y, t.targetX, t.targetY, 0);
						}
					}
				}

			}
		}
	}

	private void giveGifts() {
		if (lastGiftCheckTime <= 0)
			lastGiftCheckTime = server.getTime();
		long curTime = server.getTime();
		if (context.gifts.size() < MAX_GIFT_NUMBER && curTime - lastGiftCheckTime >= 100) {
			int rnd = (int) (Math.random() * 1000);
			if (rnd >= 950) {
				Gift g = new Gift();
				g.type = Gift.HPUP100;
				g.x = (int) (context.map.width * Math.random());
				g.y = (int) (context.map.height * Math.random());
				context.gifts.put(giftId, g);
				server.sendMessage(Server.ALL_CLIENTS, "create gift", giftId, g.type, g.x, g.y);
				giftId++;
			}
			lastGiftCheckTime = curTime;
		}
	}

	public void loadMap(String map) throws IOException {

		for (int tid : context.tanks.keySet())
			server.sendMessage(Server.ALL_CLIENTS, "destroy tank", tid);
		for (int bid : context.bullets.keySet())
			server.sendMessage(Server.ALL_CLIENTS, "destroy bullet", bid);

		context.clear();
		bornedTanks.clear();

		TanxMap.loadMap(context, map, null);
	}

	public String getMap() {
		return context.map.mapfile;
	}

}
