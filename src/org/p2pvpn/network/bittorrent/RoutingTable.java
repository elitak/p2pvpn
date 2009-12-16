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

public abstract class RoutingTable {
	protected DHT dht;
	protected BigInteger min, max;

	public RoutingTable(DHT dht, BigInteger min, BigInteger max) {
		this.dht = dht;
		this.min = min;
		this.max = max;
	}

	abstract public RoutingTableLeaf findBucket(BigInteger id);

	public Contact findContact(BigInteger id) {
		for(Contact c : findBucket(id).getBucket()) {
			if (c.getId().equals(id)) return c;
		}
		return null;
	}

	abstract public RoutingTable addContact(Contact c);
	abstract public RoutingTable removeContact(BigInteger id);
}
