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

import org.p2pvpn.network.bittorrent.bencode.BencodeString;
import org.p2pvpn.network.bittorrent.bencode.Bencode;
import org.p2pvpn.network.*;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.net.URLCodec;
import org.p2pvpn.network.bittorrent.bencode.BencodeInt;
import org.p2pvpn.tools.CryptoUtils;

/**
 * This class connects to a BitTorrent tracker, to find other peers.
 * (http://www.bittorrent.org/beps/bep_0003.html)
 * @author Wolfgang Ginolas
 */
public class BitTorrentTracker implements Runnable {
	private static final int REFRESH_S = 10 * 60;

	ConnectionManager connectionManager;
	String tracker;
    byte[] peerId;

	/**
	 * Create a new BitTorrentTracker object which periodically polls an
	 * tracker to find other peers.
	 * @param connectionManager the ConnectionManager
	 * @param tracker the trakcer url
	 */
	public BitTorrentTracker(ConnectionManager connectionManager, String tracker) {
		this.connectionManager = connectionManager;
		this.tracker = tracker;
        peerId = new byte[20];
        new Random().nextBytes(peerId);
		schedule(1);
	}

	/**
	 * Schedule a tracker poll
	 * @param seconds poll in <code>secongs</codee> seconds
	 */
	private void schedule(int seconds) {
		connectionManager.getScheduledExecutor().schedule(this, seconds, TimeUnit.SECONDS);
	}

	/**
	 * Poll the tracker.
	 * @param hash the hash of the cuttent network
	 * @param port the local port
	 * @return a Bencode-Map
	 * @throws java.net.MalformedURLException
	 * @throws java.io.IOException
	 */
	private Map<Object, Object> trackerRequest(byte[] hash, int port) throws MalformedURLException, IOException {
		String sUrl = tracker + "?info_hash=" + new String(new URLCodec().encode(hash))
				+ "&port=" + port + "&compact=1&peer_id=" + new String(new URLCodec().encode(peerId)) +
                "&uploaded=0&downloaded=0&left=100";

		//System.out.println(sUrl);

		URL url = new URL(sUrl);
		PushbackInputStream in = new PushbackInputStream(url.openStream());
		return (Map<Object, Object>) Bencode.parseBencode(in);
	}

	/**
	 * Calculate a hash for the network, using the pubkicKey of the net
	 * @param maxLen length of the hash
	 * @return the hash
	 */
	private byte[] networkHash(int maxLen) {
		byte[] b = connectionManager.getAccessCfg().getPropertyBytes("network.signature", null);
		MessageDigest md = CryptoUtils.getMessageDigest();
		md.update("BitTorrent".getBytes());	// make sure the key differs from
											// other hashes created from the publicKey
		byte[] hash = md.digest(b);
		byte[] result = new byte[Math.min(maxLen, hash.length)];
		System.arraycopy(hash, 0, result, 0, result.length);
		return result;
	}

	/**
	 * Poll the tracker and add the returned IPs to the known hosts list.
	 */
	public void run() {
		int nextRequest = REFRESH_S;
		try {
			Map<Object, Object> res = trackerRequest(networkHash(20), connectionManager.getServerPort());

			BencodeInt minInterval = (BencodeInt)res.get(new BencodeString("min interval"));
			if (minInterval!=null) nextRequest = minInterval.getInt();

			byte[] peers = ((BencodeString)res.get(new BencodeString("peers"))).getBytes();

			for (int i=0; i<peers.length; i+=6) {
				byte[] ipb = new byte[4];
				System.arraycopy(peers, i, ipb, 0, 4);
				InetAddress ip = Inet4Address.getByAddress(ipb);
				int portLow = 0xFF & (int)peers[i+5];
				int portHi  = 0xFF & (int)peers[i+4];
				int port = (portHi << 8) + portLow;
				//Logger.getLogger("").log(Level.INFO, "ip from tracker: "+ip+":"+port);
				connectionManager.getConnector().addIP(ipb, port, null, "BitTorrent", "",false);
			}

		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
		}

		schedule(nextRequest);
	}
}
