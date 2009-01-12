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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TCPConnection implements Runnable {
	
	private static final int MAX_QUEUE = 10;
	private static final int MAX_PACKET_SIZE = 10 * 1024;
	
	private ConnectionManager connectionManager;
	private Socket socket;
	private InputStream in;
	private BufferedOutputStream out;
	private SocketAddress peer;
	private P2PConnection listener;
	
	private Queue<byte[]> sendQueue;
	private boolean closed;

	public TCPConnection(ConnectionManager connectionManager, Socket socket) {
		this.connectionManager = connectionManager;
		this.socket = socket;
		peer = socket.getRemoteSocketAddress();
		sendQueue = new LinkedList<byte[]>();
		closed = false;
		try {
			in = socket.getInputStream();
			out = new BufferedOutputStream(socket.getOutputStream());
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

	private int readInt() throws IOException {
		int result, high, low;
		high = in.read();
		if (high==-1) throw new IOException("Connection to "+peer+" lost");
		low = in.read();
		if (low==-1) throw new IOException("Connection to "+peer+" lost");
		result = (high << 8) + low;
		return result;
	}
	
	@Override
	public void run() {
		byte[] buffer = new byte[2048];
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
				handlePacket(packet);
			}
		} catch (IOException e) {
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

	private void sendThread() {
		try {
			while (true) {
				if (closed) break;
				synchronized (sendQueue) {
					byte[] packet = sendQueue.poll();
					if (packet == null) {
						out.flush();
						try {
							sendQueue.wait();
						} catch (InterruptedException ex) {
						}
					} else {
						int high = (packet.length & 0xFF00) >> 8;
						int low = packet.length & 0xFF;
						out.write(high);
						out.write(low);
						out.write(packet);
					}
				}
			}
		} catch (IOException iOException) {
			close();
		}
	}
	
	private void handlePacket(byte[] packet) {
		if (listener!=null) listener.receive(packet);
	}
	
	public void setListener(P2PConnection listener) {
		this.listener = listener;
	}
	
	public void send(byte[] packet) {
		synchronized (sendQueue) {
			if (sendQueue.size()<MAX_QUEUE) {
				sendQueue.offer(packet);
				sendQueue.notify();
			}
		}
	}
	
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
}
