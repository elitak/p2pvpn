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
import java.io.Serializable;

public class PeerID implements Comparable<PeerID>, Serializable {
	private static final long serialVersionUID = 1L;
	
	private int address;

	public PeerID(int address) {
		this.address = address;
	}

	public PeerID(String addrStr) {
		this.address = Integer.parseInt(addrStr);
	}

	public PeerID() {
		this((int)(Math.random()*(1<<30)));
	}
	
	public int getAddress() {
		return address;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + address;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PeerID other = (PeerID) obj;
		if (address != other.address)
			return false;
		return true;
	}

	@Override
	public int compareTo(PeerID o) {
		return address - o.address;
	}

	@Override
	public String toString() {
		return ""+address;
	}
}
