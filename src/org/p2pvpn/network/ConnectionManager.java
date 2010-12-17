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
import org.p2pvpn.network.bittorrent.BitTorrentTracker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.network.bandwidth.TokenBucket;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;
import org.p2pvpn.tools.SocketAddrStr;

/**
 * The ConnectionManager is the central point of the P2PVPN network. It
 * coordinates the different layers of the network.
 * @author Wolfgang Ginolas
 */
public class ConnectionManager implements Runnable {
    final static private String WHATISMYIP_URL = "http://whatismyip.com/automation/n09230945.asp";
	final static private long WHATISMYIP_REFRESH_S = 10*60;

	final static private double SEND_BUCKET_SIZE = 10 * 1024;
    
	private ServerSocket server;						// the ServerSocked to accept connections
	private int serverPort;								// the local port
	private PeerID localAddr;							// the local PeerID
	private ScheduledExecutorService scheduledExecutor;	// a scheduled exicutor used for various tasks
	private Router router;								// the Router
    private Connector connector;						// the Connector
	private UPnPPortForward uPnPPortForward;			// currently not used
	private BitTorrentTracker bitTorrentTracker;		// the BitTorrentTracker
    
	private String whatIsMyIP;							// the local IP returned by whatismyip.com
	
	private AdvProperties accessCfg;					// the access invitation
	private byte[] networkKey;							// network key used for encryption

	private TokenBucket sendLimit, recLimit;			// maximum bandwidth
	private Pinger pinger;								// the Pinger

	private int sendBufferSize;							// the send buffer size
	private boolean tcpFlush;							// flush after each packet send?

	/**
	 * Create a new ConnectionManager
	 * @param accessCfg the access invitation
	 * @param serverPort the local server port
	 */
	public ConnectionManager(AdvProperties accessCfg, int serverPort) {
		sendBufferSize = TCPConnection.DEFAULT_MAX_QUEUE;
		tcpFlush = TCPConnection.DEFAULT_TCP_FLUSH;
		this.serverPort = serverPort;
		this.accessCfg = accessCfg;
		scheduledExecutor = Executors.newScheduledThreadPool(10);
		localAddr = new PeerID(accessCfg.getPropertyBytes("access.publicKey", null), true);
		router = new Router(this);
        connector = new Connector(this);
		bitTorrentTracker = null;
		//uPnPPortForward = new UPnPPortForward(this);
		whatIsMyIP = null;

		sendLimit = new TokenBucket(0, SEND_BUCKET_SIZE);
		recLimit = new TokenBucket(0, SEND_BUCKET_SIZE);
		pinger = new Pinger(this);

		calcNetworkKey();
		
		(new Thread(this, "ConnectionManager")).start();
		
		scheduledExecutor.schedule(new Runnable() {
			public void run() {checkWhatIsMyIP();}
		}, 1, TimeUnit.SECONDS);
	}

	/**
	 * Calculate the network key used for encryption
	 */
	private void calcNetworkKey() {
		// byte[] b = accessCfg.getPropertyBytes("network.publicKey", null);
		byte[] b = accessCfg.getPropertyBytes("network.signature", null);
		MessageDigest md = CryptoUtils.getMessageDigest();
		md.update("secretKey".getBytes());	// make sure the key differs from
											// other hashes created from the publicKey
		networkKey = md.digest(b);
	}

	/**
	 * Find out the IPs of the local network adapters.
	 */
	public void updateLocalIPs() {
        String ipList="";
        String ipv6List="";
        try {
            Enumeration<NetworkInterface> is = NetworkInterface.getNetworkInterfaces();
            while (is.hasMoreElements()) {
                NetworkInterface i = is.nextElement();

				if (!i.getName().toLowerCase().startsWith("tap") &&
						!i.getDisplayName().toLowerCase().startsWith("tap") &&
						!i.getName().equals("lo")) {

					System.out.println("if: '"+i.getName()+"', '"+i.getDisplayName()+"'");
					Enumeration<InetAddress> as = i.getInetAddresses();
					while (as.hasMoreElements()) {
						InetAddress a = as.nextElement();
						if (a instanceof Inet4Address) {
							String s = a.getHostAddress();
							//if (!s.startsWith("127") && !s.equals(router.getPeerInfo(localAddr, "vpn.ip"))) {
							ipList = ipList + " " + s;
							//}
						}
						if (a instanceof Inet6Address) {
							String s = a.getHostAddress();
							ipv6List = ipv6List + " " + s;
						}
					}
				}
            }
        } catch (SocketException ex) {
			Logger.getLogger("").log(Level.WARNING, "", ex);
        }
		if (whatIsMyIP!=null) ipList = ipList + " " + whatIsMyIP;
        router.setLocalPeerInfo("local.port", ""+serverPort);
        router.setLocalPeerInfo("local.ips", ipList.substring(1));
        router.setLocalPeerInfo("local.ip6s", ipv6List.substring(1));
	}

	/**
	 * Periodically check the local IPs.
	 */
    private void checkWhatIsMyIP() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new URL(WHATISMYIP_URL).openConnection().getInputStream()));
            InetAddress a = InetAddress.getByName(in.readLine());
            if (a instanceof Inet4Address) whatIsMyIP = a.getHostAddress();
        } catch (Exception ex) {
			Logger.getLogger("").log(Level.WARNING, "can not determine external address", ex);
        }
        updateLocalIPs();
		scheduledExecutor.schedule(new Runnable() {
			public void run() {checkWhatIsMyIP();}
		}, WHATISMYIP_REFRESH_S, TimeUnit.SECONDS);		
    }
    
	public PeerID getLocalAddr() {
		return localAddr;
	}

	public AdvProperties getAccessCfg() {
		return accessCfg;
	}
	
	public ScheduledExecutorService getScheduledExecutor() {
		return scheduledExecutor;
	}

	/**
	 * Called, when a TCPConnection is established.
	 * @param connection the connection
	 */
	public void newConnection(TCPConnection connection) {
		//Logger.getLogger("").log(Level.INFO, "new connection from/to: "+connection);
		new P2PConnection(this, connection);
	}

	/**
	 * Called, when a new P2PConnectrion is established.
	 * @param p2pConnection
	 */
	public void newP2PConnection(P2PConnection p2pConnection) {
		//Logger.getLogger("").log(Level.INFO, "new P2P connection from/to: "+p2pConnection.getRemoteAddr()
		//		+" ("+p2pConnection.getConnection()+")");
		router.newP2PConnection(p2pConnection);
	}

	/**
	 * A thread to accept connections from other peers.
	 */
	@Override
	public void run() {
		try {
			server = new ServerSocket(serverPort);
			serverPort = server.getLocalPort();
			Logger.getLogger("").log(Level.INFO, "listening on port "+server.getLocalPort());
			
			while (true) {
				Socket s = server.accept();
				new TCPConnection(this, s, networkKey);
			}
		}
		catch (Exception e) {
			Logger.getLogger("").log(Level.SEVERE, "Not listening on "+server.getLocalPort()+" anymore", e);
		}
	}

	/**
	 * Add the IPs stored in the access invitation to the known hosts list.
	 * @param accessCfg the access invitation
	 */
	public void addIPs(AdvProperties accessCfg) {
		connector.addIPs(accessCfg);
		String tracker = accessCfg.getProperty("network.bootstrap.tracker");
		if (tracker!=null) bitTorrentTracker = new BitTorrentTracker(this, tracker);
	}

	/**
	 * Try to connect to the given host.
	 * @param host the host
	 * @param port the port
	 */
	public void connectTo(String host, int port) {
        try {
            connectTo(InetAddress.getByName(host), port);
        } catch (UnknownHostException ex) {
			Logger.getLogger("").log(Level.WARNING, "", ex);
        }
	}

	/**
	 * Try to connect to the given host.
	 * @param host the host
	 * @param port the port
	 */
	public void connectTo(InetAddress host, int port) {
		new ConnectTask(host, port);
	}

	/**
	 * Try to connect to the given host.
	 * @param addr the host and port using the format "hist:port"
	 */
	public void connectTo(String addr) {
		try {
			InetSocketAddress a = SocketAddrStr.parseSocketAddr(addr);
			connectTo(a.getAddress(), a.getPort());
		} catch (Exception e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}

	/**
	 * Stop this network and close all connections
	 */
	public void close() {
		try {
			scheduledExecutor.shutdownNow();
			router.close();
			if (server!=null) server.close();
			//TODO close connections
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}

	/**
	 * A Task which tries to connect another peer
	 */
	private class ConnectTask implements Runnable {
		private InetAddress host;
		private int port;

		/**
		 * Try to connect another peer.
		 * @param host the host
		 * @param port the port
		 */
		public ConnectTask(InetAddress host, int port) {
			this.host = host;
			this.port = port;
			(new Thread(this, "ConnectTask")).start();
		}

		@Override
		public void run() {
			Socket s;
			//connector.addIP(host, port, null, null, "connecting", false);
			try {
				s = new Socket(host, port);
				new TCPConnection(ConnectionManager.this, s, networkKey);
				//connector.addIP(host, port, null, null, "connected", false);
			} catch (Throwable e) {
				//Logger.getLogger("").log(Level.WARNING, host+" "+port);
				//connector.addIP(host, port, null, null, e.getMessage(), false);
			}
		}
	}

	public int getServerPort() {
		return serverPort;
	}

	public Router getRouter() {
		return router;
	}

    public Connector getConnector() {
        return connector;
    }

	public UPnPPortForward getUPnPPortForward() {
		return uPnPPortForward;
	}

	public TokenBucket getSendLimit() {
		return sendLimit;
	}

	public TokenBucket getRecLimit() {
		return recLimit;
	}

	public int getSendBufferSize() {
		return sendBufferSize;
	}

	public void setSendBufferSize(int sendBufferSize) {
		this.sendBufferSize = sendBufferSize;
	}

	public boolean isTCPFlush() {
		return tcpFlush;
	}

	public void setTCPFlush(boolean tcpFlush) {
		this.tcpFlush = tcpFlush;
	}
}
