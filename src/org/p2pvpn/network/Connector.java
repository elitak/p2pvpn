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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.tools.AdvProperties;

/**
 *
 * @author Wolfgang
 */
public class Connector {
    
	private final static long RETRY_S = 5*60;
	private final static long REMOVE_MS = 1*60*60*1000;
	
    ConnectionManager connectionManager;
    Map<Endpoint, EndpointInfo> ips;
	
	Vector<ConnectorListener> listeners;
    
    public Connector(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
		listeners = new Vector<ConnectorListener>();
        ips = new HashMap<Endpoint, EndpointInfo>();
    }    
    
	public void addListener(ConnectorListener l) {
		synchronized (listeners) {
			listeners.add(l);
		}
	}
	
	public void removeListener(ConnectorListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}
	
	private void notifyListeners() {
		synchronized (listeners) {
			for(ConnectorListener l : listeners) {
				try {
					l.ipListChanged(this);
				}
				catch (Throwable t) {
					Logger.getLogger("").log(Level.WARNING, "", t);
				}
			}
		}
	}
	
	public Endpoint[] getIPs() {
		synchronized (ips) {
			return ips.keySet().toArray(new Endpoint[0]);
		}
	}
	
	public EndpointInfo getIpInfo(Endpoint e) {
		synchronized (ips) {
			return ips.get(e);
		}
	}
	
    public void addIP(byte[] ip, int port, PeerID peerID, String info, boolean keep) {
        Endpoint e = new Endpoint(ip, port);
        synchronized (ips) {
            if (!ips.containsKey(e)) {
                ips.put(e, new EndpointInfo(peerID, info, keep));
                scheduleConnect(e, 1);
            } else {
				ips.get(e).update(peerID, info, keep);
			}
        }
		notifyListeners();
    }

    public void addIP(String ip, int port, PeerID peerID, String info, boolean keep) {
        try {
            addIP(InetAddress.getByName(ip).getAddress(), port, peerID, info, keep);
        } catch (UnknownHostException ex) {
			Logger.getLogger("").log(Level.WARNING, "unknown host "+ip, ex);
        }
    }
    
    public void addIPs(AdvProperties p) {
        int i=0;
        
        while(p.containsKey("network.bootstrap.connectTo."+i)) {
            try {
                StringTokenizer st = new StringTokenizer(p.getProperty("network.bootstrap.connectTo." + i), ":");
                String ip = st.nextToken();
                int port = Integer.parseInt(st.nextToken());
                addIP(ip, port, null, "bootstrap", true);
            } catch (Throwable t) {
				Logger.getLogger("").log(Level.WARNING, "", t);
            }
            i++;
        }
    }
    
    private void scheduleConnect(Endpoint e, long delay) {
        connectionManager.getScheduledExecutor().schedule(new ConnectRunnable(e), delay, TimeUnit.SECONDS);
    }
    
    private class ConnectRunnable implements Runnable {
        Endpoint e;

        public ConnectRunnable(Endpoint e) {
            this.e = e;
        }

        public void run() {
			boolean notify = false;
			synchronized (ips) {
				EndpointInfo info = ips.get(e);
				if (info==null) return;
				
				if (!connectionManager.getRouter().isConnectedTo(info.peerID)) {
					try {
						connectionManager.connectTo(e.getInetAddress(), e.getPort());
					} catch (UnknownHostException ex) {
					}
				} else {
					info.update();
				}
				if (System.currentTimeMillis()-REMOVE_MS > info.timeAdded) {
					ips.remove(e);
					notify=true;
				}
			}
			scheduleConnect(e, RETRY_S);
			notifyListeners();
        }
        
    }
    
    public class Endpoint {
        byte[] ip;
        int port;

        public Endpoint(byte[] ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public byte[] getIp() {
            return ip;
        }

        public InetAddress getInetAddress() throws UnknownHostException {
            return InetAddress.getByAddress(ip);
        }
        
        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Endpoint other = (Endpoint) obj;
            if (this.port != other.port) {
                return false;
            }
            if (!Arrays.equals(ip, other.ip)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + (this.ip != null ? Arrays.hashCode(ip) : 0);
            hash = 59 * hash + this.port;
            return hash;
        }
		
		@Override
		public String toString() {
			try {
				return InetAddress.getByAddress(ip).getHostAddress() + ":" + port;
			} catch (UnknownHostException ex) {
				return "unknown host";
			}
		}
    }
	
	public class EndpointInfo {
		PeerID peerID;
		long timeAdded;
		String lastInfo;
		boolean keepForEver;

		EndpointInfo(PeerID peerID, String lastInfo, boolean keepForEver) {
			this.peerID = peerID;
			this.lastInfo = lastInfo;
			this.keepForEver = keepForEver;
			update();
		}
		
		EndpointInfo() {
			this(null, null, false);
		}
		
		void update() {
			timeAdded = System.currentTimeMillis();
		}

		void update(PeerID peerID, String lastInfo, boolean keepForEver) {
			if (peerID!=null) this.peerID = peerID;
			if (lastInfo!=null) this.lastInfo = lastInfo;
			if (keepForEver) this.keepForEver = true;
			timeAdded = System.currentTimeMillis();
		}
		
		public boolean isKeepForEver() {
			return keepForEver;
		}

		public String getLastInfo() {
			return lastInfo;
		}

		public PeerID getPeerID() {
			return peerID;
		}

		public long getTimeAdded() {
			return timeAdded;
		}
	}
}
