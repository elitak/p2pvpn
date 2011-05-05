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

public class RoutingTableNode extends RoutingTable {

	private RoutingTable low, high;
	BigInteger center;

	public RoutingTableNode(DHT dht, BigInteger min, BigInteger max, RoutingTable low, RoutingTable high) {
		super(dht, min, max);
		this.low = low;
		this.high = high;
		this.center = min.add(max).divide(new BigInteger("2"));
	}


	@Override
	public RoutingTable addContact(Contact c) {
		if (c.getId().compareTo(dht.getID()) <= 0)
			low = low.addContact(c);
		else high = high.addContact(c);
		return this;
	}

	@Override
	public RoutingTableLeaf findBucket(BigInteger id) {
		if (id.compareTo(dht.getID()) <= 0)
			return low.findBucket(id);
		else return high.findBucket(id);
	}

	@Override
	public RoutingTable removeContact(BigInteger id) {
		if (id.compareTo(dht.getID()) <= 0)
			return low.removeContact(id);
		else return high.removeContact(id);
	}

}
