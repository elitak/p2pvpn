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

package org.p2pvpn.tuntap;

import java.io.IOException;

/**
 *
 * @author Wolfgang
 */
public class TunTapWindows extends TunTap {
    static {
        //System.load("C:\\Dokumente und Einstellungen\\Wolfgang\\Desktop\\sf\\tapWindows\\trunk\\dist\\Debug\\Cygwin-Windows\\libTunTapWindows.dll");
		try {
			loadLibFromRecsource("lib/libTunTapWindows.dll", ".dll");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    private long cPtr;
    private String dev;    

    public TunTapWindows() throws Exception {
        if (1==openTun()) throw new Exception("Could not open tun");
    }
    
    public String getDev() {
        return dev;
    }
    
    private native int openTun();
    
    public native void close();
    
    public native void write(byte[] b, int len);
    
    public native int read(byte[] b);

    public void setIP(String ip, String subnetmask) {
    	try {
            String[] cmd = {
                "netsh", "interface", "ip", "set", "address", dev, "static", ip, subnetmask
            };
    		Process p = Runtime.getRuntime().exec(cmd);
    		System.out.println("IP set successfully ("+p.waitFor()+")");
    	} catch (Exception e) {
    		System.out.println("Could not set IP!");
    		e.printStackTrace();
    	}
    }
    
}
