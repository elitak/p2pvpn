/*
    Copyright 2008 Wolfgang Ginolas

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


public class Router implements RoutungTableListener {
	private static final long SYNC_TIME = 5; // seconds
	
	private static final byte DATA_PACKET = 0;
	private static final byte DATA_BROADCAST_PACKET = 1;
	private static final byte ASK_DB = 2;
	private static final byte SEND_DB = 3;
	private static final byte INTERNAL_PACKET = 4;

	public static final byte INTERNAL_PORT_CHAT = -1;
	
	private ConnectionManager connectionManager;
	private VPNConnector vpnConnector;

	private Map<PeerID, P2PConnection> connections;
	private Map<PeerID, VersionizedMap<String, String>> peers;
	private Map<MacAddress, P2PConnection[]> routeCache;

	private MacAddress myMAC;
	private boolean gotMacFromTun;

	private Vector<RoutungTableListener> tableListeners;

	private Map<Byte, InternalPacketListener> internalListeners;

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
	
	public synchronized void addTableListener(RoutungTableListener l) {
		tableListeners.add(l);
	}

	public synchronized PeerID[] getPeers() {
		return peers.keySet().toArray(new PeerID[0]);
	}
	
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

	private PeerID findAddressForMac(MacAddress mac) {
		String macStr = mac.toString();
		for(Map.Entry<PeerID, VersionizedMap<String, String>> e : peers.entrySet()) {
			if (macStr.equals(e.getValue().get("vpn.mac"))) return e.getKey();
		}
		return null;
	}
	
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
	
	private void syncDB() {
		P2PConnection[] cs = getConnections();
		Set<PeerID> peerSet;

		synchronized (this) {
			peerSet = peers.keySet();
		}

		for(PeerID a : peerSet) {
			P2PConnection c = null;
			
			if (!a.equals(connectionManager.getLocalAddr())) {
				long version;
				synchronized (this) {
					if (connections.containsKey(a)) {
						c = connections.get(a);
					} else {
						c = cs[(int)(Math.random()*cs.length)];
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
		
		connectionManager.getScheduledExecutor().schedule(new Runnable() {
			public void run() {
				syncDB();
			}
		}, SYNC_TIME, TimeUnit.SECONDS);
	}
	
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
	
    private void dbChanged(PeerID a) {
    	notifyListeners(false);
        
        // check for local IPs
        if (!a.equals(connectionManager.getLocalAddr())) {
			String port;
			String ips;
			synchronized (this) {
				port = peers.get(a).get("local.port");
				ips = peers.get(a).get("local.ips");
			}
            if (port!=null && ips!=null) {
                StringTokenizer st = new StringTokenizer(ips);
                while (st.hasMoreTokens()) {
                    try {
                        connectionManager.getConnector().addIP(st.nextToken(), Integer.parseInt(port),
								a, "peer exchange", false);
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
	
	public synchronized boolean isConnectedTo(PeerID id) {
		if (id==null) return false;
		return connections.containsKey(id);
	}
	
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

	public void connectionClosed(P2PConnection connection) {
		synchronized (this) {
			connections.remove(connection.getRemoteAddr());
		}
		notifyListeners(true);
	}
	
	public void close() {
		P2PConnection[] cs;
		synchronized (this) {
			cs = connections.values().toArray(new P2PConnection[0]);
		}
		for(P2PConnection c : cs) {
			c.close();
		}
	}
	
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

	private void sendInt(MacAddress dest, byte[] packet, boolean highPriority) {
		P2PConnection[] cs = findRoute(dest);
		if (cs.length>0) {
			// TODO something more intelligent then random
			cs[(int)(Math.random()*cs.length)].send(packet, highPriority);
		}
	}

	private synchronized void setMac(MacAddress mac) {
		myMAC = mac;
		peers.get(connectionManager.getLocalAddr()).put("vpn.mac", myMAC.toString());
	}

	private void setRandomMac() {
		Random rnd = new Random();
		byte[] mac = new byte[6];
		rnd.nextBytes(mac);
		setMac(new MacAddress(mac));
	}

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

	public synchronized void addInternalPacketListener(byte internalPort, InternalPacketListener l) {
		internalListeners.put(internalPort, l);
	}

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
}
