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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import org.p2pvpn.network.bandwidth.SlidingAverage;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;

/**
 * The autorisation layer of the network. If checks if a remote peer is allowed
 * to be part of this network.
 * @author Wolfgang Ginolas
 */
public class P2PConnection {

	private static final int PING_BUCKET_LEN = 10;

	private enum P2PConnState {WAIT_FOR_ACCESS,
		WAIT_FOR_KEY, CONNECTED};
	
	private P2PConnState state;						// the current state
	
	private byte[] myKeyPart;						// my key part
	
	private ConnectionManager connectionManager;	// the ConnectionManager
	private TCPConnection connection;				// the underlying TCPConnection
	private ScheduledFuture<?> schedTimeout;		// used for a connect timeout
	private PeerID remoteAddr;						// the remote PeerID
	private AdvProperties remoteAccess;				// the remote access invitation
	private long remoteExpiryDate;					// the remote date of expiry
	private Router router;							// the router

	private SlidingAverage pingTime;				// the latency for this connection


	/**
	 * Create a new P2PConnetion
	 * @param connectionManager the ConnectionManager
	 * @param connection the TCPConnection
	 */
	public P2PConnection(ConnectionManager connectionManager,
			TCPConnection connection) {

		pingTime = new SlidingAverage(PING_BUCKET_LEN, 0);
		this.connectionManager = connectionManager;
		this.connection = connection;

		remoteAddr = null;
		
		connection.setListener(this);
		
		AdvProperties access = connectionManager.getAccessCfg().filter("access", false);
		connection.send(access.asBytes(), true);
		state = P2PConnState.WAIT_FOR_ACCESS;

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

	/**
	 * Called when the connection timed out.
	 */
	private void timeout() {
		if (state!=P2PConnState.CONNECTED) {
			Logger.getLogger("").log(Level.INFO, "Timeout in handshake with "+connection.toString()+
					" in state: "+state);
			connection.close();
		}
	}

	/**
	 * Called, when the connection closed.
	 */
	public void connectionClosed() {
		Logger.getLogger("").log(Level.INFO, "P2P connection to "+connection+" lost");
		if (router!=null) router.connectionClosed(this);
	}

	/**
	 * Called, when a packat arrived.
	 * @param packet the packet
	 */
	public void receive(byte[] packet) {
		try {
			switch (state) {
				case WAIT_FOR_ACCESS: {
					remoteAccess = new AdvProperties(packet);
					remoteAddr = new PeerID(remoteAccess.getPropertyBytes("access.publicKey", null), true);
					PublicKey netKey = CryptoUtils.decodeRSAPublicKey(
							connectionManager.getAccessCfg().getPropertyBytes("network.publicKey", null));

					if (!remoteAccess.verify("access.signature", netKey)) { // check signature
						Logger.getLogger("").log(Level.WARNING, remoteAddr+" has no valid access!");
						close();
						break;
					}


					try {
						remoteExpiryDate = Long.parseLong(remoteAccess.getProperty("access.expiryDate"));
					} catch (NumberFormatException numberFormatException) {
						remoteExpiryDate = 0;
					}
					if (remoteInvitatonExpired()) {
						Logger.getLogger("").log(Level.WARNING, remoteAddr+" has expired!");
						close();
						break;
					}

					SecureRandom rnd = CryptoUtils.getSecureRandom();
					myKeyPart = new byte[CryptoUtils.getSymmetricKeyLength()];
					rnd.nextBytes(myKeyPart);

					PublicKey remoteKey = CryptoUtils.decodeRSAPublicKey(
							remoteAccess.getPropertyBytes("access.publicKey", null));

					Cipher c = CryptoUtils.getAsymmetricCipher();
					c.init(Cipher.ENCRYPT_MODE, remoteKey, rnd);

					connection.send(c.doFinal(myKeyPart), true);

					state = P2PConnState.WAIT_FOR_KEY;
					break;
				}
				case WAIT_FOR_KEY: {
					PrivateKey myKey = CryptoUtils.decodeRSAPrivateKey(
							connectionManager.getAccessCfg().getPropertyBytes("secret.access.privateKey", null));
					Cipher c = CryptoUtils.getAsymmetricCipher();
					c.init(Cipher.DECRYPT_MODE, myKey);
					byte[] remoteKeyPart = c.doFinal(packet);
					byte[] key = new byte[myKeyPart.length];
					
					for(int i=0; i<key.length; i++) {
						key[i] = (byte)(myKeyPart[i] ^ remoteKeyPart[i]);
					}
					myKeyPart = null;
					connection.changeKey(key);
					
					state = P2PConnState.CONNECTED;
					Logger.getLogger("").log(Level.INFO, "new connection to "+connection.getRemoteHost()+" ("+remoteAddr+")");
					schedTimeout.cancel(false);
					connectionManager.newP2PConnection(this);
					break;
				}
				case CONNECTED:
					router.receive(this, packet);
					break;
			}
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.WARNING, "closing connection to +"+remoteAddr, t);
			connection.close();
		}
	}

	/**
	 * Send a packet
	 * @param packet the packet
	 * @param highPriority does this packet habe ah high priority?
	 */
	public void send(byte[] packet, boolean highPriority) {
		if (state == P2PConnState.CONNECTED) connection.send(packet, highPriority);
	}

	/**
	 * @return did the remote invitation expire?
	 */
	public boolean remoteInvitatonExpired() {
		return remoteExpiryDate!=0 && remoteExpiryDate < System.currentTimeMillis();
	}

	public void setRouter(Router router) {
		this.router = router;
	}

	/**
	 * Close the connection to the remote peer.
	 */
	public void close() {
		connection.close();
	}

	public SlidingAverage getPingTime() {
		return pingTime;
	}
}
