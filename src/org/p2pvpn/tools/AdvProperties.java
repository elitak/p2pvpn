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

package org.p2pvpn.tools;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;


public class AdvProperties extends Properties {
	private static final long serialVersionUID = -743572689101055639L;

	public AdvProperties() {
		super();
	}

	public AdvProperties(Properties defaults) {
		super(defaults);
	}

	public AdvProperties(String s) {
		ByteArrayInputStream in = new ByteArrayInputStream(s.getBytes());
		try {
			load(in);
		} catch (IOException ex) {}
	}
	
	public int getPropertyInt(String key, int def) {
		String s = getProperty(key);
		if (s==null) return def;
		
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}
	
	@Override
	public String toString() {
		return toString(null, false);
	}
	
	public String toString(String comment, boolean hr) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			store(out, comment);
		} catch (IOException ex) {}
		if (hr) {
			return "#==============================\n" +
				out.toString() +
				"#==============================\n";
		} else {
			return out.toString();
		}
	}	
}
