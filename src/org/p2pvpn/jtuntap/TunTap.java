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

package org.p2pvpn.jtuntap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TunTap {
	
	static public void loadLibFromRecsource(String lib, String suffix) throws IOException {
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
		System.out.println(tmp.getCanonicalPath());
		
		System.load(tmp.getCanonicalPath());
	}
	
    static {
		try {
			loadLibFromRecsource("lib/libcTunTap.so", ".so");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private int fd;
    private String dev;
    
    public TunTap() {
        if (1==openTun()) throw new RuntimeException("Could not open tun");
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
    		Process p = Runtime.getRuntime().exec("ifconfig "+dev+" "+ip+" netmask "+subnetmask);
    		System.out.println("IP set successfully ("+p.waitFor()+")");
    	} catch (Exception e) {
    		System.out.println("Could not set IP!");
    		e.printStackTrace();
    	}
    }
}
