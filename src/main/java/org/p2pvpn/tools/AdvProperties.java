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
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;

/**
 * Extended Properties. This class allows: Storing long byte arrays,
 * signing etc.
 * @author Wolfgang Ginolas
 */
public class AdvProperties extends Properties {
	private static int SPLIT_LEN = 32;
	
	public AdvProperties() {
		super();
	}

	public AdvProperties(Properties defaults) {
		super(defaults);
	}

	/**
	 * Create Properties from a String (compatible to this.toString)
	 * @param s the String
	 */
	public AdvProperties(String s) {
		this(s.getBytes());
	}

	/**
	 * Create Properties from a byte array
	 * @param b the array
	 */
	public AdvProperties(byte[] b) {
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		try {
			load(in);
		} catch (IOException ex) {}
	}

	/**
	 * Return a value as int
	 * @param key the key
	 * @param def the default value
	 * @return the int
	 */
	public int getPropertyInt(String key, int def) {
		String s = getProperty(key);
		if (s==null) return def;
		
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/**
	 * Set a byte array value.
	 * @param key the key
	 * @param bs the byte array
	 */
	public void setPropertyBytes(String key, byte[] bs) {
		String val = new String(Base64.encodeBase64(bs, true));
		StringTokenizer st = new  StringTokenizer(val, "\n");
		
		int row=0;
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			assert line.indexOf('\r')==line.length()-1;
			line = line.substring(0, line.length()-1);		// remove the '\r'
			setProperty(key+"."+Integer.toString(row, 36), line);
			row++;
		}
	}

	/**
	 * Return a value as byte array.
	 * @param key the key
	 * @param def the default value
	 * @return the byte array
	 */
	public byte[] getPropertyBytes(String key, byte[] def) {
		StringBuffer val = new StringBuffer();
		
		int row=0;
		String line;
		do {
			line = getProperty(key+"."+Integer.toString(row, 36), null);
			
			if (line==null && row==0) return def;
			
			if (line!=null) {
				val.append(line);
				//val.append("\n");
			}
			row++;
		} while(line!=null);
		
		return Base64.decodeBase64(val.toString().getBytes());
	}

	/**
	 * A new version of keys which sorts the keys.
	 * @return sortet keys
	 */
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

	/**
	 * Return the properties as String.
	 * @param comment a comment added to the String
	 * @param removeDate remove the date?
	 * @param hr add horizontal roulers?
	 * @return the String
	 */
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

	/**
	 * Filter the keys.
	 * @param prefix the prefix of the filtered keys
	 * @param exclude exclude the matching keys? (else include)
	 * @return new filtered properties
	 */
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

	/**
	 * Return properties as byte array.
	 * @return the byte array.
	 */
	public byte[] asBytes () {
		try {
            String s = toString(null, true, false);
            s = s.replaceAll("\r", "");
			return s.getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException ex) {
			assert false;
			return null;
		}		
	}

	/**
	 * Sign this properties with the given key.
	 * @param keyName name of the key
	 * @param privateKey the key used for the signature
	 */
	public void sign(String keyName, PrivateKey privateKey) {
		try {
			byte[] data = asBytes();
			Signature signature = CryptoUtils.getSignature();
			signature.initSign(privateKey, CryptoUtils.getSecureRandom());
			signature.update(data);
			setPropertyBytes(keyName, signature.sign());
		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			assert false;
		}
	}

	/**
	 * Verify a signature.
	 * @param keyName name if the signature key.
	 * @param publicKey the public key of the signature
	 * @return signature correct?
	 */
	public boolean verify(String keyName, PublicKey publicKey) {
		try {
			byte[] data = filter(keyName, true).asBytes();
			Signature signature = CryptoUtils.getSignature();
			signature.initVerify(publicKey);
			signature.update(data);
			return signature.verify(getPropertyBytes(keyName, null));
		} catch (Throwable ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			return false;
		}
	}
}
