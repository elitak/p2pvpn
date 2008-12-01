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
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.p2pvpn.tools.AdvProperties;

/**
 *
 * @author Wolfgang
 */
public class Connector {
    
    ConnectionManager connectionManager;
    Set<Endpoint> ips;
    
    public Connector(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        ips = new HashSet<Endpoint>();
    }    
    
    public void addIP(byte[] ip, int port) {
        Endpoint e = new Endpoint(ip, port);
        synchronized (ips) {
            if (!ips.contains(ip)) {
                ips.add(e);
                scheduleConnect(e, 1);
            }
        }
    }

    public void addIP(String ip, int port) {
        try {
            addIP(InetAddress.getByName(ip).getAddress(), port);
        } catch (UnknownHostException ex) {
			Logger.getLogger("").log(Level.WARNING, "unknown host "+ip, ex);
        }
    }
    
    public void addIPs(AdvProperties p) {
        int i=0;
        
        while(p.containsKey("bootstrap.connectTo."+i)) {
            try {
                StringTokenizer st = new StringTokenizer(p.getProperty("bootstrap.connectTo." + i), ":");
                String ip = st.nextToken();
                int port = Integer.parseInt(st.nextToken());
                addIP(ip, port);
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
            try {
                connectionManager.connectTo(e.getInetAddress(), e.getPort());
                scheduleConnect(e, 5 * 60);
            } catch (UnknownHostException ex) {
                // TODO
            }
        }
        
    }
    
    private class Endpoint {
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
    }
}
