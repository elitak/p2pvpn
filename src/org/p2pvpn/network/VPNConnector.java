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

import org.p2pvpn.tuntap.TunTap;

/**
 * This claas establishes a connection between the virtual network adapter
 * and the Router.
 * @author wolfgang
 */
public class VPNConnector implements Runnable {

	private final static byte IPV4_HIGH = 0x08;
	private final static byte IPV4_LOW = 0x00;
	private final static byte IPV4_UDP = 17;

	private TunTap tuntap;
	private Router router;
	private Thread myThread;
	
	private static VPNConnector vpnConnector = null;

	/**
	 * Create a new VPNConnector.
	 * @throws java.lang.Exception
	 */
	private VPNConnector() throws Exception {
		tuntap = TunTap.createTunTap();
		router = null;

		myThread = new Thread(this, "VPNConnector");
		myThread.start();
	}

	/**
	 * Get the global VPNConnector.
	 * @return
	 * @throws java.lang.Exception
	 */
	public static VPNConnector getVPNConnector() throws Exception {
		if (vpnConnector == null) vpnConnector = new VPNConnector();
		return vpnConnector;
	}

	/**
	 * Set the Router.
	 * @param router the Router.
	 */
	public void setRouter(Router router) {
		this.router = router;
		if (router!=null) router.setVpnConnector(this);
	}

	public TunTap getTunTap() {
		return tuntap;
	}

	/**
	 * Send an packet to the virtual network adapter.
	 * @param packet the packet
	 */
	public void receive(byte[] packet) {
		//System.out.println("VPNConnector.write "+packet.length);
		tuntap.write(packet, packet.length);
	}
	
	/*public void close() {
		tuntap.close();
		myThread.interrupt();
	}*/

/* Headers of a MAC-Packet
 *
 * Offset
 * 0:  Ethernet
 * 14: IP
 * 34: UDP
*/

	/**
	 * Force the correct local IP in an UDP broadcast packet. This is necessary
	 * becaus Windows sometimes uses the wrong sourc IP in breadcast packages.
	 * @param packet the packet
	 */
	private void forceIP (byte[] packet) {
		if (packet.length>= 14+20) {
			if (packet[12]==IPV4_HIGH && packet[13]==IPV4_LOW && packet[14+9]==IPV4_UDP) { // is this IPv4 and UDP?
                byte[] ip = tuntap.getIPBytes();
                if (packet[26] != ip[0] || packet[27] != ip[1] ||
                    packet[28] != ip[2] || packet[29] != ip[3]) {

					int checksum = 0;
                    packet[14+10] = 0;		// set checksum = 0
                    packet[14+11] = 0;
                    System.arraycopy(ip, 0, packet, 26, 4);		// replace the source ip

					for(int i=14; i<34; i+=2) {
						checksum += ((0xFF&packet[i]) << 8) + (0xFF&packet[i+1]);
					}

					while ((checksum & 0xFFFF0000) != 0) {
						checksum = (checksum & 0xFFFF) + (checksum >> 16);
					}

					checksum = ~checksum;

                    packet[14+10] = (byte)(0xFF & (checksum >> 8)); // set the new IP header chacksum
                    packet[14+11] = (byte)(0xFF & checksum);

                    packet[14+20+6] = 0;		// unset UDP checksum
                    packet[14+20+7] = 0;
                    
                }
			}
		}
	}


	/**
	 * A thread the reads packages from the virtual network adapter and sends them
	 * to the Router.
	 */
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
