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
import org.p2pvpn.network.bandwidth.MeasureBandwidth;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.p2pvpn.tools.CryptoUtils;

/**
 * This is the lowest layer in the P2PVPN network. It encrypts packages and
 * sends and recheives them using TCP.
 * @author Wolfgang Ginolas
 */
public class TCPConnection implements Runnable {

	private static final double BUCKET_TIME = 0.5;
	private static final int BUCKET_LEN = 10;

	public static final int DEFAULT_MAX_QUEUE = 10;
	public static final boolean DEFAULT_TCP_FLUSH = false;

	private static final int MAX_PACKET_SIZE = 10 * 1024;

	private enum CCState {WAIT_FOR_IV, WAIT_FOR_DATA};

	private MeasureBandwidth bwIn, bwOut;		// The currently used Bandwidth

	private Cipher cIn, cOut;					// The ciphers for sending and receiving
	private SecretKey key;						// The current encryption kay
	private CCState state;						// The state of this connection
	
	private ConnectionManager connectionManager;// The ConnectionManager
	private Socket socket;						// the Socket for this connection
	private InputStream in;						// InputStream for this connection
	private BufferedOutputStream out;			// OutputStream for this connection
	private SocketAddress peer;					// the remote address
	private P2PConnection listener;				// the upper network layer
	
	private Queue<byte[]> sendQueue;			// a send queue
	private boolean closed;						// is this connection closed?

	private long lastActive;					// time of the last received packet

	/**
	 * Create a new TCPConnection
	 * @param connectionManager the ConnectionManager
	 * @param socket the Socket of the connection
	 * @param keyBytes the encryption kay to use
	 */
	public TCPConnection(ConnectionManager connectionManager, Socket socket, byte[] keyBytes) {
		this.connectionManager = connectionManager;
		this.socket = socket;
		peer = socket.getRemoteSocketAddress();
		sendQueue = new LinkedList<byte[]>();
		closed = false;
		cIn = null;
		cOut = null;
		bwIn = new MeasureBandwidth(BUCKET_TIME, BUCKET_LEN);
		bwOut = new MeasureBandwidth(BUCKET_TIME, BUCKET_LEN);
		state = CCState.WAIT_FOR_DATA;
		lastActive = System.currentTimeMillis();
		
		try {
			in = socket.getInputStream();
			out = new BufferedOutputStream(socket.getOutputStream());
			changeKey(keyBytes);
			this.connectionManager.newConnection(this);
			(new Thread(this, "TCPConnection "+peer)).start();
			(new Thread(new Runnable() {
				public void run() {
					sendThread();
				}
			}, "TCPConnection.sendThread "+peer)).start();
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}

	/**
	 * Change the encryption key.
	 * @param keyBytes the new key
	 */
	public void changeKey(byte[] keyBytes) {
		state = CCState.WAIT_FOR_IV;

		key = CryptoUtils.decodeSymmetricKey(keyBytes);
		Cipher newOut = CryptoUtils.getSymmetricCipher();
		try {
			newOut.init(Cipher.ENCRYPT_MODE, key);
		} catch (InvalidKeyException ex) {
			Logger.getLogger("").log(Level.SEVERE, null, ex);
			close();
		}
		sendEncypted(newOut.getIV(), true);
		
		cOut = newOut;
	}

	/**
	 * Read an 2 Byte integer from the connection
	 * @return the int
	 * @throws java.io.IOException
	 */
	private int readInt() throws IOException {
		int result, high, low;
		high = in.read();
		if (high==-1) throw new IOException("Connection to "+peer+" lost");
		low = in.read();
		if (low==-1) throw new IOException("Connection to "+peer+" lost");
		result = (high << 8) + low;
		return result;
	}

	/**
	 * Receive packages.
	 */
	@Override
	public void run() {
		byte[] buffer = new byte[MAX_PACKET_SIZE];
		try {
			while (true) {
				int size = readInt();
				
				if (size>MAX_PACKET_SIZE) throw new IOException("Packet too large");
				
				int rest=size;
				int off=0;
				
				while(rest>0) {
					int len = in.read(buffer, off, rest);
					if (len==-1) throw new IOException("Connection to "+peer+" lost");
					rest -= len;
					off += len;
				}
				
				byte[] packet = new byte[size];
				System.arraycopy(buffer, 0, packet, 0, size);
				handleEncryptedPacket(packet);
			}
		} catch (Throwable e) {
			//e.printStackTrace();
		}
		
		if (listener!=null) listener.connectionClosed();
		closed = true;
		
		synchronized (sendQueue) {sendQueue.notify();}
		
		try {
			socket.close();
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}

	/**
	 * Get packages from the queue and send them.
	 */
	private void sendThread() {
		try {
			while (true) {
				if (closed) break;
				byte[] packet;
				synchronized (sendQueue) {
					packet = sendQueue.poll();
				}
				if (packet == null) {
					out.flush();
					try {
						synchronized (sendQueue) {sendQueue.wait();}
					} catch (InterruptedException ex) {
					}
				}
				if (packet != null) {
					sendEncypted(packet, false);
					if (connectionManager.isTCPFlush()) out.flush();
				}
			}
		} catch (IOException iOException) {
			//Logger.getLogger("").log(Level.SEVERE, null, iOException);
			close();
		}
	}

	/**
	 * Encrypt an packet and send it.
	 * @param packet the packet
	 * @param flush flush the stream?
	 */
	private void sendEncypted(byte[] packet, boolean flush) {
		if (cOut==null) {
			sendToSocket(packet, flush);
		} else {
			try {
				sendToSocket(cOut.doFinal(packet), flush);
			} catch (Throwable t) {
				Logger.getLogger("").log(Level.SEVERE, null, t);
				close();
			} 
		}
	}	

	/**
	 * Send an packet throug the socket.
	 * @param packet the packet
	 * @param flush flush the stream?
	 */
	private void sendToSocket(byte[] packet, boolean flush) {
		try {
			connectionManager.getSendLimit().waitForTokens(2+packet.length);

			int high = (packet.length & 0xFF00) >> 8;
			int low = packet.length & 0xFF;
			out.write(high);
			out.write(low);
			out.write(packet);
			if (flush) out.flush();
			bwOut.countPacket(2+packet.length);
		} catch (IOException iOException) {
			close();
		}
	}

	/**
	 * Handle an incoming encrypten packet.
	 * @param packet the packet
	 */
	public void handleEncryptedPacket(byte[] packet) {
		byte[] ct;

		lastActive = System.currentTimeMillis();
		bwIn.countPacket(2+packet.length);
		if (!connectionManager.getRecLimit().tokensAvailable(2+packet.length)) {
			return;		// drop packet to limit bandwidth
		}

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

	/**
	 * Set the object of the upperlayer.
	 * @param listener the upper layer
	 */
	public void setListener(P2PConnection listener) {
		this.listener = listener;
	}

	/**
	 * Put a packet in the sen queue.
	 * @param packet the packet
	 * @param highPriority a high priority packet? A high
	 * priority packer won't be dropped even if the send queue is full.
	 */
	public void send(byte[] packet, boolean highPriority) {
		synchronized (sendQueue) {
			if (highPriority || sendQueue.size()<connectionManager.getSendBufferSize()) {
				sendQueue.offer(packet);
				sendQueue.notify();
			}
		}
	}

	/**
	 * Close the connection.
	 */
	public void close() {
		try {
			socket.close();
		} catch (IOException e) {
			Logger.getLogger("").log(Level.WARNING, "", e);
		}
	}

	@Override
	public String toString() {
		return peer.toString();
	}

	public String getRemoteHost() {
		return ((InetSocketAddress)peer).getHostName();
	}

	public MeasureBandwidth getBwIn() {
		return bwIn;
	}

	public MeasureBandwidth getBwOut() {
		return bwOut;
	}

	public long getLastActive() {
		return lastActive;
	}
}
