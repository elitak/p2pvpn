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

package org.p2pvpn.tuntap;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An apstract claas for an virtual network adapter. A different implementation
 * for every operating system is needed.
 * @author Wolfgang Ginolas
 */
public abstract class TunTap {

	private byte[] ip = null;

	/**
	 * Load a libary (*.so or *.dll).
	 * @param libs the libary names
	 * @throws java.io.IOException
	 */
	static void loadLib(String... libs) throws Throwable {
		Throwable e=null;
		for(String lib : libs) {
			try {
				System.load(new File(lib).getCanonicalPath());
				break;
			} catch (Throwable eio) {e = eio;}
		}
		if (e!=null) throw e;
	}

	/*static void loadLibFromRecsource(String lib, String suffix) throws IOException {
		File tmp = File.createTempFile("lib", suffix);
		tmp.deleteOnExit();
		InputStream in = TunTap.class.getClassLoader().getResourceAsStream(lib);
		OutputStream out = new FileOutputStream(tmp);

		byte[] buffer = new byte[1024*8];
		int len;

		while (0<(len = in.read(buffer))) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();

		System.load(tmp.getCanonicalPath());
	}*/

	/**
	 * Return a TunTap object for the currently used operating system.
	 * @return the TunTap object
	 * @throws java.lang.Exception
	 */
    static public TunTap createTunTap() throws Exception {
        String osName = System.getProperty("os.name");
        
        if (osName.startsWith("Windows")) {
            return new TunTapWindows();
        } else if (osName.equals("Linux")) {
            return new TunTapLinux();
        } else {
            throw new Exception("The operating system "+osName+" is not supported!");
        }
    }

	/**
	 * @return the name of the virtuel network device
	 */
    abstract public String getDev();

	/**
	 * Close the device.
	 */
    abstract public void close();

	/**
	 * Send a packet to the virtual network adapter.
	 * @param b the packet
	 * @param len the length of the packet
	 */
    abstract public void write(byte[] b, int len);

	/**
	 * Read a packet from the virtual network adapter.
	 * @param b the packet
	 * @return length if the packet
	 */
    abstract public int read(byte[] b);

	/**
	 * Set the IP address of the virtual network adapter.
	 * @param ip the IP
	 * @param subnetmask the subnet mask
	 */
    public void setIP(String ip, String subnetmask) {
		try {
			this.ip = InetAddress.getByName(ip).getAddress();
		} catch (UnknownHostException ex) {
		}
	}

	/**
	 * Return the last set IP address.
	 * @return the ip address
	 */
	public byte[] getIPBytes() {
		return ip;
	}

}
