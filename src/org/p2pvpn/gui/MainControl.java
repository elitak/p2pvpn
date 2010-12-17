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

package org.p2pvpn.gui;

import java.awt.HeadlessException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Date;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.Connector;
import org.p2pvpn.network.ConnectorListener;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.TCPConnection;
import org.p2pvpn.network.VPNConnector;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;
import org.p2pvpn.tuntap.TunTap;
import org.p2pvpn.tools.ProfileManager;

/**
 * This class controls everything regarded to the GUI or storing the settings
 * of P2PVPN.
 * @author Wolfgang Ginolas
 */
public class MainControl implements ConnectorListener {
	private static final String DEFAULT_NET_FILE = "default.net";

	private AdvProperties networkCfg;	// the invitations for the current network
	private AdvProperties accessCfg;
	
	private ConnectionManager connectionManager;
	private MainWindow mainWindow;
	private TunTap tuntap;
	
	private int serverPort;				// the local port
	private String name;				// the name of this nide
	private double sendLimit, recLimit;	// bandwidth limit for this node
	private int sendBufferSize;			// the size of the send buffer
	private boolean tcpFlush;			// flush after each packet?
	private String ip;					// the IP of the virtual network adapter
	private boolean popupChat;			// should the chat window popup when a message arrives?

	//private Preferences prefs;			// used to store all settings
        private ProfileManager prefs;

	/**
	 * Create a new MainControl
	 * @param mainWindow the MainWindow
	 */
	public MainControl(MainWindow mainWindow) {


		ProfileManager profile = new ProfileManager();
		prefs = profile.load("default");

		tuntap = null;
		connectionManager = null;
		this.mainWindow = mainWindow;

		serverPort = prefs.getInt("serverPort", 0);
		setName(prefs.get("name", "no name"));
		String accessStr = prefs.get("access", null);
		accessCfg = accessStr==null ? null : new AdvProperties(accessStr);
		String netStr = prefs.get("network", null);
		networkCfg = netStr==null ? null : new AdvProperties(netStr);
		ip = prefs.get("ip", "");
		sendLimit = prefs.getDouble("sendLimit", 0);
		recLimit = prefs.getDouble("recLimit", 0);
		sendBufferSize = prefs.getInt("sendBufferSize", TCPConnection.DEFAULT_MAX_QUEUE);
		tcpFlush = prefs.getBoolean("tcpFlush", TCPConnection.DEFAULT_TCP_FLUSH);

		popupChat = prefs.getBoolean("popupChat", false);
		
		if (accessCfg == null) loadDefaultNet();
	}

	/**
	 * Load the default settings.
	 */
	private void loadDefaultNet() {
		try {
			InputStream in = MainControl.class.getClassLoader().getResourceAsStream(DEFAULT_NET_FILE);
			AdvProperties inv = new AdvProperties();
			inv.load(in);
			String netName = inv.getProperty("network.name");
			if (JOptionPane.YES_OPTION ==
					JOptionPane.showConfirmDialog(null,
					"Your P2PVPN is not part of any network.\n" +
					"Do you want to join '" + netName + "'?", "Default Network",
					JOptionPane.YES_NO_OPTION)) {
				AdvProperties[] ps = calcNetworkAccess(inv);
				networkCfg = ps[0];
				accessCfg = ps[1];
				generateRandomIP();
			}
		} catch (IOException iOException) {
		} catch (HeadlessException headlessException) {
		}
	}

	/**
	 * Called after initialisation and starts the operation of P2PVPN.
	 */
	public void start() {
		changeNet(false);
	}

	/**
	 * Connect to a network.
	 * @param networkCfg the network invitation
	 * @param accessCfg the access initation
	 */
	public void connectToNewNet(AdvProperties networkCfg, AdvProperties accessCfg) {
		this.networkCfg = networkCfg;
		this.accessCfg = accessCfg;
		
		if (accessCfg!=null) generateRandomIP();
		
		changeNet(true);
	}

	/**
	 * Create an random IP address for the virtual network adapter.
	 */
	private void generateRandomIP() {
		try {
			Random random = new Random();
			InetAddress net = InetAddress.getByName(accessCfg.getProperty("network.ip.network"));
			InetAddress subnet = InetAddress.getByName(accessCfg.getProperty("network.ip.subnet"));

			byte[] myIPb = new byte[4];
			byte[] netb = net.getAddress();
			byte[] subnetb = subnet.getAddress();

			// TODO don't create a broadcast address
			for (int i = 0; i < 4; i++) {
				myIPb[i] = (byte) (netb[i] ^ ((~subnetb[i]) & (byte)random.nextInt()));
			}

			ip = (0xFF & myIPb[0]) + "." + (0xFF & myIPb[1]) + "." + (0xFF & myIPb[2]) + "." + (0xFF & myIPb[3]);
		} catch (UnknownHostException ex) {
			Logger.getLogger(MainControl.class.getName()).log(Level.SEVERE, null, ex);
			assert false;
		}
	}

	/**
	 * This is called, when the network has changed. It will setup the new
	 * network and notify other parts of P2PVPN.
	 * @param networkChanged was P2PVPN connectet to another network before
	 *	this method was called?
	 */
	private void changeNet(boolean networkChanged) {
		if (connectionManager!=null) connectionManager.close();
		if (accessCfg!=null) {
			try {
				connectionManager = new ConnectionManager(accessCfg, serverPort);

				try {
					VPNConnector vpnc = VPNConnector.getVPNConnector();
					vpnc.setRouter(connectionManager.getRouter());
					tuntap = vpnc.getTunTap();
					setIp(ip);
				} catch (Throwable e) {
					Logger.getLogger("").log(Level.SEVERE, "", e);
					JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
				connectionManager.getRouter().setLocalPeerInfo("name", name);
				connectionManager.addIPs(accessCfg);

				connectionManager.getConnector().addListener(this);
				if (!networkChanged) addStoredIPs();

				connectionManager.getSendLimit().setBandwidth(sendLimit);
				connectionManager.getRecLimit().setBandwidth(recLimit);
				connectionManager.setSendBufferSize(sendBufferSize);
				connectionManager.setTCPFlush(tcpFlush);

				prefs.put("access", accessCfg.toString());
				if (networkCfg==null) {
					prefs.remove("network");
				} else {
					prefs.put("network", networkCfg.toString());
				}
				prefs.put("ip", ip);
				prefsFlush();
			} catch (Throwable e) {
				Logger.getLogger("").log(Level.SEVERE, "", e);
				JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}		
		}
		mainWindow.networkHasChanged();
	}

	/**
	 * Add the IPs stored in the Preferences to the known IPs list.
	 */
	private void addStoredIPs() {
		String ipStr = prefs.get("knownIPs", "");
		StringTokenizer ips = new StringTokenizer(ipStr, ";");

		while (ips.hasMoreTokens()) {
			try {
				StringTokenizer st = new StringTokenizer(ips.nextToken(), ":");
				String ip = st.nextToken();
				int port = Integer.parseInt(st.nextToken());
				connectionManager.getConnector().addIP(ip, port, null, "stored", "", false);
			} catch (NumberFormatException numberFormatException) {
			}
		}
	}

	/**
	 * Called, when th list of known IPs changes. This method will store
	 * the list in the Preferences.
	 * @param c the Connector
	 */
	public void ipListChanged(Connector c) {
		Connector.Endpoint[] es = c.getIPs();
		String ips = "";
		for (int i = 0; i < es.length; i++) {
			if (i > 0) {
				ips = ips + ";";
			}
			ips = ips + es[i].toString();
		}
		prefs.put("knownIPs", ips);
		prefsFlush();
	}


	/**
	 * Return the name of the peer.
	 * @param peer the peer
	 * @return the name or "?" when the name is unknown
	 */
	public String nameForPeer(PeerID peer) {
		if (connectionManager==null) return "";
		String name = connectionManager.getRouter().getPeerInfo(peer, "name");
		if (name==null) return "?";
		return name;
	}

	/**
	 * Return a short description for a peer.
	 * @param peer the peer
	 * @return the description
	 */
	public String descriptionForPeer(PeerID peer) {
		if (connectionManager==null) return "";
		
		StringBuffer result = new StringBuffer();
		
		result.append("<html>");
		result.append("Name: "+nameForPeer(peer));
		
		String ip = connectionManager.getRouter().getPeerInfo(peer, "vpn.ip");
		if (ip!=null) result.append("<br>IP: "+ip);
		
		if (connectionManager.getRouter().isConnectedTo(peer)) {
			result.append("<br>direct connection");
		} else {
			if (!connectionManager.getLocalAddr().equals(peer)) {
				result.append("<br>indirect connection");
			}
		}
		
		result.append("<br>Peer ID: "+peer);
		result.append("</html>");
		
		return result.toString();
	}

	/**
	 * Flush the Preferences to disk.
	 */
	private void prefsFlush() {

            prefs.flush();
            /*
             *  No need to catch exception, ProfielManager do it now
             *
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			Logger.getLogger("").log(Level.WARNING, null, ex);
		}
             *
             */
	}

	public ConnectionManager getConnectionManager() {
		return connectionManager;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;

		if (tuntap!=null) {
			tuntap.setIP(ip, accessCfg.getProperty("network.ip.subnet"));
			if (connectionManager!=null) {
				connectionManager.getRouter().setLocalPeerInfo("vpn.ip", ip);
			}
			prefs.put("ip", ip);
			prefsFlush();
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		if (connectionManager!=null) {
			connectionManager.getRouter().setLocalPeerInfo("name", name);			
		}
		mainWindow.setNodeName(name);
		prefs.put("name", name);
		prefsFlush();
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
		prefs.putInt("serverPort", serverPort);
		prefsFlush();
	}

	public AdvProperties getAccessCfg() {
		return accessCfg;
	}

	public AdvProperties getNetworkCfg() {
		return networkCfg;
	}


	/**
	 * Generate an access invitation from an network invitation.
	 * @param netCfg the network invitation
	 * @param date the date of expiry
	 * @return the acess invitation
	 */
	public static AdvProperties genereteAccess(AdvProperties netCfg, Date date) {
		PrivateKey netPriv = CryptoUtils.decodeRSAPrivateKey(
				netCfg.getPropertyBytes("secret.network.privateKey", null));

		KeyPair accessKp = CryptoUtils.createEncryptionKeyPair();

		AdvProperties accessCfg = new AdvProperties();
		if (date==null) {
			accessCfg.setProperty("access.expiryDate", "none");
		} else {
			accessCfg.setProperty("access.expiryDate", ""+date.getTime());
		}
		accessCfg.setPropertyBytes("access.publicKey", accessKp.getPublic().getEncoded());
		accessCfg.sign("access.signature", netPriv);
		accessCfg.setPropertyBytes("secret.access.privateKey", accessKp.getPrivate().getEncoded());

		accessCfg.putAll(netCfg.filter("secret", true));

		return accessCfg;
	}

	/**
	 * Find out the type of the given invitation and return a network/access
	 * invitation pair.
	 * @param inv the network invitation
	 * @return an array with two invitations {net, access}.
	 */
	public static AdvProperties[] calcNetworkAccess(AdvProperties inv) {
		AdvProperties net;
		AdvProperties access;

		if (inv.getPropertyBytes("secret.network.privateKey", null)==null) {
			net = null;
			access = inv;
		} else {
			net = inv;
			access = MainControl.genereteAccess(net, null);
		}

		return new AdvProperties[] {net, access};
	}

	public double getRecLimit() {
		return recLimit;
	}

	public void setRecLimit(double recLimit) {
		this.recLimit = recLimit;
		if (connectionManager!=null) connectionManager.getRecLimit().setBandwidth(recLimit);
		prefs.putDouble("recLimit", recLimit);
		prefsFlush();
	}

	public double getSendLimit() {
		return sendLimit;
	}

	public void setSendLimit(double sendLimit) {
		this.sendLimit = sendLimit;
		if (connectionManager!=null) connectionManager.getSendLimit().setBandwidth(sendLimit);
		prefs.putDouble("sendLimit", sendLimit);
		prefsFlush();
	}

	public int getSendBufferSize() {
		return sendBufferSize;
	}

	public void setSendBufferSize(int sendBufferSize) {
		this.sendBufferSize = sendBufferSize;
		if (connectionManager!=null) connectionManager.setSendBufferSize(sendBufferSize);
		prefs.putInt("sendBufferSize", sendBufferSize);
		prefsFlush();
	}

	public boolean isTCPFlush() {
		return tcpFlush;
	}

	public void setTCPFlush(boolean tcpFlush) {
		this.tcpFlush = tcpFlush;
		if (connectionManager!=null) connectionManager.setTCPFlush(tcpFlush);
		prefs.putBoolean("tcpFlush", tcpFlush);
		prefsFlush();
	}

	public boolean isPopupChat() {
		return popupChat;
	}

	public void setPopupChat(boolean popupChat) {
		this.popupChat = popupChat;
		prefs.putBoolean("popupChat", popupChat);
		prefsFlush();
	}
}
