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

import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.net.URLCodec;
import org.p2pvpn.tools.CryptoUtils;

public class BitTorrentTracker implements Runnable {
	private static final int REFRESH_S = 10 * 60;

	ConnectionManager connectionManager;
	String tracker;

	public BitTorrentTracker(ConnectionManager connectionManager, String tracker) {
		this.connectionManager = connectionManager;
		this.tracker = tracker;
		schedule(1);
	}

	private void schedule(int seconds) {
		connectionManager.getScheduledExecutor().schedule(this, seconds, TimeUnit.SECONDS);
	}

	private Map<Object, Object> trackerRequest(byte[] hash, int port) throws MalformedURLException, IOException {
		String sUrl = tracker + "?info_hash=" + new String(new URLCodec().encode(hash))
				+ "&port=" + port + "&compact=1";

		URL url = new URL(sUrl);
		PushbackInputStream in = new PushbackInputStream(url.openStream());
		return (Map<Object, Object>) parseBencode(in);
	}

	private byte[] networkHash(int maxLen) {
		byte[] b = connectionManager.getAccessCfg().getPropertyBytes("network.publicKey", null);
		MessageDigest md = CryptoUtils.getMessageDigest();
		md.update("BitTorrent".getBytes());	// make shure the key differs from
											// other hashes created from the publicKey
		byte[] hash = md.digest(b);
		byte[] result = new byte[Math.min(maxLen, hash.length)];
		System.arraycopy(hash, 0, result, 0, result.length);
		return result;
	}

	public void run() {
		int nextRequest = REFRESH_S;
		try {
			Map<Object, Object> res = trackerRequest(networkHash(20), connectionManager.getServerPort());

			Integer minInterval = (Integer)res.get(new BencodeString("min interval"));
			if (minInterval!=null) nextRequest = minInterval;

			byte[] peers = ((BencodeString)res.get(new BencodeString("peers"))).getBytes();

			for (int i=0; i<peers.length; i+=6) {
				byte[] ipb = new byte[4];
				System.arraycopy(peers, i, ipb, 0, 4);
				InetAddress ip = Inet4Address.getByAddress(ipb);
				int portLow = 0xFF & (int)peers[i+5];
				int portHi  = 0xFF & (int)peers[i+4];
				int port = (portHi << 8) + portLow;
				Logger.getLogger("").log(Level.INFO, "ip from tracker: "+ip+":"+port);
				connectionManager.getConnector().addIP(ipb, port, null, "BitTorrent", false);
			}

		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
		}

		schedule(nextRequest);
	}

	private Object parseBencode(PushbackInputStream in) throws IOException {
		int first = in.read();
		in.unread(first);

		switch (first) {
			case 'i':
				return parseBencodeInt(in);

			case '0': case '1':	case '2': case '3': case '4':
			case '5': case '6': case '7': case '8': case '9':
				return parseBencodeString(in);

			case 'l':
				return parseBencodeList(in);

			case 'd':
				return parseBencodeMap(in);

			default:
				throw new IOException("no bencode: illecal char: "+ ((char)first));
		}
	}

	private Object parseBencodeInt(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'i');
		return parseBencodeReadInt(in);
	}

	private int parseBencodeReadInt(PushbackInputStream in) throws IOException {
		int result=0;

		while (true) {
			int b=in.read();

			if (b<'0' || b>'9') break;

			result = result * 10 + (b-'0');
		}
		return result;
	}

	private Object parseBencodeString(PushbackInputStream in) throws IOException {
		int len = parseBencodeReadInt(in);

		byte[] bs = new byte[len];
		int pos = 0;

		while(pos<len) {
			pos += in.read(bs, pos, bs.length-pos);
		}

		return new BencodeString(bs);
	}

	private Object parseBencodeList(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'l');

		int first;
		Vector<Object> result = new Vector<Object>();

		while ('e'!= (first = in.read())) {
			in.unread(first);
			result.add(parseBencode(in));
		}
		return result.toArray();
	}

	private Object parseBencodeMap(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'd');

		int first;
		HashMap<Object, Object> result = new HashMap<Object, Object>();

		while ('e'!= (first = in.read())) {
			in.unread(first);
			Object key = parseBencode(in);
			Object val = parseBencode(in);

			result.put(key, val);
		}
		return result;
	}

	private void parseBencodeExpect(PushbackInputStream in, int b) throws IOException {
		int bIn = in.read();
		if (b != bIn) {
			throw new IOException("no bencode: illecal char: "+ ((char)bIn)+" ("+bIn+") expected: "+((char)b));
		}
	}

	public class BencodeString {
		byte[] bytes;

		public BencodeString(byte[] bytes) {
			this.bytes = bytes;
		}

		public BencodeString(String s) {
			bytes = s.getBytes();
		}

		public byte[] getBytes() {
			return bytes;
		}

		@Override
		public boolean equals(Object obj) {
			return toString().equals(obj.toString());
		}

		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		@Override
		public String toString() {
			return new String(bytes);
		}
	}
}
