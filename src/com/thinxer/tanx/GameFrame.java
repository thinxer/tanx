package com.thinxer.tanx;

import java.awt.BorderLayout;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import com.thinxer.tanx.client.GameLoop;
import com.thinxer.tanx.server.Server;
import com.thinxer.tanx.server.blockingserver.BlockingServer;
import com.thinxer.tanx.util.Log;
import com.thinxer.tanx.util.Message;

public class GameFrame extends Frame implements MouseListener, MouseMotionListener, KeyListener {
	private static final long serialVersionUID = -270017114405696433L;

	private static final String TAG = "GameFrame";

	private Panel mainPanel;
	private TextField chatText;
	private GameLoop gl;
	private Server server;
	private String lastServer = "166.111.68.68:29995";

	private JFileChooser fc;

	public Panel getMainPanel() {
		return mainPanel;
	}

	public GameFrame() throws HeadlessException {
		super("tanX");

		Log.v(TAG, "initializing");

		mainPanel = new Panel();
		mainPanel.setIgnoreRepaint(true);
		mainPanel.setPreferredSize(new Dimension(600, 400));
		mainPanel.addKeyListener(this);
		mainPanel.addMouseListener(this);
		mainPanel.addMouseMotionListener(this);
		mainPanel.addComponentListener(new ComponentListener() {

			@Override
			public void componentHidden(ComponentEvent e) {
			}

			@Override
			public void componentMoved(ComponentEvent e) {
			}

			@Override
			public void componentResized(ComponentEvent e) {
				Log.v(TAG, "resized");
				GameFrame.this.pack();
			}

			@Override
			public void componentShown(ComponentEvent e) {
			}

		});
		add(mainPanel, BorderLayout.CENTER);

		final MenuBar menuBar = new MenuBar();
		final Menu gameMenu = new Menu("Game");
		final Menu teamMenu = new Menu("Team");
		final Menu maskMenu = new Menu("Mask");
		final Menu replayMenu = new Menu("Replay");
		final Menu serverMenu = new Menu("Server");
		final Menu mapMenu = new Menu("Map");

		final MenuItem connect = new MenuItem("Connect");
		final MenuItem connectLocal = new MenuItem("Connect to Local Server");
		final MenuItem changeName = new MenuItem("Change Name");
		final MenuItem exit = new MenuItem("Exit");
		gameMenu.add(connect);
		gameMenu.add(connectLocal);
		gameMenu.addSeparator();
		gameMenu.add(changeName);
		gameMenu.addSeparator();
		gameMenu.add(exit);

		ItemListener il = new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				CheckboxMenuItem source = (CheckboxMenuItem) e.getSource();
				if (gl != null) {
					Message msg = new Message();
					msg.what = GameLoop.CHANGE_TEAM;
					msg.arg1 = Integer.parseInt(source.getActionCommand());
					gl.sendMessage(msg);
				}
				for (int i = 0; i < teamMenu.getItemCount(); i++) {
					CheckboxMenuItem item = (CheckboxMenuItem) teamMenu.getItem(i);
					if (item != null)
						item.setState(false);
				}
				source.setState(true);
			}

		};
		CheckboxMenuItem teamFree = new CheckboxMenuItem("Team Free");
		teamFree.setState(true);
		teamFree.setActionCommand("-1");
		teamFree.addItemListener(il);
		teamMenu.add(teamFree);
		for (int i = 0; i < 8; i++) {
			CheckboxMenuItem teamx = new CheckboxMenuItem("Team " + i);
			teamx.setActionCommand("" + i);
			teamx.addItemListener(il);
			teamMenu.add(teamx);
		}

		final MenuItem saveReplay = new MenuItem("Save");
		final MenuItem openReplay = new MenuItem("Open");
		final MenuItem lastReplay = new MenuItem("Open Last Battle");
		final MenuItem stopReplay = new MenuItem("Stop");
		replayMenu.add(saveReplay);
		replayMenu.add(openReplay);
		replayMenu.add(lastReplay);
		replayMenu.add(stopReplay);

		final CheckboxMenuItem enableMask = new CheckboxMenuItem("Enable");
		final CheckboxMenuItem disableMask = new CheckboxMenuItem("Disable");
		maskMenu.add(enableMask);
		maskMenu.add(disableMask);

		ActionListener mapListener = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (server != null) {
					try {
						server.loadMap(e.getActionCommand());
					} catch (IOException e1) {
						gl.sendDisplayMessage("Error loading map: " + e.getActionCommand());
					}
				}
			}
			
		};
		
		String[] maps = { "simple.txt", "startup.txt" };
		for (String m : maps) {
			MenuItem mapItem = new MenuItem(m);
			mapMenu.add(mapItem);
			mapItem.addActionListener(mapListener);
		}

		final MenuItem startServer = new MenuItem("Start Server");
		final MenuItem stopServer = new MenuItem("Stop Server");
		serverMenu.add(startServer);
		serverMenu.add(stopServer);
		serverMenu.add(mapMenu);

		ItemListener maskListener = new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if ((enableMask.getState() && e.getSource() == enableMask) || (e.getSource() == disableMask && !disableMask.getState())) {
					enableMask.setState(true);
					disableMask.setState(false);
					gl.setMaskEnabled(true);
				} else {
					enableMask.setState(false);
					disableMask.setState(true);
					gl.setMaskEnabled(false);
				}
			}

		};
		enableMask.addItemListener(maskListener);
		disableMask.addItemListener(maskListener);
		enableMask.setState(true);

		menuBar.add(gameMenu);
		menuBar.add(teamMenu);
		menuBar.add(maskMenu);
		menuBar.add(replayMenu);
		menuBar.add(serverMenu);
		this.setMenuBar(menuBar);

		ActionListener al = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (connect == e.getSource()) {
					String server = JOptionPane.showInputDialog("Server Address:", lastServer);
					if (server != null) {
						lastServer = server;
						Message msg = new Message();
						msg.what = GameLoop.CONNECT_SERVER;
						msg.obj = lastServer;
						gl.sendMessage(msg);
					}
				} else if (connectLocal == e.getSource()) {
					Message msg = new Message();
					msg.what = GameLoop.CONNECT_SERVER;
					msg.obj = "localhost:7654";
					gl.sendMessage(msg);
				} else if (changeName == e.getSource()) {
					String name = JOptionPane.showInputDialog("New Name", gl.getTankName());
					if (name != null) {
						gl.setTankName(name);
					}
				} else if (saveReplay == e.getSource()) {
					if (fc.showSaveDialog(GameFrame.this) == JFileChooser.APPROVE_OPTION) {
						try {
							gl.getRecorder().saveAs(fc.getSelectedFile().getAbsolutePath());
							gl.sendDisplayMessage("Replay saved.");
						} catch (IOException e1) {
							e1.printStackTrace();
							Log.e(TAG, "error saving replay");
							gl.sendDisplayMessage("Error saving replay!");
						}
					}
				} else if (openReplay == e.getSource()) {
					if (fc.showOpenDialog(GameFrame.this) == JFileChooser.APPROVE_OPTION) {
						try {
							gl.getRecorder().play(fc.getSelectedFile().getAbsolutePath());
						} catch (IOException e1) {
							e1.printStackTrace();
							Log.e(TAG, "error opening replay");
							gl.sendDisplayMessage("Error opening replay!");
						}
					}
				} else if (lastReplay == e.getSource()) {
					try {
						gl.getRecorder().playLast();
					} catch (IOException e1) {
						e1.printStackTrace();
						Log.e(TAG, "error opening replay");
						gl.sendDisplayMessage("Error opening replay!");
					}
				} else if (stopReplay == e.getSource()) {
					try {
						gl.getRecorder().stopPlay();
					} catch (IOException e1) {
						e1.printStackTrace();
						gl.sendDisplayMessage("Error stopping replay!");
					}
				} else if (exit == e.getSource()) {
					if (gl != null)
						gl.stop();
					GameFrame.this.dispose();
				} else if (startServer == e.getSource()) {
					if (server == null)
						server = new BlockingServer(7654);
					else
						server.stop();
					server.start();
					try {
						server.loadMap("simple.txt");
					} catch (IOException e1) {
						e1.printStackTrace();
						gl.sendDisplayMessage("Error loading server map: simple.txt");
					}
					// XXX add a server callback....
					if (server.getListeningPort() != -1)
						gl.sendDisplayMessage("Listening on port: " + server.getListeningPort());
					else {
						new Thread(new Runnable() {
							public void run() {
								try {
									Thread.sleep(1000);
									if (server.getListeningPort() != -1) {
										gl.sendDisplayMessage("Listening on port: " + server.getListeningPort());
										return;
									}
									Thread.sleep(2000);
									if (server.getListeningPort() != -1) {
										gl.sendDisplayMessage("Listening on port: " + server.getListeningPort());
										return;
									}
									gl.sendDisplayMessage("Cannot start server");
								} catch (InterruptedException e) {
								}
							}
						}).start();
					}
				} else if (stopServer == e.getSource()) {
					if (server != null)
						server.stop();
				}
			}

		};
		connect.addActionListener(al);
		connectLocal.addActionListener(al);
		changeName.addActionListener(al);
		exit.addActionListener(al);
		saveReplay.addActionListener(al);
		openReplay.addActionListener(al);
		lastReplay.addActionListener(al);
		stopReplay.addActionListener(al);
		startServer.addActionListener(al);
		stopServer.addActionListener(al);

		chatText = new TextField();
		KeyListener kl = new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (GameFrame.this.isVisible() && e.getKeyCode() == KeyEvent.VK_ENTER) {
					Message msg = new Message();
					msg.what = GameLoop.CHAT_MESSAGE;
					String str = chatText.getText();
					msg.obj = str;
					if (gl != null && str.length() > 0)
						gl.sendMessage(msg);
					chatText.setText("");
					GameFrame.this.getMainPanel().requestFocus();
				}
			}

			@Override
			public void keyTyped(KeyEvent e) {
			}

		};
		chatText.addKeyListener(kl);
		this.add(chatText, BorderLayout.SOUTH);

		pack();
		mainPanel.requestFocus();

		// on close
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosed(WindowEvent e) {
				super.windowClosed(e);
				if (gl != null)
					gl.stop();
				if (server != null)
					server.stop();
			}

			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				dispose();
			}

		});

		// file chooser
		FileFilter tanxFileFilter = new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.getName().endsWith(".tanxrep") || f.isDirectory();
			}

			@Override
			public String getDescription() {
				return "Tanx Replay";
			}

		};
		this.fc = new JFileChooser();
		this.fc.setFileFilter(tanxFileFilter);
	}

	public void setGameLoop(GameLoop gl) {
		this.gl = gl;
	}

	public GameLoop getGameLoop() {
		return gl;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	private int lastButton = GameLoop.ACTION_MOVE_ON;

	@Override
	public void mousePressed(MouseEvent e) {
		Message message = new Message();
		message.what = e.getButton() == MouseEvent.BUTTON1 ? GameLoop.ACTION_FIRE_ON : GameLoop.ACTION_MOVE_ON;
		lastButton = message.what;
		message.arg1 = e.getX();
		message.arg2 = e.getY();
		gl.sendMessage(message);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		Message message = new Message();
		message.what = e.getButton() == MouseEvent.BUTTON1 ? GameLoop.ACTION_FIRE_OFF : GameLoop.ACTION_MOVE_OFF;
		lastButton = message.what;
		message.arg1 = e.getX();
		message.arg2 = e.getY();
		gl.sendMessage(message);
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		Message message = new Message();
		// Log.e(TAG, "" + e.getButton());
		message.what = lastButton;
		message.arg1 = e.getX();
		message.arg2 = e.getY();
		gl.sendMessage(message);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		Message message = new Message();
		message.what = GameLoop.MOUSE_MOVE;
		message.arg1 = e.getX();
		message.arg2 = e.getY();
		gl.sendMessage(message);
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
			Message message = new Message();
			message.what = GameLoop.KEY_DOWN;
			message.arg1 = GameLoop.PATH_KEY;
			gl.sendMessage(message);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
			Message message = new Message();
			message.what = GameLoop.KEY_UP;
			message.arg1 = GameLoop.PATH_KEY;
			gl.sendMessage(message);
		}
		if (this.isVisible() && e.getKeyCode() == KeyEvent.VK_ENTER)
			chatText.requestFocus();
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}
}
