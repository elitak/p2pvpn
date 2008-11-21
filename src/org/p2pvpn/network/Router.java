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
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import org.p2pvpn.tools.VersionizedMap;


public class Router implements RoutungTableListener {
	static final long SYNC_TIME = 5; // seconds
	
	static final byte DATA_PACKET = 0;
	static final byte DATA_BROADCAST_PACKET = 1;
	static final byte ASK_DB = 2;
	static final byte SEND_DB = 3;
	
	private ConnectionManager connectionManager;
	private Map<PeerID, P2PConnection> connections;
	private Map<PeerID, VersionizedMap<String, String>> peers;
	private Vector<RoutungTableListener> tableListeners;
	private VPNConnector vpnConnector;

	private Map<MacAddress, P2PConnection[]> routeCache;
	
	private MacAddress myMAC;

	public Router(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		tableListeners = new Vector<RoutungTableListener>();
		connections = new HashMap<PeerID, P2PConnection>();
		routeCache = new HashMap<MacAddress, P2PConnection[]>();
		peers = new HashMap<PeerID, VersionizedMap<String, String>>();
		peers.put(connectionManager.getLocalAddr(), new VersionizedMap<String, String>());
		myMAC = null;
		
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
	
	private Set<MacAddress> getKnownMACs(boolean withLocalMac) {
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
		
		System.out.println("Routing: dist to "+dest+" = "+maxDist);
		for(PeerID a : connections.keySet()) {
			Integer d = dist.get(a);
			if (d!=null && d == maxDist - 1) {
				System.out.println("  next peer: "+a);
				result.add(connections.get(a));
			}
		}

		return result.toArray(new P2PConnection[0]);
	}
	
	private P2PConnection[] findRoute(MacAddress macDest) {
		P2PConnection[] result = routeCache.get(macDest);
		
		if (result==null) result = findRouteInt(macDest);

		routeCache.put(macDest, result);
		return result;
	}
	
	private void addReachablePeer(Set<PeerID> reachable, PeerID a) {
		if (reachable.contains(a)) return;
		
		reachable.add(a);
		VersionizedMap<String, String> db = peers.get(a);
		if (db!=null) {
			String conn = db.get("connectedTo");
			if (conn!=null) {
				StringTokenizer st = new StringTokenizer(conn);
				
				while(st.hasMoreTokens())  {
					addReachablePeer(reachable, new PeerID(st.nextToken()));
				}
			}
		}
	}

	private void updatePeers() {
		Set<PeerID> reachable = new HashSet<PeerID>();
		
		routeCache.clear();
		
		addReachablePeer(reachable, connectionManager.getLocalAddr());
		
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
			outO.writeObject(peers.get(a));
			outO.flush();
			connection.send(outB.toByteArray());
		} catch (IOException ex) {
		}
	}
	
	private synchronized void syncDB() {
		P2PConnection[] cs = getConnections();
		
		for(PeerID a : peers.keySet()) {
			P2PConnection c = null;
			
			if (!a.equals(connectionManager.getLocalAddr())) {
				if (connections.containsKey(a)) {
					c = connections.get(a);
				} else {
					c = cs[(int)(Math.random()*cs.length)];
				}

				try {
					ByteArrayOutputStream outB = new ByteArrayOutputStream();
					outB.write(ASK_DB);
					ObjectOutputStream outO = new ObjectOutputStream(outB);
					outO.writeObject(a);
					outO.writeLong(peers.get(a).getVersion());
					outO.flush();
					c.send(outB.toByteArray());
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
			StringBuffer cs = new StringBuffer();
			boolean first = true;
			for(P2PConnection c : connections.values()) {
				if (!first) cs.append(" ");
				cs.append(c.getRemoteAddr());
				first = false;
			}
			peers.get(connectionManager.getLocalAddr()).put("connectedTo", cs.toString());
		}

		updatePeers();
		
		for(RoutungTableListener l : tableListeners) {
			l.tableChanged(this);
		}
	}
	
    private void dbChanged(PeerID a) {
    	notifyListeners(false);
        
        // check for local IPs
        if (!a.equals(connectionManager.getLocalAddr())) {
            String port = peers.get(a).get("local.port");
            String ips = peers.get(a).get("local.ips");
            if (port!=null && ips!=null) {
                StringTokenizer st = new StringTokenizer(ips);
                while (st.hasMoreTokens()) {
                    try {
                        connectionManager.getConnector().addIP(st.nextToken(), Integer.parseInt(port));
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
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
	
	public synchronized void setLocalPeerInfo(String key, String val) {
		peers.get(connectionManager.getLocalAddr()).put(key, val);
		notifyListeners(false);
	}
	
	public synchronized P2PConnection[] getConnections() {
		return connections.values().toArray(new P2PConnection[0]);
	}
	
	public synchronized boolean isConnectedTo(PeerID id) {
		return connections.containsKey(id);
	}
	
	public synchronized void newP2PConnection(P2PConnection connection) {
		if (connections.containsKey(connection.getRemoteAddr())
				|| connectionManager.getLocalAddr().equals(connection.getRemoteAddr())) {
			connection.close();
			return;
		}
		connections.put(connection.getRemoteAddr(), connection);
		connection.setRouter(this);
		notifyListeners(true);
	}

	public synchronized void connectionClosed(P2PConnection connection) {
		connections.remove(connection.getRemoteAddr());
		notifyListeners(true);
	}
	
	public synchronized void close() {
		for(P2PConnection c : connections.values()) {
			c.close();
		}
	}
	
	public void printTable() {
		System.out.println("Routing Table (this is: "+connectionManager.getLocalAddr()+")");
		System.out.println("=============");
		
		for(P2PConnection c : connections.values()) {
			System.out.println(
					c.getRemoteAddr()+"\t"+
					c.getConnection());
		}
		
		System.out.println();
	}

	public synchronized void receive(P2PConnection connection, byte[] packet) {
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
					VersionizedMap<String, String> db = peers.get(a);
					if (db!=null) {
						myVer = db.getVersion();
						if (myVer>hisVer) sendDBPacket(connection, a);
					}
					break;
				}
				case SEND_DB: {
					ObjectInputStream inO = new ObjectInputStream(inB);
					PeerID a = (PeerID)inO.readObject();
					VersionizedMap<String, String> hisDB = (VersionizedMap<String, String>)inO.readObject();
					long myVer = 0;
					VersionizedMap<String, String> db = peers.get(a);
					if (db!=null) myVer = db.getVersion();
					if (myVer < hisDB.getVersion()) peers.put(a, hisDB);
                    dbChanged(a);
					break;
				}
				default: throw new IOException("Bad packet type");	
			}
		} catch (IOException e) {
			e.printStackTrace();
			connection.close();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void handleDataPacket(int type, byte[] packet) {
		MacAddress dest = new MacAddress(packet, 0+1);
		
		if (dest.equals(myMAC)) {
			System.out.println("Data-Packet from "+new MacAddress(packet, 6+1)+" for me");
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
			System.out.println("Data-Packet from "+new MacAddress(packet, 6+1)+" for "+dest);
			sendInt(dest, packet);
		}
	}
	
	private void sendInt(MacAddress dest, byte[] packet) {
		P2PConnection[] cs = findRoute(dest);
		if (cs.length>0) {
			// TODO something more intelligent then random
			cs[(int)(Math.random()*cs.length)].send(packet);
		}
	}
	
	public synchronized void send(byte[] packet) {
		
		if (myMAC==null) {
			myMAC = new MacAddress(packet, 6);
			peers.get(connectionManager.getLocalAddr()).put("vpn.mac", myMAC.toString());
		}
		
		MacAddress mac = new MacAddress(packet, 0);
		
		if (mac.isBroadcast()) {
			Collection<MacAddress> macs = getKnownMACs(false);
			for(MacAddress d : macs) {
				byte[] parentPacket = new byte[packet.length+1+6];
				parentPacket[0] = DATA_BROADCAST_PACKET;
				System.arraycopy(packet, 0, parentPacket, 1+6, packet.length);
				System.arraycopy(d.getAddress(), 0, parentPacket, 1, 6);	// change destination
				sendInt(d, parentPacket);
			}
		} else {
			byte[] parentPacket = new byte[packet.length+1];
			parentPacket[0] = DATA_PACKET;
			System.arraycopy(packet, 0, parentPacket, 1, packet.length);
			sendInt(mac, parentPacket);
		}
	}
	
	@Override
	public void tableChanged(Router router) {
		printTable();
	}

	public synchronized void setVpnConnector(VPNConnector vpnConnector) {
		this.vpnConnector = vpnConnector;
	}
}
