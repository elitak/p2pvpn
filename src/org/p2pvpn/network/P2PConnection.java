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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public class P2PConnection {
	
	static final String VERSION = "test1";
	
	private enum P2PConnState {WAITING_FOR_VERSION, WAITING_FOR_ADDRESS, CONNECTED};
	
	private P2PConnState state;
	
	private ConnectionManager connectionManager;
	private TCPConnection connection;
	private ScheduledFuture<?> schedTimeout;
	private PeerID remoteAddr;
	private Router router;
	
	public P2PConnection(ConnectionManager connectionManager,
			TCPConnection connection) {
		
		this.connectionManager = connectionManager;
		this.connection = connection;

		remoteAddr = null;
		
		state = P2PConnState.WAITING_FOR_VERSION;
		
		connection.setListener(this);
		
		connection.send(VERSION.getBytes());

		schedTimeout = 
			connectionManager.getScheduledExecutor().schedule(new Runnable() {
			public void run() {
				timeout();
			}
		}, 30, TimeUnit.SECONDS);
	}

	public TCPConnection getConnection() {
		return connection;
	}

	public PeerID getRemoteAddr() {
		return remoteAddr;
	}

	private void timeout() {
		Logger.getLogger("").log(Level.INFO, "Timeout in handshake with "+connection.toString());
		connection.close();
	}

	public void connectionClosed() {
		Logger.getLogger("").log(Level.INFO, "P2P connection to "+connection+" lost");
		if (router!=null) router.connectionClosed(this);
	}

	public void receive(byte[] packet) {
		ByteArrayInputStream inB = new ByteArrayInputStream(packet);
		
		try {
			switch (state) {
				case WAITING_FOR_VERSION:
					if (Arrays.equals(VERSION.getBytes(), packet)) {
						state = P2PConnState.WAITING_FOR_ADDRESS;
						
						ByteArrayOutputStream outB = new ByteArrayOutputStream();
						ObjectOutputStream outO = new ObjectOutputStream(outB);
						outO.writeObject(connectionManager.getLocalAddr());
						outO.flush();
						connection.send(outB.toByteArray());
					} else {
						Logger.getLogger("").log(Level.INFO, "P2PConnection: incorrect version");
						schedTimeout.cancel(false);
						connection.close();
					}
					break;
				
				case WAITING_FOR_ADDRESS:
					ObjectInputStream inO = new ObjectInputStream(inB);
					remoteAddr = (PeerID)inO.readObject();
					schedTimeout.cancel(false);
					connectionManager.newP2PConnection(this);
					state = P2PConnState.CONNECTED;
					break;
				
				case CONNECTED:
					router.receive(this, packet);
					break;
			}
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "closing connection to +"+remoteAddr, e);
			connection.close();
		} catch (ClassNotFoundException e) {
			Logger.getLogger("").log(Level.SEVERE, "", e);
			System.exit(1);
		}
	}

	public void send(byte[] packet) {
		assert state == P2PConnState.CONNECTED;
		connection.send(packet);
	}
	
	public void setRouter(Router router) {
		this.router = router;
	}

	public void close() {
		connection.close();
	}
}
