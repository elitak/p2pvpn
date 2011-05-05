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
 *
 * @author wolfgang
 */
public class BencodeInt implements BencodeObject {
	int i;

	public BencodeInt(int i) {
		this.i = i;
	}

	public int getInt() {
		return i;
	}

	public void write(OutputStream out) throws IOException {
		out.write('i');
		out.write(Integer.toString(i).getBytes());
		out.write('e');
	}

	@Override
	public String toString() {
		return Integer.toString(i);
	}
}
