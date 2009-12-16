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

package org.p2pvpn.network.bittorrent.bencode;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A byte array that can easily convertet to/from a String.
 */
public class BencodeString implements BencodeObject {
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

	public void write(OutputStream out) throws IOException {
		out.write(Integer.toString(bytes.length).getBytes());
		out.write(':');
		out.write(bytes);
	}
}