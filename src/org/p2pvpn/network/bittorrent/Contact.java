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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class Contact {
	public final static int MAX_BAD = 10;

	private BigInteger id;
	private SocketAddress addr;

	public static InetSocketAddress parseSocketAddress(byte[] bs, int off, int len) {
		if (len==4+2 || len==16+2) {
			int ipLen = len-2;
			byte[] ipa = new byte[ipLen];
			System.arraycopy(bs, off, ipa, 0, ipLen);
			InetAddress ip = null;
			try {
				ip = InetAddress.getByAddress(ipa);
			} catch (UnknownHostException ex) {
				ex.printStackTrace();
			}
			int port = ((0xff&bs[off+ipLen]) << 8) + (0xff&bs[off+ipLen+1]);
			return new InetSocketAddress(ip, port);
		} else return null;
	}

	public Contact(BigInteger id, SocketAddress addr) {
		this.id = id;
		this.addr = addr;
	}

	public Contact(byte[] idS, SocketAddress addr) {
		this(DHT.unsigned(new BigInteger(idS)), addr);
	}

	public Contact(byte[] bs, int off, int len) {
		byte[] id = new byte[20];
		System.arraycopy(bs, off, id, 0, 20);
		this.id = DHT.unsigned(new BigInteger(id));
		this.addr = parseSocketAddress(bs, off+20, len-20);
	}

	public SocketAddress getAddr() {
		return addr;
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
