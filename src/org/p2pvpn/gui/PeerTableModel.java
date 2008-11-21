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

package org.p2pvpn.gui;

import java.util.Vector;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.Router;
import org.p2pvpn.network.RoutungTableListener;

/**
 *
 * @author wolfgang
 */
public class PeerTableModel implements RoutungTableListener, TableModel {

	/*
	 * Table Columns: Name, Id, Direct, IP, MAC
	 */
	
	private ConnectionManager connectionManager;
	private Router router;
	private Vector<TableModelListener> listeners;
	private PeerID[] table;
	
	public PeerTableModel(ConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
		this.router = connectionManager.getRouter();
		router.addTableListener(this);
		listeners = new Vector<TableModelListener>();
		table = router.getPeers();
	}
	
	public PeerID getPeerID(int row) {
		return table[row];
	}
	
	@Override
	public void tableChanged(Router router) {
		table = router.getPeers();
		for(TableModelListener l : listeners) {
			l.tableChanged(new TableModelEvent(this));
		}
	}

	@Override
	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}

	@Override
	public Class<?> getColumnClass(int c) {
		switch (c) {
		case 0: return String.class; 
		case 1: return String.class; 
		case 2: return String.class; 
		case 3: return String.class; 
		case 4: return String.class; 
		default: return null;
		}
	}

	@Override
	public int getColumnCount() {
		return 5;
	}

	@Override
	public String getColumnName(int c) {
		switch (c) {
		case 0: return "Name"; 
		case 1: return "ID"; 
		case 2: return "Connection"; 
		case 3: return "IP"; 
		case 4: return "MAC"; 
		default: return null;
		}
	}

	@Override
	public int getRowCount() {
		return table.length;
	}

	@Override
	public Object getValueAt(int r, int c) {
		switch (c) {
		case 0: return router.getPeerInfo(table[r], "name");
		case 1: return table[r].toString(); 
		case 2: 
			if (table[r].equals(connectionManager.getLocalAddr())) return "it's me"; 
			if (router.isConnectedTo(table[r])) return "direct";
			return "indirect";
		case 3: return router.getPeerInfo(table[r], "vpn.ip");
		case 4: return router.getPeerInfo(table[r], "vpn.mac");
		default: return null;
		}
	}

	@Override
	public boolean isCellEditable(int r, int c) {
		return false;
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);		
	}

	@Override
	public void setValueAt(Object o, int r, int c) {
	}
}
