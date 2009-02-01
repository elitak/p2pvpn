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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Wolfgang
 */
public class TunTapLinux extends TunTap {
    static {
		try {
			loadLibFromRecsource("clib/libTunTapLinux.so", ".so");
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
    }

    private int fd;
    private String dev;    

    public TunTapLinux() throws Exception {
        if (1==openTun()) throw new Exception("Could not open '/dev/net/tun!'\n" +
				"Please run this application as root.");
    }
    
    public String getDev() {
        return dev;
    }
    
    private native int openTun();
    
    public native void close();
    
    public native void write(byte[] b, int len);
    
    public native int read(byte[] b);
    
    public void setIP(String ip, String subnetmask) {
		super.setIP(ip, subnetmask);
    	try {
    		Process p = Runtime.getRuntime().exec("ifconfig "+dev+" "+ip+" netmask "+subnetmask);
			Logger.getLogger("").log(Level.INFO, "IP set successfully ("+p.waitFor()+")");
    	} catch (Exception e) {
			Logger.getLogger("").log(Level.WARNING, "Could not set IP!", e);
    	}
    }
    
}
