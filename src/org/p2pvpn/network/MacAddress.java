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

import java.util.StringTokenizer;

public class MacAddress {
	private long address;

	public MacAddress(byte[] address, int off) {
		setAddress(address, off);
	}
	
	public MacAddress(byte[] address) {
		setAddress(address);
	}
	
	public MacAddress(String s) {
		byte[] addr = new byte[6];
		StringTokenizer st = new StringTokenizer(s, ":");
		int i;
		
		i=0;
		while(i<6 && st.hasMoreTokens()) {
			addr[i] = (byte)Integer.parseInt(st.nextToken(), 16);
			i++;
		}
		
		setAddress(addr);
	}
	
	public void setAddress(byte[] address, int off) {
		assert address.length>=off+6;
		
		this.address = 0;
		for(int i=0; i<6; i++) {
			this.address <<= 8;
			this.address += ((int)address[i+off]) & 0xFF;
		}
	}

	public void setAddress(byte[] address) {
		setAddress(address, 0);
	}

	public byte[] getAddress() {
		byte[] result = new byte[6];
		
		long a = address;
		
		for(int i=5; i>=0; i--) {
			result[i] = (byte)(a & 0xFF);
			a >>= 8;
		}
		return result;
	}
	
	public boolean isBroadcast() {
		return 0 != (address & 0x010000000000l);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (address ^ (address >>> 32));
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
		MacAddress other = (MacAddress) obj;
		if (address != other.address)
			return false;
		return true;
	}

	public String toString() {
		byte[] a = getAddress();
		StringBuffer result = new StringBuffer("");
		
		for(int i=0; i<6; i++) {
			if (i!=0) result.append(':');
			int b = ((int)a[i]) & 0xFF; 
			if (b < 0x10) result.append('0');
			result.append(Integer.toString(b, 16));
		}
		
		return result.toString();
	}
	
}
