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
import java.io.UnsupportedEncodingException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AdvProperties extends Properties {
	private static final long serialVersionUID = -743572689101055639L;

	private static int SPLIT_LEN = 32;
	
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
	
	public void setPropertyBytes(String key, byte[] bs) {
		StringBuffer v = new StringBuffer();
		
		for(int i=0; i<bs.length; i++) {
			int b = ((int)bs[i]) & 0xFF; 
			if (b < 0x10) v.append('0');
			v.append(Integer.toString(b, 16));
		}
		
		setProperty(key, v.toString());
	}
	
	public byte[] getPropertyBytes(String key, byte[] def) {
		String s = getProperty(key);
		if (s==null) return def;
		if (s.length() % 2 != 0) return def;
		
		int len = s.length() / 2;
		byte[] result = new byte[len];
		
		try {
			for (int i = 0; i < len; i++) {
				result[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
			}
		} catch (NumberFormatException numberFormatException) {
			return def;
		}
		
		return result;
	}
	
	public void setPropertySplitBytes(String key, byte[] bs) {
		int rowI=0;
		int pos=0;
		
		do {
			int len = Math.min(bs.length - pos, SPLIT_LEN);
			byte[] part = new byte[len];
			System.arraycopy(bs, pos, part, 0, len);
			setPropertyBytes(key+"."+rowI, part);
			
			rowI++;
			pos += len;
		} while (pos<bs.length);
	}
	
	public byte[] getPropertySplitBytes(String key, byte[] def) {
		byte[] result = new byte[0];
		int rowI = 0;
		byte[] row;
		
		do {
			row = getPropertyBytes(key+"."+rowI, null);
			if (row==null && rowI==0) return def;
			
			if (row!=null) {
				byte [] newRes = new byte[result.length + row.length];
				System.arraycopy(result, 0, newRes, 0, result.length);
				System.arraycopy(row, 0, newRes, result.length, row.length);
				result = newRes;
			}
			rowI++;
		} while (row!=null);
		
		return result;
	}
	
	@Override
	public synchronized Enumeration keys() {
		Enumeration keysEnum = super.keys();
		Vector keyList = new Vector();
		while(keysEnum.hasMoreElements()){
			keyList.add(keysEnum.nextElement());
		}
		Collections.sort(keyList);
		return keyList.elements();
	}

	
	@Override
	public String toString() {
		return toString(null, false, false);
	}
	
	public String toString(String comment, boolean removeDate, boolean hr) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String result;
		
		try {
			store(out, comment);
		} catch (IOException ex) {}
		
		result = out.toString();
		
		if (removeDate) {
			result = result.substring(result.indexOf('\n')+1);
		}
		
		if (hr) {
			result = "#==============================\n" +
				result +
				"#==============================\n";
		}
		
		return result;
	}
	
	public AdvProperties filter(String prefix, boolean exclude) {
		AdvProperties result = new AdvProperties();
		
		for(Object o : keySet()) {
			boolean hit = o.toString().startsWith(prefix);
			
			if (exclude) hit = !hit;
			
			if (hit) {
				result.put(o, get(o));
			}
		}
		return result;
	}
	
	private byte[] getData () {
		try {
			return toString(null, true, false).getBytes("UTF-8");
		} catch (UnsupportedEncodingException ex) {
			assert false;
			return null;
		}		
	}
	
	public void sign(String keyName, PrivateKey privateKey) {
		try {
			byte[] data = getData();
			Signature signature = CryptoUtils.getSignature();
			signature.initSign(privateKey, CryptoUtils.getSecureRandom());
			signature.update(data);
			setPropertySplitBytes(keyName, signature.sign());
		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			assert false;
		}
	}
	
	public boolean verify(String keyName, PublicKey publicKey) {
		try {
			byte[] data = filter(keyName, true).getData();
			Signature signature = CryptoUtils.getSignature();
			signature.initVerify(publicKey);
			signature.update(data);
			return signature.verify(getPropertySplitBytes(keyName, null));
		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			return false;
		}
	}
}
