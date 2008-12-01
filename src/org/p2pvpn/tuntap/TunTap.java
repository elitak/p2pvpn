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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class TunTap {
	
	static void loadLibFromRecsource(String lib, String suffix) throws IOException {
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
	}

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
    
    abstract public String getDev();
    
    abstract public void close();
    
    abstract public void write(byte[] b, int len);
    
    abstract public int read(byte[] b);
    
    abstract public void setIP(String ip, String subnetmask);
}
