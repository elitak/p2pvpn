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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;


public class P2PConnection {
	
	private enum P2PConnState {WAIT_FOR_ACCESS, WAIT_FOR_RND, WAIT_FOR_ENC_RND,
		WAIT_FOR_KEY, CONNECTED};
	
	private P2PConnState state;
	
	private byte[] myKeyPart;
	
	private ConnectionManager connectionManager;
	private TCPConnection connection;
	private ScheduledFuture<?> schedTimeout;
	private PeerID remoteAddr;
	private AdvProperties remoteAccess;
	private Router router;
	
	public P2PConnection(ConnectionManager connectionManager,
			TCPConnection connection) {
		
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

	private void timeout() {
		Logger.getLogger("").log(Level.INFO, "Timeout in handshake with "+connection.toString()+
				" in state: "+state);
		connection.close();
	}

	public void connectionClosed() {
		Logger.getLogger("").log(Level.INFO, "P2P connection to "+connection+" lost");
		if (router!=null) router.connectionClosed(this);
	}

	public void receive(byte[] packet) {
		//ByteArrayInputStream inB = new ByteArrayInputStream(packet); TODO
		
		try {
			switch (state) {
				case WAIT_FOR_ACCESS: {
					remoteAccess = new AdvProperties(packet);
					remoteAddr = new PeerID(remoteAccess.getPropertyBytes("access.publicKey", null), true);
					PublicKey netKey = CryptoUtils.decodeRSAPublicKey(
							connectionManager.getAccessCfg().getPropertyBytes("network.publicKey", null));
					if (remoteAccess.verify("access.signature", netKey)) {
						SecureRandom rnd = CryptoUtils.getSecureRandom();
						myKeyPart = new byte[CryptoUtils.getSymmetricKeyLength()];
						rnd.nextBytes(myKeyPart);

						PublicKey remoteKey = CryptoUtils.decodeRSAPublicKey(
								remoteAccess.getPropertyBytes("access.publicKey", null));
						
						Cipher c = CryptoUtils.getAsymmetricCipher();
						c.init(Cipher.ENCRYPT_MODE, remoteKey, rnd);
						
						connection.send(c.doFinal(myKeyPart), true);
						
						state = P2PConnState.WAIT_FOR_KEY;
					} else {
						Logger.getLogger("").log(Level.WARNING, remoteAddr+" has no valid access!");
						close();
					}
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

	public void send(byte[] packet, boolean highPriority) {
		assert state == P2PConnState.CONNECTED;
		connection.send(packet, highPriority);
	}
	
	public void setRouter(Router router) {
		this.router = router;
	}

	public void close() {
		connection.close();
	}
}
