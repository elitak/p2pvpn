/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
