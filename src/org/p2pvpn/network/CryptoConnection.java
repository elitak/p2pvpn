/*
    Copyright 2009 Wolfgang Ginolas

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

import java.security.InvalidKeyException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.p2pvpn.tools.CryptoUtils;


public class CryptoConnection {

	private enum CCState {WAIT_FOR_IV, WAIT_FOR_DATA};
	
	Cipher cIn, cOut;
	SecretKey key;
	
	ConnectionManager connectionManager;
	P2PConnection listener;
	TCPConnection tcpConnection;
	CCState state;

	public CryptoConnection(ConnectionManager connectionManager, TCPConnection tcpConnection, byte[] keyBytes) {
		this.tcpConnection = tcpConnection;
		this.connectionManager = connectionManager;
		cIn = null;
		cOut = null;
		state = CCState.WAIT_FOR_DATA;

		tcpConnection.setListener(this);
		changeKey(keyBytes);
		
		connectionManager.newCryptoConnection(this);
	}

	public synchronized void setListener(P2PConnection listener) {
		this.listener = listener;
	}

	public synchronized void changeKey(byte[] keyBytes) {
		state = CCState.WAIT_FOR_IV;

		key = CryptoUtils.decodeSymmetricKey(keyBytes);
		
		Cipher newOut = CryptoUtils.getSymmetricCipher();
		try {
			newOut.init(Cipher.ENCRYPT_MODE, key);
		} catch (InvalidKeyException ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			close();
		}
		send(newOut.getIV());
		
		cOut = newOut;
	}
	
	public synchronized void send(byte[] packet) {
		if (cOut==null) {
			tcpConnection.send(packet);
		} else {
			try {
				tcpConnection.send(cOut.doFinal(packet));
			} catch (Throwable t) {
				Logger.getLogger("").log(Level.SEVERE, null, t);
				close();
			} 
		}
	}
	
	public synchronized void receive(byte[] packet) {
		byte[] ct;
		if (cIn==null) {
			ct = packet;
		} else {
			try {
				ct = cIn.doFinal(packet);
			} catch (Throwable t) {
				Logger.getLogger("").log(Level.SEVERE, null, t);
				close();
				return;
			} 
		}
		switch (state) {
			case WAIT_FOR_IV:
				cIn = CryptoUtils.getSymmetricCipher();
				try {
					cIn.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ct));
				} catch (Throwable t) {
					Logger.getLogger("").log(Level.SEVERE, null, t);
					close();
				} 
				state = CCState.WAIT_FOR_DATA;
				break;
			case WAIT_FOR_DATA:
				if (listener!=null) listener.receive(ct);
				break;
		}
	}

	public void close() {
		tcpConnection.close();
	}
	
	public void connectionClosed() {
		if (listener!=null) listener.connectionClosed();
	}
}
