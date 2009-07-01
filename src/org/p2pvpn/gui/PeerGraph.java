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


package org.p2pvpn.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.StringTokenizer;
import javax.swing.JComponent;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.Router;
import org.p2pvpn.network.RoutungTableListener;

/**
 * This Component shows a simple peer graph which is used in the info window
 * @author Wolfgang Ginolas
 */
public class PeerGraph extends JComponent implements RoutungTableListener {
	private static final int BORDER = 50;
	private static final int NODE_SIZE = 20;

	private ConnectionManager connectionManager;
	private Router router;

	public PeerGraph() {
		super();
		setConnectionManager(null);
	}

	public void setConnectionManager(ConnectionManager cm) {
		connectionManager = cm;
		if (connectionManager==null) {
			router = null;
		} else {
			router = connectionManager.getRouter();
			router.addTableListener(this);
		}
	}

	public void tableChanged(Router router) {
		repaint();
	}

	@Override
	public void paint(Graphics g) {
		if (router==null) return;
		Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		FontMetrics fm = g.getFontMetrics();
		int centerX = getWidth() / 2;
		int centerY = getHeight() / 2;
		int size = Math.min(getWidth(), getHeight()) / 2 - BORDER;

		PeerID[] peers = router.getPeers();
		int[] px = new int[peers.length];
		int[] py = new int[peers.length];

		for(int i=0; i<peers.length; i++) {
			double a = ((double)i) / peers.length * 2*Math.PI;
			px[i] = centerX + (int)(Math.cos(a)*size);
			py[i] = centerY + (int)(Math.sin(a)*size);
		}

		g2.setStroke(new BasicStroke(2));

		for(int i=0; i<peers.length; i++) {
			// Draw connections
			String connectedTo = router.getPeerInfo(peers[i], "connectedTo");
			if (connectedTo!=null) {
				StringTokenizer st = new StringTokenizer(connectedTo);

				while(st.hasMoreTokens())  {
					PeerID peer = new PeerID(st.nextToken());
					for(int p=0; p<peers.length; p++) {
						if (peers[p].equals(peer)) {
							g2.drawLine(px[i], py[i], px[p], py[p]);
						}
					}
				}
			}
		}

		for(int i=0; i<peers.length; i++) {
			int x = px[i];
			int y = py[i];
			String name = router.getPeerInfo(peers[i], "name");
			if (name==null) name="?";

			// Draw node
			g2.setColor(Color.BLUE);
			g2.fillOval(x-NODE_SIZE/2, y-NODE_SIZE/2, NODE_SIZE, NODE_SIZE);
			g2.setColor(Color.BLACK);
			g2.drawOval(x-NODE_SIZE/2, y-NODE_SIZE/2, NODE_SIZE, NODE_SIZE);
			g2.drawString(name, x-fm.stringWidth(name)/2, y+NODE_SIZE/2+fm.getHeight());

		}
	}
}
