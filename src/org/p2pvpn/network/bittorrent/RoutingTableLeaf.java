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
import java.util.Vector;

public class RoutingTableLeaf extends RoutingTable {
	private static final int MAX_SIZE = 8;


	private Vector<Contact> bucket;

	public RoutingTableLeaf(DHT dht, BigInteger min, BigInteger max) {
		super(dht, min, max);
		this.bucket = new Vector<Contact>();
	}

	private RoutingTable split() {
		BigInteger center = min.add(max).divide(new BigInteger("2"));
		RoutingTableLeaf low = new RoutingTableLeaf(dht, min, center);
		RoutingTableLeaf high = new RoutingTableLeaf(dht, center.add(new BigInteger("1")), max);
		RoutingTableNode node = new RoutingTableNode(dht, min, max, low, high);

		for (Contact c : bucket) {
			node.addContact(c);
		}
		return node;
	}

	private boolean maySplit() {
		return (min.compareTo(dht.getID()) <= 0) && (dht.getID().compareTo(max) <= 0);
	}

	@Override
	public RoutingTable addContact(Contact c) {
		int i = bucket.indexOf(c);
		if (i!=-1) {
			bucket.set(i, c);
			return this;
		}

		if (bucket.size() < MAX_SIZE) {
			bucket.add(c);
			return this;
		}
		
		if (maySplit()) {
			RoutingTable s = split();
			s.addContact(c);
			return s;
		}
		return this;
	}

	@Override
	public RoutingTableLeaf findBucket(BigInteger id) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public RoutingTable removeContact(BigInteger id) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Vector<Contact> getBucket() {
		return bucket;
	}
}
