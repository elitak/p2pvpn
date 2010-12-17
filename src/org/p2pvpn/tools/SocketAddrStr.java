/*
    Copyright 2009 Wolfgang Ginolas

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

package org.p2pvpn.tools;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SocketAddrStr {
	public static final Pattern ipv4 = Pattern.compile("\\s*([^:]+):(\\d+)\\s*");
	public static final Pattern ipv6 = Pattern.compile("\\s*\\[([^\\]]+)\\]:(\\d+)\\s*");
	
	public static String socketAddrToStr(InetSocketAddress sAddr) {
		InetAddress a = sAddr.getAddress();
		if (a instanceof Inet4Address) {
			return a.getHostAddress()+":"+sAddr.getPort();
		} else if (a instanceof Inet6Address) {
			return "["+a.getHostAddress()+"]:"+sAddr.getPort();
		} else return sAddr.toString();
	}

	public static InetSocketAddress parseSocketAddr(String s) throws Exception {
		String host=null, port=null;
		s = s.trim();
		Matcher m4 = ipv4.matcher(s);
		Matcher m6 = ipv6.matcher(s);
		if (m4.matches()) {
			host = m4.group(1);
			port = m4.group(2);
		} else if (m6.matches()) {
			host = m6.group(1);
			port = m6.group(2);
		}

		if (host!=null && port!=null) {
			return new InetSocketAddress(host, Integer.parseInt(port));
		}

		throw new Exception("'"+s+"' is not a valid socket address");
	}
}
