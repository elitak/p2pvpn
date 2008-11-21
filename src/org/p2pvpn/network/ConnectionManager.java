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
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConnectionManager implements Runnable {
    final static private String WHATISMYIP_URL = "http://whatismyip.com/automation/n09230945.asp";
    
	private ServerSocket server;
	private int serverPort;
	private PeerID localAddr;
	private ScheduledExecutorService scheduledExecutor;
	private Router router;
    private Connector connector;
    
	public ConnectionManager(int serverPort) {
		this.serverPort = serverPort;
		scheduledExecutor = Executors.newScheduledThreadPool(1);
		localAddr = new PeerID();
		router = new Router(this);
        connector = new Connector(this);
		
		(new Thread(this)).start();
	}

    public void findLocalIPs() {
        (new Thread(new Runnable() {
            public void run() {
                findLocalIPsThread();
            }
        })).start();
    }
    
    private void findLocalIPsThread() {
        String ipList="";
        router.setLocalPeerInfo("local.port", ""+serverPort);
        try {
            Enumeration<NetworkInterface> is = NetworkInterface.getNetworkInterfaces();
            while (is.hasMoreElements()) {
                NetworkInterface i = is.nextElement();
                Enumeration<InetAddress> as = i.getInetAddresses();

                System.out.print(i.getName() + ":");
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address) {
                        String s = a.getHostAddress();
                        System.out.print(" " + s);
                        if (!s.startsWith("127") && !s.equals(router.getPeerInfo(localAddr, "vpn.ip"))) {
                            ipList = ipList + " " + s;
                        }
                    }
                }
                System.out.println();
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        router.setLocalPeerInfo("local.ips", ipList.substring(1));
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new URL(WHATISMYIP_URL).openConnection().getInputStream()));
            InetAddress a = InetAddress.getByName(in.readLine());
            if (a instanceof Inet4Address) ipList = ipList + " " + a.getHostAddress();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        router.setLocalPeerInfo("local.ips", ipList.substring(1));
    }
    
	public PeerID getLocalAddr() {
		return localAddr;
	}
	
	public ScheduledExecutorService getScheduledExecutor() {
		return scheduledExecutor;
	}

	public void newConnection(TCPConnection connection) {
		System.out.println("new connection from/to: "+connection);
		new P2PConnection(this, connection);
	}

	public void newP2PConnection(P2PConnection p2pConnection) {
		System.out.println("new P2P connection from/to: "+p2pConnection.getRemoteAddr()
				+" ("+p2pConnection.getConnection()+")");
		router.newP2PConnection(p2pConnection);
	}
	
	@Override
	public void run() {
		try {
			server = new ServerSocket(serverPort);
			serverPort = server.getLocalPort();
			System.out.println("listening on port "+server.getLocalPort());
			
			while (true) {
				Socket s = server.accept();
				new TCPConnection(this, s);
			}
		}
		catch (SocketException se) {}
		catch (IOException e) {e.printStackTrace();}
		
		System.out.println("Not listening on "+server.getLocalPort()+" anymore");
	}

	public void connectTo(String host, int port) {
        try {
            connectTo(InetAddress.getByName(host), port);
        } catch (UnknownHostException ex) {
            // TODO
        }
	}
	
	public void connectTo(InetAddress host, int port) {
		new ConnectTask(host, port);
	}
	
	public void connectTo(String addr) {
		StringTokenizer st = new StringTokenizer(addr,":");
		
		String host = st.nextToken();
		int port = Integer.parseInt(st.nextToken());
		connectTo(host, port);
	}
	
	public void close() {
		try {
			scheduledExecutor.shutdownNow();
			router.close();
			server.close();
			//TODO close connections
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private class ConnectTask implements Runnable {
		private InetAddress host;
		private int port;

		public ConnectTask(InetAddress host, int port) {
			this.host = host;
			this.port = port;
			(new Thread(this)).start();
		}

		@Override
		public void run() {
			Socket s;
			try {
				s = new Socket(host, port);
				new TCPConnection(ConnectionManager.this, s);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
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
}
