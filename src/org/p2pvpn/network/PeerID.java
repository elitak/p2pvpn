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
import java.security.MessageDigest;
import java.util.Arrays;
import org.apache.commons.codec.binary.Base64;
import org.p2pvpn.tools.CryptoUtils;

public class PeerID implements Comparable<PeerID>, Serializable {
	private static final long serialVersionUID = 1L;
	
	private byte[] id;

	public PeerID(byte[] b, boolean hash) {
		if (hash) {
			MessageDigest md = CryptoUtils.getMessageDigest();
			id = md.digest(b);
		} else {
			id = b;
		}
	}

	public PeerID(String addrStr) {
		id = Base64.decodeBase64(addrStr.getBytes());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final PeerID other = (PeerID) obj;
		if (this.id != other.id && (this.id == null || !Arrays.equals(id, other.id))) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}

	public int compareTo(PeerID o) {
		int d = id.length - o.id.length;
		if (d!=0) return d;
		for(int i=0; i<id.length; i++) {
			d = id[i] - o.id[i];
			if (d!=0) return d;
		}
		return 0;
	}
	
	@Override
	public String toString() {
		return new String(Base64.encodeBase64(id, false));
	}
}
