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

package org.p2pvpn.network.bittorrent.bencode;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

public class Bencode {

	/**
	 * Parse a Bencode string
	 * @param in the input stream
	 * @return the result. The type is Integer, BencodeString,
	 * Vector&lt;Object&gt; or HashMap&lt;Object, Object&gt;
	 * @throws java.io.IOException
	 */
	static public BencodeObject parseBencode(InputStream in) throws IOException {
		return parseBencode(new PushbackInputStream(in));
	}

	/**
	 * Parse a Bencode string
	 * @param in the input stream
	 * @return the result. The type is Integer, BencodeString,
	 * Vector&lt;Object&gt; or HashMap&lt;Object, Object&gt;
	 * @throws java.io.IOException
	 */
	static public BencodeObject parseBencode(PushbackInputStream in) throws IOException {
		int first = in.read();
		in.unread(first);

		switch (first) {
			case 'i':
				return parseBencodeInt(in);

			case '0': case '1':	case '2': case '3': case '4':
			case '5': case '6': case '7': case '8': case '9':
				return parseBencodeString(in);

			case 'l':
				return parseBencodeList(in);

			case 'd':
				return parseBencodeMap(in);

			default:
				throw new IOException("no bencode: illecal char: "+ ((char)first));
		}
	}

	/**
	 * Parse an Mencode integer.
	 * @param in the inputstream
	 * @return the Integer
	 * @throws java.io.IOException
	 */
	static private BencodeObject parseBencodeInt(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'i');
		return new BencodeInt(parseBencodeReadInt(in));
	}

	/**
	 * Read an Integer from the stream
	 * @param in the input stream
	 * @return the int
	 * @throws java.io.IOException
	 */
	static private int parseBencodeReadInt(PushbackInputStream in) throws IOException {
		int result=0;

		while (true) {
			int b=in.read();

			if (b<'0' || b>'9') break;

			result = result * 10 + (b-'0');
		}
		return result;
	}

	/**
	 * Parse an BencodeString
	 * @param in the inputstream
	 * @return the BencodeString
	 * @throws java.io.IOException
	 */
	static private BencodeObject parseBencodeString(PushbackInputStream in) throws IOException {
		int len = parseBencodeReadInt(in);

		byte[] bs = new byte[len];
		int pos = 0;

		while(pos<len) {
			pos += in.read(bs, pos, bs.length-pos);
		}

		return new BencodeString(bs);
	}

	/**
	 * Parse an bencode list
	 * @param in the input stream
	 * @return the Vector
	 * @throws java.io.IOException
	 */
	static private BencodeObject parseBencodeList(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'l');

		int first;
		BencodeList result = new BencodeList();

		while ('e'!= (first = in.read())) {
			in.unread(first);
			result.add(parseBencode(in));
		}
		return result;
	}

	/**
	 * Parse an bencode map
	 * @param in the input stream
	 * @return the HashMap
	 * @throws java.io.IOException
	 */
	static private BencodeObject parseBencodeMap(PushbackInputStream in) throws IOException {
		parseBencodeExpect(in, 'd');

		int first;
		BencodeMap result = new BencodeMap();

		while ('e'!= (first = in.read())) {
			in.unread(first);
			BencodeString key = (BencodeString)parseBencode(in);
			BencodeObject val = parseBencode(in);

			result.put(key, val);
		}
		return result;
	}

	/**
	 * Expect the given byte in the stream
	 * @param in the input stream
	 * @param b the expected byte
	 * @throws java.io.IOException
	 */
	static private void parseBencodeExpect(PushbackInputStream in, int b) throws IOException {
		int bIn = in.read();
		if (b != bIn) {
			throw new IOException("no bencode: illecal char: "+ ((char)bIn)+" ("+bIn+") expected: "+((char)b));
		}
	}
}
