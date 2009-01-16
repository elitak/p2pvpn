/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.p2pvpn.gui;

import java.util.Vector;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.Router;
import org.p2pvpn.network.RoutungTableListener;

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
