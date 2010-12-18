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
package org.p2pvpn.network.bittorrent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.PriorityBlockingQueue;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.bittorrent.bencode.Bencode;
import org.p2pvpn.network.bittorrent.bencode.BencodeInt;
import org.p2pvpn.network.bittorrent.bencode.BencodeList;
import org.p2pvpn.network.bittorrent.bencode.BencodeMap;
import org.p2pvpn.network.bittorrent.bencode.BencodeObject;
import org.p2pvpn.network.bittorrent.bencode.BencodeString;
import org.p2pvpn.tools.CryptoUtils;

public class DHT {
	private static final int PACKET_LEN = 10*1024;
	private static final BigInteger MASK = new BigInteger("1").shiftLeft(160);
	private static final int QUEUE_MAX_LEN = 100;
	private static final int MAX_BAD = 4;
	private static final int MAX_BAD_LEN = 100;

	private static final int ASK_PEER_COUNT = 10;
	private static final int FAST_DELAY = 1000; //msant && java -classpath build/P2PVPN.jar org.p2pvpn.network.bittorrent.DHT
	private static final int SLOW_DELAY = 60*1000; //ms

	private final DatagramSocket dSock;

	private BencodeString id;
	private BencodeString searchID = null;
	private BigInteger searchIDInt = null;

	private PriorityBlockingQueue<Contact> peerQueue;

	private final Map<Contact, Integer> peerBad;

	private int ipsFound = 0;

	private ConnectionManager connectionManager = null;

	public DHT(DatagramSocket dSock) {
		System.out.println("2");

		this.dSock = dSock;

		final byte[] idBytes = new byte[20];
		CryptoUtils.getSecureRandom().nextBytes(idBytes);
		System.out.println("3");
		id = new BencodeString(idBytes);

		peerQueue = makeQueue();

		peerBad = Collections.synchronizedMap(new HashMap<Contact, Integer>());
		
		setSearchID(null);

	}

	public void start() {
		new Thread(new Runnable() {
			public void run() {
				mainThread();
			}
		}).start();
	}

	private PriorityBlockingQueue<Contact> makeQueue() {
		return new PriorityBlockingQueue<Contact>(10, new Comparator<Contact>() {
			public int compare(Contact o1, Contact o2) {
				BigInteger d1 = searchDist(o1);
				BigInteger d2 = searchDist(o2);
				return d1.compareTo(d2);
			}
		});
	}

	public void setConnectionManager(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
	}

	public void setSearchID(BencodeString searchID) {
		this.searchID = searchID==null ? new BencodeString("01234567890123456789") : searchID;
		searchIDInt = unsigned(new BigInteger(this.searchID.getBytes()));

		// reorder queue
		cleanQueue();
		ipsFound = 0;
	}


	private BigInteger searchDist(Contact c) {
		return unsigned(searchIDInt.xor(c.getId()));
	}

	public static BigInteger unsigned(BigInteger i) {
		if (i.signum()<0) {
			return MASK.add(i);
		} else return i;
	}

	private void ping(SocketAddress addr) throws IOException {
		BencodeMap m = new BencodeMap();
		m.put(new BencodeString("t"), new BencodeString("ping"));
		m.put(new BencodeString("y"), new BencodeString("q"));
		m.put(new BencodeString("q"), new BencodeString("ping"));
		BencodeMap a = new BencodeMap();
		a.put(new BencodeString("id"), id);
		m.put(new BencodeString("a"), a);
		System.out.print("ping: "+addr);
		sendPacket(addr, m);
		System.out.println(" done");
	}

	private void getPeers(Contact c) throws IOException {
		BencodeMap m = new BencodeMap();
		m.put(new BencodeString("t"), new BencodeString("get_peers"));
		m.put(new BencodeString("y"), new BencodeString("q"));
		m.put(new BencodeString("q"), new BencodeString("get_peers"));
		BencodeMap a = new BencodeMap();
		a.put(new BencodeString("id"), id);
		a.put(new BencodeString("info_hash"), searchID);
		a.put(new BencodeString("want"), new BencodeString("n6"));
		m.put(new BencodeString("a"), a);
		//System.out.println("get_peers: "+c);
		sendPacket(c.getAddr(), m);
	}

	private void announcePeer(Contact c, BencodeString token) throws IOException {
		if (token==null) return;
		BencodeMap m = new BencodeMap();
		m.put(new BencodeString("t"), new BencodeString("announce"));
		m.put(new BencodeString("y"), new BencodeString("q"));
		m.put(new BencodeString("q"), new BencodeString("announce_peer"));
		BencodeMap a = new BencodeMap();
		a.put(new BencodeString("id"), id);
		a.put(new BencodeString("info_hash"), searchID);
		a.put(new BencodeString("port"), new BencodeInt(dSock.getLocalPort()));
		a.put(new BencodeString("token"), token);
		m.put(new BencodeString("a"), a);
		//System.out.println("get_peers: "+c);
		sendPacket(c.getAddr(), m);
	}

	private void cleanQueue() {
		System.out.println("cleanQueue");
		final PriorityBlockingQueue<Contact> newQueue = makeQueue();

		for(Contact c : peerQueue) {
			newQueue.add(c);
			if (newQueue.size()*2 > QUEUE_MAX_LEN) break;
		}

		peerQueue = newQueue;
	}

	private void cleanBad() {
		peerBad.clear();
	}

	private void mainThread() {
		new Thread(new Runnable() {
			public void run() {
				recvThread();
			}
		}).start();

		while (true) {
			try {
				System.out.println("loop");
				Thread.sleep(ipsFound < 10 ? FAST_DELAY : SLOW_DELAY);
				if (peerQueue.isEmpty()) {
					ipsFound = 0;
					ping(new InetSocketAddress("router.utorrent.com", 6881));
					ping(new InetSocketAddress("router.bittorrent.com", 6881));
					//ping(new InetSocketAddress("dht.wifi.pps.jussieu.fr",6881));
				} else {
					Vector<Contact> best = new Vector<Contact>();
					{
						Contact c;
						while (best.size()<ASK_PEER_COUNT && null!=(c=peerQueue.poll())) {
							if (!best.contains(c) && getBad(c)<MAX_BAD) {
								best.add(c);
								System.out.println("best: dist: "+searchDist(c).bitLength()+"  peer: "+c+
										"  bad: "+getBad(c));
							}
						}
						System.out.println();
					}
					for (Contact c : best) {
						getPeers(c);
						makeBad(c);
						addQueue(c, false);
					}
					System.out.println(String.format("peerQueue: %d  peerBad: %d",
							peerQueue.size(), peerBad.size()));
				}
			} catch (Exception ex) {
				ex.printStackTrace(); // TODO
			}
		}
	}

	private void sendPacket(SocketAddress addr, BencodeObject o) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		//System.out.println("send "+addr+": "+o);
		o.write(out);
		byte[] buf = out.toByteArray();
		//System.out.println("test1: "+new String(buf));
		//BencodeObject to = Bencode.parseBencode(new ByteArrayInputStream(buf));
		//System.out.println("test2: "+to);
		DatagramPacket p = new DatagramPacket(buf, buf.length, addr);
		dSock.send(p);
	}

	private void makeGood(Contact c) {
		peerBad.remove(c);
	}

	private int getBad(Contact c) {
		Integer i = peerBad.get(c);
		if (i==null) return 0;
		return i;
	}

	private void makeBad(Contact c) {
		if (peerBad.size()>MAX_BAD_LEN) cleanBad();

		peerBad.put(c, getBad(c)+1);
	}

	private void addQueue(Contact c, boolean good) {
		if (peerQueue.size()>QUEUE_MAX_LEN) cleanQueue();

		if (!peerQueue.contains(c)) peerQueue.add(c);
		if (good) {
			makeGood(c);
		}
	}

	private void recvPacket(DatagramPacket p) {
		try {
			BencodeObject o = Bencode.parseBencode(new ByteArrayInputStream(p.getData(), 0, p.getLength()));
			//System.out.println("recv: "+o);
			if (((BencodeMap)o).get(new BencodeString("y")).equals(new BencodeString("r"))) {
				String t = ((BencodeMap)o).get(new BencodeString("t")).toString();
				BencodeMap r = (BencodeMap)((BencodeMap)o).get(new BencodeString("r"));
				//System.out.println("t: "+t);
				if (t.equals("ping")) {
					BencodeString rid = (BencodeString)r.get(new BencodeString("id"));
					addQueue(new Contact(rid.getBytes(), p.getSocketAddress()), true);
				}
				if (t.equals("get_peers")) {
					BencodeString rid = (BencodeString)r.get(new BencodeString("id"));
					Contact rem = new Contact(rid.getBytes(), p.getSocketAddress());
					addQueue(rem, true);
					BencodeString nodes = (BencodeString)r.get(new BencodeString("nodes"));
					BencodeString nodes6 = (BencodeString)r.get(new BencodeString("nodes6"));
					BencodeList values = (BencodeList)r.get(new BencodeString("values"));
					if (nodes!=null) {
						byte[] bs = nodes.getBytes();
						for(int i=0; i+26<bs.length; i+=26) {
							addQueue(new Contact(bs, i, 26), false);
						}
					}
					if (nodes6!=null) {
						byte[] bs = nodes6.getBytes();
						for(int i=0; i+(20+16+2)<bs.length; i+=(20+16+2)) {
							addQueue(new Contact(bs, i, 26), false);
						}
					}
					if (values!=null) {
						System.out.println("Values:");
						for(BencodeObject val : values) {
							ipsFound++;
							byte[] bs = ((BencodeString)val).getBytes();
							InetSocketAddress addr = Contact.parseSocketAddress(bs, 0, bs.length);
							if (connectionManager!=null) {
								connectionManager.getConnector().addIP(
										addr.getAddress(),
										addr.getPort(),
										null,
										"DHT",
										"",
										false);
							}
							System.out.println("   "+addr);
						}
					}
					announcePeer(rem, (BencodeString)r.get(new BencodeString("token")));
				}
			} else if (((BencodeMap)o).get(new BencodeString("y")).equals(new BencodeString("e"))) {
				BencodeList e = (BencodeList)(((BencodeMap)o).get(new BencodeString("e")));
				System.out.print("Error: ");
				for(BencodeObject bo : e) {
					System.out.print(bo+" ");
				}
				System.out.println();
			}
		} catch (IOException ex) {
			ex.printStackTrace(); // TODO
		}
	}

	private void recvThread() {
		try {
			byte[] buf = new byte[PACKET_LEN];
			DatagramPacket p = new DatagramPacket(buf, PACKET_LEN);
			while (true) {
				dSock.receive(p);
				recvPacket(p);
			}
		} catch (IOException iOException) {
			iOException.printStackTrace(); // TODO
		}
	}

	public static void main(String args[]) {
		System.out.println("main");
		try {
			System.out.println("1");
			final DHT dht = new DHT(new DatagramSocket(Integer.parseInt(args[0])));
			dht.start();
		} catch (SocketException ex) {
			ex.printStackTrace();
		}
	}

	public BigInteger getID() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

}
