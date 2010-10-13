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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.tools.AdapterManager;

/**
 * The TunTap class for Windows
 * @author Wolfgang Ginolas
 */
public class TunTapWindows extends TunTap {
    static {
		try {
			loadLib("clib\\libTunTapWindows.dll", "clib\\libTunTapWindows64.dll");
		} catch (Throwable e) {
			Logger.getLogger("").log(Level.SEVERE, "Could not load libTunTapWindows.dll", e);
		}
    }

    private long cPtr;
    private String dev;    

	/**
	 * Create a new TunTapWindows.
	 * @throws java.lang.Exception
	 */
    public TunTapWindows() throws Exception {
        
        /* try to install Win Tap Driver
        Integer erg = openTun();
        if(erg == 1){
            checkAndInstallAdapter();
            if (1==openTun())throw new Exception("ERROR during Install. Try as administrator.");
        } else if (erg == 2){
            throw new Exception("Could not open Virtual Ethernet Adapter!\n" +
				"Make sure the TAP-Win32 driver ist installed."); // TODO More error messages
        }
        */


        if (0!=openTun()) throw new Exception("Could not open Virtual Eternat Adapter!\n" +
				"Make sure the TAP-Win32 driver ist installed."); // TODO More error messages
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
            String[] cmd = {
                "netsh", "interface", "ip", "set", "address", dev, "static", ip, subnetmask
            };
            String[] metric = {
                "netsh", "interface", "ip", "set", "interface", "interface="+dev,
                "metric=10"
            };

            String[] rename = {
                "netsh", "interface", "set", "interface", "name="+dev,
                "newname=P2P VPN"
            };

            Process p = Runtime.getRuntime().exec(cmd);
            p = Runtime.getRuntime().exec(metric);
            p = Runtime.getRuntime().exec(rename);
    		//System.out.println("IP set successfully ("+p.waitFor()+")");
            // netsh takes a long time... don't wait for it
    	} catch (Exception e) {
			Logger.getLogger("").log(Level.WARNING, "Could not set IP!", e);
    	}        
    }

    public Boolean checkAndInstallAdapter() {
        AdapterManager adapter = new AdapterManager();
        if(adapter.install())
            return true;

        return false;
    }
}
