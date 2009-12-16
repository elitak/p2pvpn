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

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.network.bittorrent.bencode.BencodeString;

public class Contact {
	public final static int MAX_BAD = 10;

	private BigInteger id;
	private SocketAddress addr;
	private int bad;

	public Contact(BigInteger id, SocketAddress addr) {
		this.id = id;
		this.addr = addr;
		this.bad = 0;
	}

	public Contact(byte[] idS, SocketAddress addr) {
		this(DHT.unsigned(new BigInteger(idS)), addr);
	}

	public Contact(byte[] bs, int off) {
		byte[] id = new byte[20];
		System.arraycopy(bs, off, id, 0, 20);
		byte[] ipa = new byte[4];
		System.arraycopy(bs, off+20, ipa, 0, 4);
		//ipa[3] = bs[off+20];
		//ipa[2] = bs[off+21];
		//ipa[1] = bs[off+22];
		//ipa[0] = bs[off+23];
		InetAddress ip = null;
		try {
			ip = InetAddress.getByAddress(ipa);
		} catch (UnknownHostException ex) {
			ex.printStackTrace();
		}
		int port = ((0xff&bs[off+24]) << 8) + (0xff&bs[off+25]);
		this.id = DHT.unsigned(new BigInteger(id));
		this.addr = new InetSocketAddress(ip, port);
		this.bad = 0;
	}

	public SocketAddress getAddr() {
		return addr;
	}

	public int getBad() {
		return bad;
	}

	public BigInteger getId() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Contact other = (Contact) obj;
		if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 67 * hash + (this.id != null ? this.id.hashCode() : 0);
		return hash;
	}

	@Override
	public String toString() {
		return "("+id+", "+addr+")";
	}
}
