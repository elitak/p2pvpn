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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
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


public class ConnectionManager implements Runnable {
    final static private String WHATISMYIP_URL = "http://whatismyip.com/automation/n09230945.asp";
	final static private long WHATISMYIP_REFRESH_S = 10*60;

	final static private double SEND_BUCKET_SIZE = 10 * 1024;
    
	private ServerSocket server;
	private int serverPort;
	private PeerID localAddr;
	private ScheduledExecutorService scheduledExecutor;
	private Router router;
    private Connector connector;
	private UPnPPortForward uPnPPortForward;
	private BitTorrentTracker bitTorrentTracker;
    
	private String whatIsMyIP;
	
	private AdvProperties accessCfg;
	private byte[] networkKey;

	private TokenBucket sendLimit, recLimit;

	private int sendBufferSize;
	private boolean tcpFlush;
	
	public ConnectionManager(AdvProperties accessCfg, int serverPort) {
		this.serverPort = serverPort;
		this.accessCfg = accessCfg;
		scheduledExecutor = Executors.newScheduledThreadPool(1);
		localAddr = new PeerID(accessCfg.getPropertyBytes("access.publicKey", null), true);
		router = new Router(this);
        connector = new Connector(this);
		bitTorrentTracker = null;
		//uPnPPortForward = new UPnPPortForward(this);
		whatIsMyIP = null;

		sendLimit = new TokenBucket(0, SEND_BUCKET_SIZE);
		recLimit = new TokenBucket(0, SEND_BUCKET_SIZE);

		calcNetworkKey();
		
		(new Thread(this, "ConnectionManager")).start();
		
		scheduledExecutor.schedule(new Runnable() {
			public void run() {checkWhaiIsMyIP();}
		}, 1, TimeUnit.SECONDS);
	}

	private void calcNetworkKey() {
		byte[] b = accessCfg.getPropertyBytes("network.publicKey", null);
		MessageDigest md = CryptoUtils.getMessageDigest();
		md.update("secretKey".getBytes());	// make shure the key differs from
											// other hashes created from the publicKey
		networkKey = md.digest(b);
	}
	
	public void updateLocalIPs() {
        String ipList="";
        try {
            Enumeration<NetworkInterface> is = NetworkInterface.getNetworkInterfaces();
            while (is.hasMoreElements()) {
                NetworkInterface i = is.nextElement();
                Enumeration<InetAddress> as = i.getInetAddresses();

                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address) {
                        String s = a.getHostAddress();
                        if (!s.startsWith("127") && !s.equals(router.getPeerInfo(localAddr, "vpn.ip"))) {
                            ipList = ipList + " " + s;
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
	}
    
    private void checkWhaiIsMyIP() {
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
			public void run() {checkWhaiIsMyIP();}
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

	public void newConnection(TCPConnection connection) {
		Logger.getLogger("").log(Level.INFO, "new connection from/to: "+connection);
		new P2PConnection(this, connection);
	}

	public void newP2PConnection(P2PConnection p2pConnection) {
		Logger.getLogger("").log(Level.INFO, "new P2P connection from/to: "+p2pConnection.getRemoteAddr()
				+" ("+p2pConnection.getConnection()+")");
		router.newP2PConnection(p2pConnection);
	}
	
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

	public void addIPs(AdvProperties accessCfg) {
		connector.addIPs(accessCfg);
		String tracker = accessCfg.getProperty("network.bootstrap.tracker");
		if (tracker!=null) bitTorrentTracker = new BitTorrentTracker(this, tracker);
	}

	public void connectTo(String host, int port) {
        try {
            connectTo(InetAddress.getByName(host), port);
        } catch (UnknownHostException ex) {
			Logger.getLogger("").log(Level.WARNING, "", ex);
        }
	}
	
	public void connectTo(InetAddress host, int port) {
		new ConnectTask(host, port);
	}
	
	public void connectTo(String addr) {
		try {
			StringTokenizer st = new StringTokenizer(addr, ":");
			
			String host = st.nextToken();
			int port = Integer.parseInt(st.nextToken());
			connectTo(host, port);
		} catch (Exception e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}
	
	public void close() {
		try {
			scheduledExecutor.shutdownNow();
			router.close();
			server.close();
			//TODO close connections
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}
	
	private class ConnectTask implements Runnable {
		private InetAddress host;
		private int port;

		public ConnectTask(InetAddress host, int port) {
			this.host = host;
			this.port = port;
			(new Thread(this, "ConnectTask")).start();
		}

		@Override
		public void run() {
			Socket s;
			try {
				s = new Socket(host, port);
				new TCPConnection(ConnectionManager.this, s, networkKey);
			} catch (UnknownHostException e) {
				Logger.getLogger("").log(Level.WARNING, host+" "+port, e);
			} catch (IOException e) {
				Logger.getLogger("").log(Level.WARNING, host+" "+port, e);
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
