/*
    Copyright 2008, 2009 Wolfgang Ginolas

    This file is part of P2PVPN.

    P2PVPN is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Foobar is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.p2pvpn.network;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.tools.VersionizedMap;

/**
 * All packets are running throug the Router. Packages are received from the
 * neighbour peers and the VPNConnecter and send to the neighbour peers or the
 * VPNConnector accordting to the destination address.
 * @author wolfgang
 */
public class Router implements RoutungTableListener {
	private static final long SYNC_TIME = 5; // seconds
	private static final long CONN_TIMEOUT_MS = 60 * 1000;
	
	private static final byte DATA_PACKET = 0;
	private static final byte DATA_BROADCAST_PACKET = 1;
	private static final byte ASK_DB = 2;
	private static final byte SEND_DB = 3;
	private static final byte INTERNAL_PACKET = 4;

	public static final byte INTERNAL_PORT_CHAT = -1;
	public static final byte INTERNAL_PORT_PING = 1;
	
	private ConnectionManager connectionManager;	// the ConnectioionManager
	private VPNConnector vpnConnector;				// the VpnConnector

	private Map<PeerID, P2PConnection> connections;	// all connections
	private Map<PeerID, VersionizedMap<String, String>> peers;	// all peers
	private Map<MacAddress, P2PConnection[]> routeCache;		// cached routes

	private MacAddress myMAC;			// local mac address
	private boolean gotMacFromTun;		// was the mac address received from the und interface?

	private Vector<RoutungTableListener> tableListeners; // listeners of the peer list

	private Map<Byte, InternalPacketListener> internalListeners; // listeners for internal packets

	/**
	 * Create a new Router
	 * @param connectionManager the ConnectionManager
	 */
	public Router(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		tableListeners = new Vector<RoutungTableListener>();
		connections = new HashMap<PeerID, P2PConnection>();
		routeCache = new HashMap<MacAddress, P2PConnection[]>();
		internalListeners = new HashMap<Byte, InternalPacketListener>();
		peers = new HashMap<PeerID, VersionizedMap<String, String>>();
		peers.put(connectionManager.getLocalAddr(), new VersionizedMap<String, String>());
		setRandomMac();
		gotMacFromTun = false;
		
		connectionManager.getScheduledExecutor().schedule(new Runnable() {
			public void run() {
				syncDB();
			}
		}, SYNC_TIME, TimeUnit.SECONDS);
	}

	/**
	 * Add a listener for the peer list.
	 * @param l the listener
	 */
	public synchronized void addTableListener(RoutungTableListener l) {
		tableListeners.add(l);
	}

	/**
	 * Return all knpown peers.
	 * @return the peers
	 */
	public synchronized PeerID[] getPeers() {
		return peers.keySet().toArray(new PeerID[0]);
	}

	/**
	 * Return all known mac addresses.
	 * @param withLocalMac include the local mac address
	 * @return the addresses
	 */
	private synchronized Set<MacAddress> getKnownMACs(boolean withLocalMac) {
		Set<MacAddress> result = new HashSet<MacAddress>();
		for(Map.Entry<PeerID, VersionizedMap<String, String>> e : peers.entrySet()) {
			if (withLocalMac || !e.getKey().equals(connectionManager.getLocalAddr())) {
				String mac = e.getValue().get("vpn.mac");
				if (mac!=null) {
					result.add(new MacAddress(mac));
				}
			}
		}
		return result;
	}

	/**
	 * Return the ID of the peer with the given mac address.
	 * @param mac the addrress
	 * @return the PeerID
	 */
	private PeerID findAddressForMac(MacAddress mac) {
		String macStr = mac.toString();
		for(Map.Entry<PeerID, VersionizedMap<String, String>> e : peers.entrySet()) {
			if (macStr.equals(e.getValue().get("vpn.mac"))) return e.getKey();
		}
		return null;
	}

	/**
	 * Calculate a route to another peer wothout caching.
	 * @param macDest the osther peer
	 * @return list of naighbours  with the shortest connection to the destination
	 */
	private P2PConnection[] findRouteInt(MacAddress macDest) {
		if (macDest.equals(myMAC)) return new P2PConnection[0];
		
		// find Address
		PeerID dest = findAddressForMac(macDest);
		if (dest==null) return new P2PConnection[0];
		
		Queue<PeerID> queue = new LinkedList<PeerID>();
		Map<PeerID, Integer> dist = new HashMap<PeerID, Integer>();
		queue.offer(dest);
		dist.put(dest, 0);

		while (!queue.isEmpty() && !dist.containsKey(connectionManager.getLocalAddr())) {
			PeerID a = queue.remove();
			int d = dist.get(a);
			
			String conn = getPeerInfo(a, "connectedTo");
			if (conn!=null) {
				StringTokenizer st = new StringTokenizer(conn);

				while(st.hasMoreTokens())  {
					PeerID next = new PeerID(st.nextToken());
					
					if (!dist.containsKey(next)) {
						dist.put(next, d+1);
						queue.offer(next);
					}
				}
			}
		}

		Integer maxDist = dist.get(connectionManager.getLocalAddr());
		if (maxDist == null) return new P2PConnection[0];
		Collection<P2PConnection> result = new Vector<P2PConnection>();

		synchronized (this) {
			//System.out.println("Routing: dist to "+dest+" = "+maxDist);
			for(PeerID a : connections.keySet()) {
				Integer d = dist.get(a);
				if (d!=null && d == maxDist - 1) {
					//System.out.println("  next peer: "+a);
					result.add(connections.get(a));
				}
			}
		}
		return result.toArray(new P2PConnection[0]);
	}

	/**
	 * Calculate a route to another peer with caching.
	 * @param macDest the osther peer
	 * @return list of naighbours  with the shortest connection to the destination
	 */
	private P2PConnection[] findRoute(MacAddress macDest) {
		P2PConnection[] result;
		synchronized (this) {
			result = routeCache.get(macDest);
		}
		
		if (result==null) result = findRouteInt(macDest);

		synchronized (this) {
			routeCache.put(macDest, result);
		}
		return result;
	}

	/**
	 * Find a naigbour with the given mac address.
	 * @param mac the mac address
	 * @return the P2PCpnnection to the neighbour
	 */
	public P2PConnection getP2PConnection(MacAddress mac) {
		P2PConnection[] cs = findRoute(mac);
		for (P2PConnection c : cs) {
			PeerID id = c.getRemoteAddr();
			String macStr = getPeerInfo(id, "vpn.mac");
			if (macStr!=null) {
				MacAddress oMac = new MacAddress(macStr);
				if (oMac.equals(mac)) return c;
			}
		}
		return null;
	}

	/**
	 * Find all peers that are reachable from the given peer.
	 * @param reachable the set of reachable peers
	 * @param a the peer
	 */
	private void _addReachablePeer(Set<PeerID> reachable, PeerID a) {
		if (reachable.contains(a)) return;
		
		reachable.add(a);
		VersionizedMap<String, String> db = peers.get(a);
		if (db!=null) {
			String conn = db.get("connectedTo");
			if (conn!=null) {
				StringTokenizer st = new StringTokenizer(conn);
				
				while(st.hasMoreTokens())  {
					_addReachablePeer(reachable, new PeerID(st.nextToken()));
				}
			}
		}
	}

	/**
	 * Update the peer list after the network topology changed.
	 */
	private synchronized void updatePeers() {
		Set<PeerID> reachable = new HashSet<PeerID>();

		routeCache.clear();
		
		_addReachablePeer(reachable, connectionManager.getLocalAddr());
		
		Iterator<PeerID> as = peers.keySet().iterator();
		
		while(as.hasNext()) {
			PeerID a = as.next();
			if (!reachable.contains(a)) as.remove();
		}
		
		for(PeerID a : reachable) {
			if (!peers.containsKey(a)) {
				peers.put(a, new VersionizedMap<String, String>());
			}
		}
	}

	/**
	 * Send the database of a peer.
	 * @param connection sent to this connection
	 * @param a the peer
	 */
	private void sendDBPacket(P2PConnection connection, PeerID a) {
		try {
			ByteArrayOutputStream outB = new ByteArrayOutputStream();
			outB.write(SEND_DB);
			ObjectOutputStream outO = new ObjectOutputStream(outB);
			outO.writeObject(a);
			synchronized (this) {
				outO.writeObject(peers.get(a));
			}
			outO.flush();
			connection.send(outB.toByteArray(), true);
		} catch (IOException ex) {
		}
	}

	/**
	 * Disconnect from neighbours which did not send a apcket for some time or
	 * the invitation expired.
	 */
	private void removeDeadPeers() {
		P2PConnection[] cs = getConnections();
		long time = System.currentTimeMillis();

		for(P2PConnection c : cs) {
			if ((time - c.getConnection().getLastActive() > CONN_TIMEOUT_MS)
					|| c.remoteInvitatonExpired()) c.close();
		}
	}

	/**
	 * Request the databases of all peers.
	 */
	private void syncDB() {
		try {
			Set<PeerID> peerSet;

			removeDeadPeers();
			P2PConnection[] cs = getConnections();

			synchronized (this) {
				peerSet = peers.keySet();
			}

			for (PeerID a : peerSet) {
				P2PConnection c = null;

				if (!a.equals(connectionManager.getLocalAddr())) {
					long version;
					synchronized (this) {
						if (connections.containsKey(a)) {
							c = connections.get(a);
						} else {
							c = cs[(int) (Math.random() * cs.length)];
						}
						version = peers.get(a).getVersion();
					}

					try {
						ByteArrayOutputStream outB = new ByteArrayOutputStream();
						outB.write(ASK_DB);
						ObjectOutputStream outO = new ObjectOutputStream(outB);
						outO.writeObject(a);
						outO.writeLong(version);
						outO.flush();
						c.send(outB.toByteArray(), true);
					} catch (IOException ex) {
					}
				}
			}
		} catch (Throwable e) {
		}
		
		connectionManager.getScheduledExecutor().schedule(new Runnable() {
			public void run() {
				syncDB();
			}
		}, SYNC_TIME, TimeUnit.SECONDS);
	}

	/**
	 * Notify all listeners that the peer list changed.
	 * @param connectionsChanged did the list of neighbours change?
	 */
	private void notifyListeners(boolean connectionsChanged) {
		
		if (connectionsChanged) {
			synchronized (this) {
				StringBuffer cs = new StringBuffer();
				boolean first = true;
				for(P2PConnection c : connections.values()) {
					if (!first) cs.append(" ");
					cs.append(c.getRemoteAddr());
					first = false;
				}
				peers.get(connectionManager.getLocalAddr()).put("connectedTo", cs.toString());
			}
		}

		updatePeers();

		RoutungTableListener[] ls;
		synchronized (this) {
			ls = tableListeners.toArray(new RoutungTableListener[0]);
		}

		for(RoutungTableListener l : ls) {
				l.tableChanged(this);
		}
	}

	/**
	 * Called when the database for a peer changed. Update the network topolpgy
	 * and the list of known IPs.
	 * @param a the peer
	 */
    private void dbChanged(PeerID a) {
    	notifyListeners(false);
        
        // check for local IPs
        if (!a.equals(connectionManager.getLocalAddr())) {
			String port;
			String ips, ip6s;
			synchronized (this) {
				port = peers.get(a).get("local.port");
				ips = peers.get(a).get("local.ips");
				ip6s = peers.get(a).get("local.ip6s");
			}
            if (port!=null && ips!=null) {
                StringTokenizer st = new StringTokenizer(ips);
                while (st.hasMoreTokens()) {
                    try {
                        connectionManager.getConnector().addIP(st.nextToken(), Integer.parseInt(port),
								a, "peer exchange", "", false);
                    } catch (NumberFormatException numberFormatException) {
						Logger.getLogger("").log(Level.WARNING, "", numberFormatException);
                    }
                }
            }
            if (port!=null && ip6s!=null) {
                StringTokenizer st = new StringTokenizer(ip6s);
                while (st.hasMoreTokens()) {
                    try {
                        connectionManager.getConnector().addIP(st.nextToken(), Integer.parseInt(port),
								a, "peer exchange", "", false);
                    } catch (NumberFormatException numberFormatException) {
						Logger.getLogger("").log(Level.WARNING, "", numberFormatException);
                    }
                }
            }
        }
    }
   
	public synchronized String getPeerInfo(PeerID peer, String key) {
		VersionizedMap<String, String> db = peers.get(peer);
		if (db==null) return null;
		return db.get(key);
	}
	
	public synchronized Map<String, String> getPeerInfo(PeerID peer) {
		VersionizedMap<String, String> db = peers.get(peer);
		if (db==null) return null;
		return new TreeMap<String, String>(db);
	}
	
	public void setLocalPeerInfo(String key, String val) {
		synchronized (this) {
			peers.get(connectionManager.getLocalAddr()).put(key, val);
		}
		notifyListeners(false);
	}
	
	public synchronized P2PConnection[] getConnections() {
		return connections.values().toArray(new P2PConnection[0]);
	}

	public synchronized P2PConnection getConnection(PeerID id) {
		return connections.get(id);
	}

	public synchronized boolean isConnectedTo(PeerID id) {
		if (id==null) return false;
		return connections.containsKey(id);
	}

	/**
	 * A new P2PConnection is established and can be used for sending
	 * and receivong apckages.
	 * @param connection the connection
	 */
	public void newP2PConnection(P2PConnection connection) {
		synchronized (this) {
			if (connections.containsKey(connection.getRemoteAddr())
					|| connectionManager.getLocalAddr().equals(connection.getRemoteAddr())) {
				connection.close();
				return;
			}
			connections.put(connection.getRemoteAddr(), connection);
		}
		connection.setRouter(this);
		notifyListeners(true);
	}

	/**
	 * A connection to a neighbour was closed.
	 * @param connection the connection
	 */
	public void connectionClosed(P2PConnection connection) {
		synchronized (this) {
			connections.remove(connection.getRemoteAddr());
		}
		notifyListeners(true);
	}

	/**
	 * Close all connections.
	 */
	public void close() {
		P2PConnection[] cs;
		synchronized (this) {
			cs = connections.values().toArray(new P2PConnection[0]);
		}
		for(P2PConnection c : cs) {
			c.close();
		}
	}

	/**
	 * Print the routing table.
	 */
	public void printTable() {
		System.out.println("Routing Table (this is: "+connectionManager.getLocalAddr()+")");
		System.out.println("=============");

		synchronized (this) {
			for(P2PConnection c : connections.values()) {
				System.out.println(
						c.getRemoteAddr()+"\t"+
						c.getConnection());
			}
		}
		
		System.out.println();
	}

	/**
	 * Called when a packad arrived
	 * @param connection the connection which recheived this packet
	 * @param packet the packet
	 */
	public void receive(P2PConnection connection, byte[] packet) {
		ByteArrayInputStream inB = new ByteArrayInputStream(packet);

		try {
			int type = inB.read();
			
			switch (type) {
				case DATA_PACKET:
				case DATA_BROADCAST_PACKET: {
					handleDataPacket(type, packet);
					break;
				}
				case ASK_DB: {
					ObjectInputStream inO = new ObjectInputStream(inB);
					PeerID a = (PeerID)inO.readObject();
					long hisVer = inO.readLong();
					long myVer = 0;
					synchronized (this) {
						VersionizedMap<String, String> db = peers.get(a);
						if (db!=null) {
							myVer = db.getVersion();
						}
					}
					if (myVer>hisVer) sendDBPacket(connection, a);
					break;
				}
				case SEND_DB: {
					ObjectInputStream inO = new ObjectInputStream(inB);
					PeerID a = (PeerID)inO.readObject();
					VersionizedMap<String, String> hisDB = (VersionizedMap<String, String>)inO.readObject();
					long myVer = 0;
					synchronized (this) {
						VersionizedMap<String, String> db = peers.get(a);
						if (db!=null) myVer = db.getVersion();
						if (myVer < hisDB.getVersion()) peers.put(a, hisDB);
					}
                    dbChanged(a);
					break;
				}
				case INTERNAL_PACKET: {
					handleInternalPacket(packet);
					break;
				}
				default: throw new IOException("Bad packet type");	
			}
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "closing connection to "+connection.getRemoteAddr(), e);
			connection.close();
		} catch (ClassNotFoundException e) {
			Logger.getLogger("").log(Level.SEVERE, "Dying!", e);
			System.exit(1);
		}
	}

	/**
	 * Handle a data packet
	 * @param type the type of the packet
	 * @param packet the packet
	 */
	private void handleDataPacket(int type, byte[] packet) {
		MacAddress dest = new MacAddress(packet, 0+1);
		
		if (dest.equals(myMAC)) {
			//System.out.println("Data-Packet from "+new MacAddress(packet, 6+1)+" for me");
			byte[] subPacket;

			if (type==DATA_BROADCAST_PACKET) {
                            subPacket = new byte[packet.length-1-6];
                            System.arraycopy(packet, 1+6, subPacket, 0, subPacket.length);
			} else {
                            subPacket = new byte[packet.length-1];
                            System.arraycopy(packet, 1, subPacket, 0, subPacket.length);
                        }

			if (vpnConnector!=null) vpnConnector.receive(subPacket);
		} else {
			//System.out.println("Data-Packet from "+new MacAddress(packet, 6+1)+" for "+dest);
			sendInt(dest, packet, false);
		}
	}

	/**
	 * Send an packet.
	 * @param dest the destination
	 * @param packet the packet
	 * @param highPriority has this packet a high priority?
	 */
	private void sendInt(MacAddress dest, byte[] packet, boolean highPriority) {
		P2PConnection[] cs = findRoute(dest);
		if (cs.length>0) {
			int minI=0;
			double minPing = Double.MAX_VALUE;

			for(int i=0; i<cs.length; i++) {
				double ping = cs[i].getPingTime().getAverage();
				if (ping<minPing) {
					minPing = ping;
					minI = i;
				}
			}
			cs[minI].send(packet, highPriority);
		}
	}

	/**
	 * Set the local mac address.
	 * @param mac the address
	 */
	private synchronized void setMac(MacAddress mac) {
		myMAC = mac;
		peers.get(connectionManager.getLocalAddr()).put("vpn.mac", myMAC.toString());
	}

	/**
	 * Set a random mac address.
	 * Used when the real mac address is yet unknown.
	 */
	private void setRandomMac() {
		Random rnd = new Random();
		byte[] mac = new byte[6];
		rnd.nextBytes(mac);
		setMac(new MacAddress(mac));
	}

	/**
	 * Send a packet. Called from VPNConnector.
	 * @param packet the packet
	 */
	public void send(byte[] packet) {
		
		if (!gotMacFromTun) {
			setMac(new MacAddress(packet, 6));
			gotMacFromTun = true;
		}
		
		MacAddress mac = new MacAddress(packet, 0);
		
		if (mac.isBroadcast()) {
			Collection<MacAddress> macs = getKnownMACs(false);
			for(MacAddress d : macs) {
				byte[] parentPacket = new byte[packet.length+1+6];
				parentPacket[0] = DATA_BROADCAST_PACKET;
				System.arraycopy(packet, 0, parentPacket, 1+6, packet.length);
				System.arraycopy(d.getAddress(), 0, parentPacket, 1, 6);	// change destination
				sendInt(d, parentPacket, false);
			}
		} else {
			byte[] parentPacket = new byte[packet.length+1];
			parentPacket[0] = DATA_PACKET;
			System.arraycopy(packet, 0, parentPacket, 1, packet.length);
			sendInt(mac, parentPacket, false);
		}
	}

	/**
	 * Add an internal packet listener.
	 * @param internalPort listen to this port
	 * @param l the listener
	 */
	public synchronized void addInternalPacketListener(byte internalPort, InternalPacketListener l) {
		internalListeners.put(internalPort, l);
	}

	/**
	 * Handle an internal packet
	 * @param packet the packet
	 */
	private void handleInternalPacket(byte[] packet) {
		MacAddress dest = new MacAddress(packet, 0+2);
		byte intPort = packet[1];

		if (dest.equals(myMAC)) {
			byte[] data = new byte[packet.length-2-6];
			System.arraycopy(packet, 2+6, data, 0, data.length);

			InternalPacketListener l;
			synchronized (this) {
				l = internalListeners.get(intPort);
			}
			if (l!=null) l.receiveInternalPacket(this, intPort, data);
		} else {
			sendInt(dest, packet, intPort<0);
		}
	}

	/**
	 * Send an internal packet
	 * @param to the destination
	 * @param internalPort the internal destination port
	 * @param data the data
	 */
	public void sendInternalPacket(MacAddress to, byte internalPort, byte[] data) {
		if (to==null) {
			Collection<MacAddress> macs = getKnownMACs(false);
			for(MacAddress d : macs) {
				if (!d.equals(myMAC)) sendInternalPacket(d, internalPort, data);
			}
		} else {
			byte[] packet = new byte[1 + 1 + 6 + data.length];
			packet[0] = INTERNAL_PACKET;
			packet[1] = internalPort;
			System.arraycopy(to.getAddress(), 0, packet, 2, 6);
			System.arraycopy(data, 0, packet, 1+1+6, data.length);
			sendInt(to, packet, internalPort<0);
		}
	}

	@Override
	public void tableChanged(Router router) {
		printTable();
	}

	public void setVpnConnector(VPNConnector vpnConnector) {
		this.vpnConnector = vpnConnector;
	}

	public MacAddress getMyMAC() {
		return myMAC;
	}
}
