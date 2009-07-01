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

import java.util.Vector;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.Router;
import org.p2pvpn.network.RoutungTableListener;

/**
 * The Model for the peer list used in the MainWindow.
 * @author Wolfgang Ginolas
 */
public class PeerListModel implements RoutungTableListener, ListModel {
	
	ConnectionManager connectionManager;
	Vector<ListDataListener> listeners;
	Vector<PeerID> list;
	
	public PeerListModel() {
		connectionManager = null;
		listeners = new Vector<ListDataListener>();
	    list = new Vector<PeerID>();
	}
	
	public void tableChanged(Router router) {
		PeerID[] peers = router.getPeers();
	    list = new Vector<PeerID>();

		for (PeerID peer : peers) {
			if (!connectionManager.getLocalAddr().equals(peer)) {
				list.add(peer);
			}
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				notifyListeners();
			}
		});
	}

	public void notifyListeners() {
		for (ListDataListener l : listeners) {
			l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, list.size()));
		}
	}

	public void setConnectionManager(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		if (connectionManager!=null) {
			connectionManager.getRouter().addTableListener(this);
			tableChanged(connectionManager.getRouter());
		}
	}

	public int getSize() {
		return list.size();
	}

	public Object getElementAt(int index) {
		return list.get(index);
	}

	public void addListDataListener(ListDataListener l) {
		listeners.add(l);
	}

	public void removeListDataListener(ListDataListener l) {
		listeners.remove(l);
	}

}
