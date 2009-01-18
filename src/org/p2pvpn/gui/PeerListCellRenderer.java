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

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.p2pvpn.network.PeerID;

public class PeerListCellRenderer extends JLabel implements ListCellRenderer{

	private static Icon directIcon = null;
	private static Icon indirectIcon = null;
	
	static {
		try {
			directIcon = new ImageIcon(
					InfoWindow.class.getClassLoader().getResource("resources/images/direct.png"));
			indirectIcon = new ImageIcon(
					InfoWindow.class.getClassLoader().getResource("resources/images/indirect.png"));
		} catch (NullPointerException e) {
		}
	}
	
	MainControl mainControl;
	
	public PeerListCellRenderer() {
		super();
		mainControl = null;
	}

	public void setMainControl(MainControl mainControl) {
		this.mainControl = mainControl;
	}
	
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		boolean direct = true;
		String text = "";
		String toolTip = "";
		
		if (mainControl!=null) {
			PeerID peer = (PeerID)value;
			
			if (mainControl.getConnectionManager()!=null) {
				direct = mainControl.getConnectionManager().getRouter().isConnectedTo(peer);
			}
			text = mainControl.nameForPeer(peer);
			toolTip = mainControl.descriptionForPeer(peer);
		}
		
		setText(text);
		setIcon(direct ? directIcon : indirectIcon);
		setToolTipText(toolTip);
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}
		setEnabled(list.isEnabled());
		setFont(list.getFont());
		setOpaque(true);
		return this;
	}
}
