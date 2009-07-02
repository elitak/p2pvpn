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

package org.p2pvpn.network;

/**
 * This was an attempt to penetrate NAT with UpNP. Currently not used.
 * @author Wolfgang Ginolas
 */
public class UPnPPortForward {

}

/*
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sbbi.upnp.Discovery;
import net.sbbi.upnp.impls.InternetGatewayDevice;


public class UPnPPortForward implements Runnable {
	
	private static final long UPDATE_S = 10*60;
	private static final int LEASEDURATION_S = 0;
	private static final int DISCOVERY_TIMEOUT_MS = 5 * 1000;
	
	ConnectionManager connectionManager;
	
	private Vector<UPnPPortForwardListener> listeners;
	
	private InternetGatewayDevice igd;
	private String externalIP;
	private boolean mapped;
	private String error;

	public UPnPPortForward(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		listeners = new Vector<UPnPPortForwardListener>();
		
		connectionManager.getScheduledExecutor().schedule(this, 1, TimeUnit.SECONDS);
	}

	private InternetGatewayDevice checkInterface(NetworkInterface iface) throws IOException {
		InternetGatewayDevice[] igds = InternetGatewayDevice.getDevices(
			DISCOVERY_TIMEOUT_MS, Discovery.DEFAULT_TTL, Discovery.DEFAULT_MX, iface);
		if (igds!=null && igds.length>0) {
			return igds[0];
		} else {
			return null;
		}
	}
	
	public void run() {
		igd = null;
		mapped = false;
		error = null;
		externalIP = null;
		NetworkInterface iface = null;
		
		try {
			Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
			while (ifs.hasMoreElements() && igd == null) {
				NetworkInterface i = ifs.nextElement();
				InternetGatewayDevice cigd = null;
				try {
					cigd = checkInterface(i);
				} catch (IOException ex) {
				}
				if (cigd != null) {
					igd = cigd;
					iface = i;
				}
			}
			
			if (igd!=null) {
				Enumeration<InetAddress> ips = iface.getInetAddresses();
				String myIP = null;
				while (ips.hasMoreElements()) {
					InetAddress ip = ips.nextElement();
					if (ip instanceof Inet4Address) {
						myIP = ip.getHostAddress();
					}
				}
				if (myIP!=null) {
					int port = connectionManager.getServerPort();
					mapped = igd.addPortMapping("P2PVPN", null, port, port, myIP, LEASEDURATION_S, "TCP");
				}
			}
			
			
		} catch (Throwable t) {
			error = t.getMessage();
			Logger.getLogger("").log(Level.WARNING, "UPnP error", t);
		}
		
		updateListeners();
		connectionManager.getScheduledExecutor().schedule(this, UPDATE_S, TimeUnit.SECONDS);
	}
	
	private void updateListeners() {
		synchronized (listeners) {
			for(UPnPPortForwardListener l : listeners) {
				try {
					l.upnpChanged(this);
				} catch (Throwable t) {
					Logger.getLogger("").log(Level.WARNING, "", t);
				}
			}
		}
	}
	
	public void addListener(UPnPPortForwardListener l) {
		synchronized (listeners) {
			listeners.add(l);
		}
	}

	public void removeListener(UPnPPortForwardListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	public String getError() {
		return error;
	}

	public String getExternalIP() {
		return externalIP;
	}

	public InternetGatewayDevice getIgd() {
		return igd;
	}

	public boolean isMapped() {
		return mapped;
	}


}
*/