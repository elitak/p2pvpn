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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * This class periodically sends ping packats to all naibour peers.
 * @author Wolfgang Ginolas
 */
public class Pinger implements InternalPacketListener {

	private final static byte PING_REQUEST = 0;
	private final static byte PING_REPLY = 1;

	private final static long MAX_PING_TIME_MS = 10 * 1000;
	private final static long PING_INTERVALL_MS = 1 * 1000;

	private ConnectionManager connectionManager;	// the ConnectionManager
	private Router router;							// the Router
	private Random random;							// used for random numbers
	private Map<Integer, PingInfo> pings;			// the currently running pings

	/**
	 * Create a new Pinger.
	 * @param connectionManager the ConnectionManager
	 */
	public Pinger(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		router = connectionManager.getRouter();
		random = new Random();
		pings = new HashMap<Integer, PingInfo>();

		router.addInternalPacketListener(Router.INTERNAL_PORT_PING, this);

		schedulePing();
	}

	/**
	 * Send ping requests to all naibours.
	 */
	private void pingAll() {
		P2PConnection[] cs = router.getConnections();
		for(P2PConnection c : cs) {
			PeerID id = c.getRemoteAddr();
			String macStr = router.getPeerInfo(id, "vpn.mac");
			if (macStr!=null) {
				MacAddress mac = new MacAddress(macStr);
				sendPingRequest(mac);
			}
		}

		// remove old pings
		long time = System.currentTimeMillis();
		synchronized (this) {
			Iterator<PingInfo> iter = pings.values().iterator();
			while(iter.hasNext()) {
				PingInfo i = iter.next();
				if (time-i.getSendTime() > MAX_PING_TIME_MS) {
					iter.remove();
					P2PConnection c = router.getP2PConnection(i.getMac());
					if (c!=null) c.getPingTime().putVaule(MAX_PING_TIME_MS);
				}
			}
		}
		schedulePing();
	}

	/**
	 * Schedule the nect ping request.
	 */
	private void schedulePing() {
		connectionManager.getScheduledExecutor().schedule(new Runnable() {
			public void run() {
				pingAll();
			}
		}, PING_INTERVALL_MS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Send a ping request.
	 * @param to the destination
	 */
	private void sendPingRequest(MacAddress to) {
		byte[] packet = new byte[1 + 2 + 6];
		int id;

		synchronized (this) {
			id = random.nextInt() & 0xFFFF;
			pings.put(id, new PingInfo(to));
		}

		packet[0] = PING_REQUEST;
		packet[1] = (byte)(id >> 8);
		packet[2] = (byte)(id & 0xFF);
		System.arraycopy(router.getMyMAC().getAddress(), 0, packet, 3, 6);
		router.sendInternalPacket(to, Router.INTERNAL_PORT_PING, packet);
		//System.out.println("ping request to "+to);
	}

	/**
	 * Send a ping replay
	 * @param request the request packet
	 */
	private void sendPingReply(byte[] request) {
		if (request.length==9) {
			byte[] packet = new byte[1 + 2];
			byte[] mac = new byte[6];

			packet[0] = PING_REPLY;
			packet[1] = request[1];		// copy the Ping ID
			packet[2] = request[2];

			router.sendInternalPacket(new MacAddress(request, 3), Router.INTERNAL_PORT_PING, packet);
			//System.out.println("ping reply to "+new MacAddress(request, 3));
		}
	}

	/**
	 * Handle a recheives ping reply.
	 * @param packet the replay packet
	 */
	private void handlePingReply(byte[] packet) {
		long time = System.currentTimeMillis();
		if (packet.length==3) {
			int id = ((0xFF & packet[1]) << 8) + (0xFF & packet[2]);

			PingInfo info;
			synchronized (this) {
				info = pings.get(id);
				pings.remove(id);
			}
			if (info!=null) {
				P2PConnection c = router.getP2PConnection(info.getMac());
				//System.out.println("ping reply from "+info.getMac());
				if (c!=null) {
					c.getPingTime().putVaule(time - info.getSendTime());
				}
			}
		}
	}

	/**
	 * Calles when a ping request or reply arrived
	 * @param router the Router
	 * @param internalPort the internal port
	 * @param packet the packet
	 */
	public void receiveInternalPacket(Router router, byte internalPort, byte[] packet) {
		if (internalPort != Router.INTERNAL_PORT_PING) return;

		switch (packet[0]) {
			case PING_REQUEST:
				sendPingReply(packet);
				break;
				
			case PING_REPLY:
				handlePingReply(packet);
				break;
			default:
		}
	}

	/**
	 * Information about a running ping.
	 */
	private class PingInfo {
		private long sendTime;
		private MacAddress mac;

		/**
		 * Create a new PingInfo.
		 * @param mac the destination
		 */
		public PingInfo(MacAddress mac) {
			this.sendTime = System.currentTimeMillis();
			this.mac = mac;
		}

		public MacAddress getMac() {
			return mac;
		}

		/**
		 * @return the time when the ping request was send
		 */
		public long getSendTime() {
			return sendTime;
		}
	}

}
