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

package org.p2pvpn.network;

import org.p2pvpn.tuntap.TunTap;

public class VPNConnector implements Runnable {

	private final static byte IPV4_HIGH = 0x08;
	private final static byte IPV4_LOW = 0x00;

	private TunTap tuntap;
	private Router router;
	private Thread myThread;
	
	private static VPNConnector vpnConnector = null;
	
	private VPNConnector() throws Exception {
		tuntap = TunTap.createTunTap();
		router = null;

		myThread = new Thread(this, "VPNConnector");
		myThread.start();
	}

	public static VPNConnector getVPNConnector() throws Exception {
		if (vpnConnector == null) vpnConnector = new VPNConnector();
		return vpnConnector;
	}
	
	public void setRouter(Router router) {
		this.router = router;
		if (router!=null) router.setVpnConnector(this);
	}

	public TunTap getTunTap() {
		return tuntap;
	}
	
	public void receive(byte[] packet) {
		//System.out.println("VPNConnector.write "+packet.length);
		tuntap.write(packet, packet.length);
	}
	
	/*public void close() {
		tuntap.close();
		myThread.interrupt();
	}*/

	private void forceIP (byte[] packet) {
		if (packet.length>= 14+20) {
			if (packet[12]==IPV4_HIGH && packet[13]==IPV4_LOW) { // is this IPv4?
				byte[] ip = tuntap.getIPBytes();
				System.arraycopy(ip, 0, packet, 26, 4);		// replace the source ip
			}
		}
	}

	@Override
	public void run() {
		byte[] buffer = new byte[2048];
		// TODO close?
		while(true) {
			int len = tuntap.read(buffer);
			//System.out.println("VPNConnector.read "+len);
            if (len>=12) {
                byte[] packet = new byte[len];
                System.arraycopy(buffer, 0, packet, 0, len);
				forceIP(packet);
                if (router!=null) router.send(packet);
            }
		}
	}
}
