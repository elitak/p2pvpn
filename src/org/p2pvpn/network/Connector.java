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
	private final static long REMOVE_MS = 16*60*1000;
	
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
		ConnectorListener[] ls;
		synchronized (listeners) {
			ls = listeners.toArray(new ConnectorListener[0]);
		}
		for(ConnectorListener l : ls) {
			try {
				l.ipListChanged(this);
			}
			catch (Throwable t) {
				Logger.getLogger("").log(Level.WARNING, "", t);
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
	
    public void addIP(byte[] ip, int port, PeerID peerID, String source, String status, boolean keep) {
        Endpoint endpoint = new Endpoint(ip, port);
		EndpointInfo endpointInfo = new EndpointInfo(peerID, source, status, keep);
		connectionManager.getScheduledExecutor().schedule(
				new AddIPLater(endpoint, endpointInfo), 0, TimeUnit.SECONDS);
    }

    public void addIP(String ip, int port, PeerID peerID, String source, String status, boolean keep) {
        try {
            addIP(InetAddress.getByName(ip).getAddress(), port, peerID, source, status, keep);
        } catch (UnknownHostException ex) {
			Logger.getLogger("").log(Level.WARNING, "unknown host "+ip, ex);
        }
    }

	public void addIP(InetAddress ip, int port, PeerID peerID, String source, String status, boolean keep) {
		addIP(ip.getAddress(), port, peerID, source, status, keep);
	}
    
    public void addIPs(AdvProperties p) {
        int i=0;
        
        while(p.containsKey("network.bootstrap.connectTo."+i)) {
            try {
                StringTokenizer st = new StringTokenizer(p.getProperty("network.bootstrap.connectTo." + i), ":");
                String ip = st.nextToken();
                int port = Integer.parseInt(st.nextToken());
                addIP(ip, port, null, "bootstrap", "", true);
            } catch (Throwable t) {
				Logger.getLogger("").log(Level.WARNING, "", t);
            }
            i++;
        }
    }
    
    private void scheduleConnect(Endpoint e, long delay) {
        connectionManager.getScheduledExecutor().schedule(new ConnectRunnable(e), delay, TimeUnit.SECONDS);
    }

	private class AddIPLater implements Runnable {
		private Endpoint endpoint;
		private EndpointInfo endpointInfo;

		public AddIPLater(Endpoint endpoint, EndpointInfo endpointInfo) {
			this.endpoint = endpoint;
			this.endpointInfo = endpointInfo;
		}

		public void run() {
			boolean schedule = false;
			synchronized (ips) {
				if (!ips.containsKey(endpoint)) {
					ips.put(endpoint, endpointInfo);
					schedule = true;
				} else {
					ips.get(endpoint).update(endpointInfo.peerID,
							endpointInfo.getSource(), endpointInfo.getStatus(), endpointInfo.isKeepForEver());
				}
			}
			if (schedule) scheduleConnect(endpoint, 1);
			notifyListeners();
		}
	}

    private class ConnectRunnable implements Runnable {
        Endpoint e;

        public ConnectRunnable(Endpoint e) {
            this.e = e;
        }

        public void run() {
			EndpointInfo info;
			synchronized (ips) {
				info = ips.get(e);
			}
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
				synchronized (ips) {
					ips.remove(e);
				}
				notifyListeners();
			}
			scheduleConnect(e, RETRY_S);
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
		String source;
		String status;
		boolean keepForEver;

		EndpointInfo(PeerID peerID, String source, String status, boolean keepForEver) {
			this.peerID = peerID;
			this.source = source;
			this.status = status;
			this.keepForEver = keepForEver;
			update();
		}
		
		EndpointInfo() {
			this(null, null, "", false);
		}
		
		void update() {
			timeAdded = System.currentTimeMillis();
		}

		void update(PeerID peerID, String source, String status, boolean keepForEver) {
			if (peerID!=null) this.peerID = peerID;
			if (source!=null) this.source = source;
			if (status!=null) this.status = status;
			if (keepForEver) this.keepForEver = true;
			timeAdded = System.currentTimeMillis();
		}
		
		public boolean isKeepForEver() {
			return keepForEver;
		}

		public String getSource() {
			return source;
		}

		public String getStatus() {
			return status;
		}

		public PeerID getPeerID() {
			return peerID;
		}

		public long getTimeAdded() {
			return timeAdded;
		}
	}
}
