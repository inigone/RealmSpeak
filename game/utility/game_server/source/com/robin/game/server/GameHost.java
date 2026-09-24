package com.robin.game.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import com.robin.game.objects.GameData;
import com.robin.game.objects.GameObject;
import com.robin.game.objects.GameObjectChange;

/**
 * This class will "host" a game, by providing a connection listener, servers, and
 * controlled data management for multiple clients.
 */
public class GameHost {
	public static final String DATA_NAME = "host";
	
	public static GameHost mostRecentHost = null; // FIXME Should use a Singleton pattern here!
	
	private static Logger logger = Logger.getLogger(GameHost.class.getName());

	public static final int DEFAULT_PORT = 47474;
	
	protected GameData masterData;	// loaded once, never changed - used to determine changes for new clients
	protected GameData gameData;	// ever changing gameData
	protected String gameTitle;
	protected String password;
	
	protected String hostName = null;
	
	protected GameConnector connector;
	
	protected ArrayList<GameServer> servers;
	
	protected ArrayList<GameHostListener> gameHostListeners;

	private Map<String, String> clientLayouts = new HashMap<>();
	private Path layoutPersistFile = null;

	private Path chatLogFile = null;
	private PrintWriter chatLogWriter = null;

	public GameHost(String dataPath,String gameTitle,String password) {
		mostRecentHost = this;
		masterData = new GameData();
		masterData.loadFromPath(dataPath);
		masterData.setDataName("master");
		gameData = new GameData();
		gameData.loadFromPath(dataPath);
		gameData.setDataName(DATA_NAME);
		init(gameTitle,password);
	}
	public GameHost(GameData master,GameData data,String gameTitle,String password) {
		mostRecentHost = this;
		masterData = master;
		gameData = data;
		gameData.setDataName(DATA_NAME);
		init(gameTitle,password);
	}
	public boolean isNameUnique(GameServer ignoreServer,String name) {
		for (GameServer server:servers) {
			String clientName = server.getClientName();
			if (!server.equals(ignoreServer) && clientName!=null && clientName.equals(name)) {
				// A reconnecting client races the teardown of its own previous GameServer: until that
				// one is removed from the list its name is still taken, and the reconnect is refused.
				logger.warning("LOGIN REFUSED for \""+name+"\": a GameServer still holds that name ("
					+servers.size()+" registered: "+describeServers()+")");
				return false;
			}
		}
		logger.info("LOGIN name \""+name+"\" is free ("+servers.size()+" registered: "+describeServers()+")");
		return true;
	}
	private String describeServers() {
		StringBuilder sb = new StringBuilder();
		for (GameServer server:servers) {
			if (sb.length()>0) sb.append(", ");
			sb.append(server.getClientName()==null ? "<unnamed>" : server.getClientName());
		}
		return sb.length()==0 ? "none" : sb.toString();
	}
	private void init(String title,String pass) {
		this.connector = null;
		this.gameTitle = title;
		this.password = pass;
		servers = new ArrayList<>();
	}
	public String getGameTitle() {
		return gameTitle;
	}
	public String getPassword() {
		return password;
	}
	public void addGameHostListener(GameHostListener listener) {
		if (gameHostListeners == null) {
			gameHostListeners = new ArrayList<>();
		}
		gameHostListeners.add(listener);
	}
	public boolean removeGameHostListener(GameHostListener listener) {
		boolean success = false;
		if (gameHostListeners != null) {
			success = gameHostListeners.remove(listener);
			if (gameHostListeners.size()==0) {
				gameHostListeners = null;
			}
		}
		return success;
	}
	/** Opens (appending) the chat log file for this hosting session. */
	public void setChatLogFile(Path file) {
		this.chatLogFile = file;
		try {
			chatLogWriter = new PrintWriter(new BufferedWriter(new FileWriter(file.toFile(), true)));
		} catch (IOException ex) {
			logger.warning("Could not open chat log: " + ex.getMessage());
		}
	}

	public void logChatLine(String characterId, String message) {
		if (chatLogWriter == null) return;
		chatLogWriter.println(characterId + "\t" + message);
		chatLogWriter.flush();
	}

	public ArrayList<String> getChatHistory() {
		ArrayList<String> result = new ArrayList<>();
		if (chatLogFile == null || !Files.exists(chatLogFile)) return result;
		try (BufferedReader reader = new BufferedReader(new FileReader(chatLogFile.toFile()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab > 0) {
					result.add(line.substring(0, tab));
					result.add(line.substring(tab + 1));
				}
			}
		} catch (IOException ex) {
			logger.warning("Could not read chat log: " + ex.getMessage());
		}
		return result;
	}

	/** Sets the file used to persist client layouts across server restarts, and loads any saved layouts from it. */
	public void setLayoutPersistFile(Path file) {
		this.layoutPersistFile = file;
		loadLayouts();
	}

	public void storeClientLayout(String clientName, String data) {
		clientLayouts.put(clientName, data);
		persistLayouts();
	}

	public String retrieveClientLayout(String clientName) {
		return clientLayouts.get(clientName);
	}

	private void loadLayouts() {
		if (layoutPersistFile == null || !Files.exists(layoutPersistFile)) return;
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(layoutPersistFile)) {
			props.load(in);
			for (String key : props.stringPropertyNames()) {
				clientLayouts.put(key, props.getProperty(key));
			}
		} catch (IOException ex) {
			logger.warning("Could not load client layouts: " + ex.getMessage());
		}
	}

	private void persistLayouts() {
		if (layoutPersistFile == null) return;
		Properties props = new Properties();
		props.putAll(clientLayouts);
		try (OutputStream out = Files.newOutputStream(layoutPersistFile)) {
			props.store(out, null);
		} catch (IOException ex) {
			logger.warning("Could not save client layouts: " + ex.getMessage());
		}
	}

	public void fireHostOnly(InfoObject io) {
		if (gameHostListeners!=null) {
			for (GameHostListener listener : gameHostListeners) {
				listener.handleHostOnlyInfo(io);
			}
		}
	}
	public void fireHostModified() {
		if (gameHostListeners!=null) {
			fireHostModified(new GameHostEvent(this));
		}
	}
	public void fireHostModified(GameHostEvent event) {
		if (gameHostListeners!=null) {
			for (GameHostListener listener : gameHostListeners) {
				listener.hostModified(event);
			}
		}
	}
	public void fireServerLost(GameServer server) {
		if (gameHostListeners!=null) {
			GameHostEvent event = new GameHostEvent(this,server,GameHostEvent.NOTICE_LOST_CONNECTION);
			for (GameHostListener listener : gameHostListeners) {
				listener.serverLost(event);
			}
		}
	}
	
	public void startListening() {
		startListening(DEFAULT_PORT);
	}
	/**
	 * Launches a GameConnector, listening on given port.
	 */
	public void startListening(int port) {
		if (connector==null) {
			connector = new GameConnector(this,port);
			connector.start();
			try {
				Thread.sleep(500); // Give the connector a chance to start!
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			logger.fine("Started listening on port "+port);
		}
	}
	public void stopListening() {
		if (connector!=null) {
			connector.kill();
			connector = null;
		}
		logger.fine("Stopped listening.");
		mostRecentHost = null;
	}
	public GameConnector getConnector() {
		return connector;
	}
	
	/**
	 * Assigns a server to the provided connection
	 */
	public void addConnection(Socket connection) {
		try { connection.setSoTimeout(GameNet.DEFAULT_TIMEOUT_MS); } catch(IOException ex) { /* ignore */ }
		GameServer server = new GameServer(this,connection);
		server.setClientHostName(hostName);
		server.start();
		servers.add(server);
		logger.info("SERVER ADDED for a new connection ("+servers.size()+" registered: "+describeServers()+")");
		fireHostModified(new GameHostEvent(this,server,GameHostEvent.NOTICE_NEW_CONNECTION));
	}
	public void removeServer(GameServer server) {
		if (servers.remove(server)) {
			logger.info("SERVER REMOVED for client "+server.getClientName()
				+" ("+servers.size()+" still registered: "+describeServers()+")");
			fireServerLost(server);
		}
//		else {
//			logger.info("Unable to remove server for client "+server.getClientName()+"!!");
//			throw new IllegalStateException("Unable to remove server for client "+server.getClientName()+"!!");
//		}
	}
	public void killAllOutsideConnections() {
		// Assume that the first connection is the host's player, and shut down all the rest.
		ArrayList<GameServer> list = new ArrayList<>();
		list.add(servers.remove(0));
		shutdown();
		servers = list;
	}
	public void killConnection(GameServer server) {
		server.kill();
		servers.remove(server);
	}
	public void shutdown() {
		for (GameServer server:servers) {
			server.kill();
		}
		servers.clear();
	}
	public ArrayList<GameServer> getServers() {
		return servers;
	}
	
	public GameData getGameData() {
		return gameData;
	}
	public GameObject getGameObject(Long id) {
		return gameData.getGameObject(id);
	}
	
	public boolean applyChanges(GameServer activeServer,ArrayList<GameObjectChange> changes) {
		return applyChanges(activeServer,changes,true);
	}
	public boolean applyChanges(GameServer activeServer,ArrayList<GameObjectChange> changes,boolean fireChange) {
		// Apply changes and distribute to peer servers while holding the GameHost lock,
		// then release the lock before calling fireHostModified().
		// Releasing the lock before fireHostModified() means concurrent applyChanges() calls
		// and getMasterToGameChanges() calls are never held up by slow host-panel work
		// (e.g. RealmHostPanel updateGameState() and may trigger autosave (zipToFile)
		boolean shouldFire = false;
		synchronized(this) {
			if (changes!=null && !changes.isEmpty()) {
//				for (Iterator i=changes.iterator();i.hasNext();) {
//					GameObjectChange change = (GameObjectChange)i.next();
//					if (!change.testVersion(gameData)) {
//						// Version inconsistency
//						return false;
//					}
//				}
				logger.fine("Host apply changes: "+changes.size()+" changes.");
				for (GameObjectChange action : changes) {
					logger.finer("--> "+action);
					action.applyChange(gameData);
				}
//				gameData.rebuildChanges(); // This breaks things fairly badly!
				logger.fine("Host apply changes: DONE.");

				// Update all servers (except the originating server) with the changes
				ArrayList<GameServer> serversToUpdate = new ArrayList<>();
				serversToUpdate.addAll(servers);
				for (GameServer server:serversToUpdate) {
					logger.fine("activeServer="+activeServer);
					logger.fine("server="+server);
					if (activeServer==null || !server.equals(activeServer)) {
						logger.fine("updating a server with "+changes.size());
						server.addObjectChanges(changes);
					}
				}

				if (activeServer!=null && fireChange) {
					shouldFire = true;
				}
			}
		}
		// GameHost lock is released - fireHostModified/updateGame may be slow but will
		// no longer block concurrent applyChanges() or getMasterToGameChanges() calls.
		if (shouldFire) {
			fireHostModified();
		}
		return true;
	}
	public void broadcast(String key,String message) {
		for (GameServer server:servers) {
			server.broadcast(key,message);
		}
	}
	public void distributeInfo(InfoObject io) {
		if (io.isForHost()) {
			fireHostOnly(io);
		}
		else {
			for (GameServer server:servers) {
				if (server==null) continue; // would this EVER happen?
				String serverClientName = server.getClientName()==null?"":server.getClientName();
				String ioClientName = io.getDestClientName()==null?null:io.getDestClientName();
				if (serverClientName.equals(ioClientName)) {
//					boolean sameThread = server==Thread.currentThread();
					server.addInfoDirect(io);
//					if (sameThread) {
////System.out.println("Same thread");
//						// Same thread, so drive the server manually
//						try {
//							while(!server.isInfoDirectSentAndReceived()) {
//								server.processNextRequest();
//							}
//						}
//						catch (Exception ex) {
//							ex.printStackTrace();
//						}
//					}
//					else {
////System.out.println("Diff thread");
//						// Different thread, so let the server do it itself, but wait until it is done
//						try {
//							while(!server.isInfoDirectSentAndReceived()) {
//								Thread.sleep(200);
//							}
//						}
//						catch (InterruptedException ex) {
//							ex.printStackTrace();
//						}
//					}
					break; // no need to keep searching if the server was found
				}
			}
		}
	}
	public synchronized ArrayList<GameObjectChange> getMasterToGameChanges() {
		return masterData.buildChanges(gameData);
	}
	public void _testBuildChanges() {
		ArrayList<GameObjectChange> changes = getMasterToGameChanges();
		System.out.println("changes="+changes.size());
		for (GameObjectChange change : changes) {
			System.out.println(change);
		}
		changes.clear();
	}
	/**
	 * Only used when connecting locally
	 */
	public void connectClient(GameClient client) {
		if (client.connection==null) {
			NetFreeSocket serverSide = new NetFreeSocket();
			NetFreeSocket clientSide = new NetFreeSocket();
			serverSide.connect(clientSide);
			client.connection = clientSide;
			client.connected = true;
			addConnection(serverSide);
		}
		else {
			throw new IllegalStateException("Client is already connected!!");
		}
	}
	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
}