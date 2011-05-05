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

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.p2pvpn.network.PeerID;
import org.p2pvpn.network.Router;
import org.p2pvpn.network.RoutungTableListener;

/**
 * This is the main window of P2PVPN.
 *
 * @author Wolfgang Ginoas
 */
public class MainWindow extends javax.swing.JFrame implements RoutungTableListener {

	private static final String P2PVPN_IMG = "resources/images/P2PVPN-32.png";
	private static final String CHAT_IMG = "resources/images/chat.png";
	private static final String CHAT_BLA_IMG = "resources/images/chat_bla.png";

	private MainControl mainControl;	// the MainControl
	private NewNetwork newNetwork;		// the other windows
	private OptionWindow optionWindow;
	private InviteWindow inviteWindow;
	private AcceptWindow acceptWindow;
	private ChatWindow chatWindow;
    private InfoWindow infoWindow;

	private PeerListModel peerListModel;// model for the peer list
	private PeerListCellRenderer peerListCellRenderer;	// cell renderer for the peer list

    private TrayIcon trayIcon;			// the try icon
	
    /** Creates new form MainWindow and
	 * and initializes the MainControl and all the other windows
	 */
    public MainWindow() {
        setLocationByPlatform(true);
		peerListModel = new PeerListModel();
		peerListCellRenderer = new PeerListCellRenderer();
        initComponents();
		try {
			URL url = InfoWindow.class.getClassLoader().getResource(P2PVPN_IMG);
			setIconImage(new ImageIcon(url).getImage());
		} catch(NullPointerException e) {}
		
		setButtonIcon(btnNewNet, "resources/images/new.png");
		setButtonIcon(btnInvite, "resources/images/invite.png");
		setButtonIcon(btnAccept, "resources/images/accept.png");
		setButtonIcon(btnOptions, "resources/images/options.png");
		setButtonIcon(btnInfo, "resources/images/info.png");
		setButtonIcon(btnChat, CHAT_IMG);
		
		mainControl = new MainControl(this);
		newNetwork = new NewNetwork(this, mainControl);
		optionWindow = new OptionWindow(this);
		inviteWindow = new InviteWindow(this, mainControl);
		acceptWindow = new AcceptWindow(this, mainControl);
		chatWindow = new ChatWindow(this, mainControl);
        infoWindow = new InfoWindow(mainControl);

        try {
            PopupMenu popupMenu = new PopupMenu();

            MenuItem show = new MenuItem("Show");
			show.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					setVisible(true);
				}
			});
			popupMenu.add(show);

            MenuItem hide = new MenuItem("Hide");
			hide.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					setVisible(false);
				}
			});
			popupMenu.add(hide);
			popupMenu.addSeparator();

            MenuItem quit = new MenuItem("Quit");
			quit.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					System.exit(0);
				}
			});
			popupMenu.add(quit);

			trayIcon = new TrayIcon(
                    Toolkit.getDefaultToolkit().getImage(InfoWindow.class.getClassLoader().getResource(P2PVPN_IMG)),
					"P2PVPN", popupMenu);
            trayIcon.setImageAutoSize(true);

			trayIcon.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton() == MouseEvent.BUTTON1) {
						setVisible(!isVisible());
					}
				}
			});

            SystemTray.getSystemTray().add(trayIcon);
            setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        } catch (Throwable t) {
            Logger.getLogger("").log(Level.INFO, "Coult not create Tray-Icon", t);
        }
		
		mainControl.start();
    }

	/**
	 * Set tht icon for an button
	 * @param btn the button
	 * @param path thi icon
	 */
	private void setButtonIcon(JButton btn, String path) {
		try {
			btn.setIcon(new ImageIcon(InfoWindow.class.getClassLoader().getResource(path)));
			btn.setText("");
		} catch (NullPointerException e) {
			System.err.println("could not load "+path);
		}
	}
	
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        btnNewNet = new javax.swing.JButton();
        btnAccept = new javax.swing.JButton();
        btnInvite = new javax.swing.JButton();
        btnOptions = new javax.swing.JButton();
        btnChat = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        lblName = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstPeers = new javax.swing.JList();
        btnInfo = new javax.swing.JButton();
        txtNetwork = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("P2PVPN");

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);

        btnNewNet.setText("N");
        btnNewNet.setToolTipText("New Network...");
        btnNewNet.setFocusable(false);
        btnNewNet.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnNewNet.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnNewNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNewNetActionPerformed(evt);
            }
        });
        jToolBar1.add(btnNewNet);

        btnAccept.setText("A");
        btnAccept.setToolTipText("Accept Invitation...");
        btnAccept.setFocusable(false);
        btnAccept.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAccept.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAccept.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAcceptActionPerformed(evt);
            }
        });
        jToolBar1.add(btnAccept);

        btnInvite.setText("I");
        btnInvite.setToolTipText("Invite someone...");
        btnInvite.setFocusable(false);
        btnInvite.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnInvite.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnInvite.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInviteActionPerformed(evt);
            }
        });
        jToolBar1.add(btnInvite);

        btnOptions.setText("O");
        btnOptions.setToolTipText("Options...");
        btnOptions.setFocusable(false);
        btnOptions.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnOptions.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOptionsActionPerformed(evt);
            }
        });
        jToolBar1.add(btnOptions);

        btnChat.setText("C");
        btnChat.setToolTipText("Chat...");
        btnChat.setFocusable(false);
        btnChat.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnChat.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnChat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChatActionPerformed(evt);
            }
        });
        jToolBar1.add(btnChat);
        jToolBar1.add(jSeparator1);

        lblName.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        lblName.setText("Name");
        jToolBar1.add(lblName);

        lstPeers.setModel(peerListModel);
        lstPeers.setCellRenderer(peerListCellRenderer);
        lstPeers.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                lstPeersMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                lstPeersMousePressed(evt);
            }
        });
        jScrollPane1.setViewportView(lstPeers);

        btnInfo.setText("I");
        btnInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoActionPerformed(evt);
            }
        });

        txtNetwork.setText("Network");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jToolBar1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 199, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(txtNetwork)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 138, Short.MAX_VALUE)
                .add(btnInfo))
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 199, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jToolBar1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 267, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnInfo)
                    .add(txtNetwork)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void btnNewNetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewNetActionPerformed
	newNetwork.setVisible(true);
}//GEN-LAST:event_btnNewNetActionPerformed

private void btnInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfoActionPerformed
    infoWindow.setVisible(true);
}//GEN-LAST:event_btnInfoActionPerformed

/**
 * Open the options window.
 */
private void btnOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOptionsActionPerformed
	optionWindow.setNodeName(mainControl.getName());
	optionWindow.setPort(mainControl.getServerPort());
	optionWindow.setIP(mainControl.getIp());
	optionWindow.setSendLimit(mainControl.getSendLimit());
	optionWindow.setRecLimit(mainControl.getRecLimit());
	optionWindow.setSendBufferSize(mainControl.getSendBufferSize());
	optionWindow.setTCPFlush(mainControl.isTCPFlush());
	optionWindow.setPopupChat(mainControl.isPopupChat());
	optionWindow.setVisible(true);
	if (optionWindow.isOk()) {
		mainControl.setName(optionWindow.getNodeName());
		mainControl.setServerPort(optionWindow.getPort());
		mainControl.setIp(optionWindow.getIP());
		mainControl.setSendLimit(optionWindow.getSendLimit());
		mainControl.setRecLimit(optionWindow.getRecLimit());
		mainControl.setSendBufferSize(optionWindow.getSendBufferSize());
		mainControl.setTCPFlush(optionWindow.isTCPFlush());
		mainControl.setPopupChat(optionWindow.isPopupChat());
	}
}//GEN-LAST:event_btnOptionsActionPerformed

private void btnInviteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInviteActionPerformed
	inviteWindow.setVisible(true);
}//GEN-LAST:event_btnInviteActionPerformed

private void btnAcceptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAcceptActionPerformed
	acceptWindow.setVisible(true);
}//GEN-LAST:event_btnAcceptActionPerformed

private void btnChatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChatActionPerformed
	chatWindow.setVisible(true);
	setButtonIcon(btnChat, CHAT_IMG);
		if (trayIcon!=null) {
			trayIcon.setImage(Toolkit.getDefaultToolkit().getImage(InfoWindow.class.getClassLoader().getResource(P2PVPN_IMG)));
		}
}//GEN-LAST:event_btnChatActionPerformed

/**
 * Context menu for the peer-list
 */
private void lstPeersMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lstPeersMousePressed
	
	JPopupMenu menu = new JPopupMenu();
	if (menu.isPopupTrigger(evt)) {
		int i = lstPeers.locationToIndex(evt.getPoint());
		if (i>=0 && mainControl.getConnectionManager()!=null) {
			PeerID peer = (PeerID)lstPeers.getModel().getElementAt(i);
			String ip = mainControl.getConnectionManager().getRouter().getPeerInfo(peer, "vpn.ip");
			if (ip!=null) {
				JMenuItem mitem = new JMenuItem("Copy IP address");
				mitem.addActionListener(new PopupMenuListener(ip));
				menu.add(mitem);
				menu.show(evt.getComponent(), evt.getX(), evt.getY());
			}
		}
	}
}//GEN-LAST:event_lstPeersMousePressed

	void setChatBla() {
		setButtonIcon(btnChat, CHAT_BLA_IMG);
		if (trayIcon!=null) {
			trayIcon.setImage(Toolkit.getDefaultToolkit().getImage(InfoWindow.class.getClassLoader().getResource(CHAT_BLA_IMG)));
		}
	}

	public void setNodeName(String name) {
		lblName.setText(name);
	}

	/**
	 * Called from MeinControl when the network has changed.
	 */
	public void networkHasChanged() {
		btnInvite.setEnabled(mainControl.getNetworkCfg()!=null);
		peerListModel.setConnectionManager(mainControl.getConnectionManager());
		peerListCellRenderer.setMainControl(mainControl);
		if (mainControl.getConnectionManager()!=null) {
			mainControl.getConnectionManager().getRouter().addTableListener(this);
			lblName.setToolTipText(null);
			tableChanged(mainControl.getConnectionManager().getRouter());
		}
		if (mainControl.getAccessCfg()==null) {
			txtNetwork.setText("not connected");
		} else {
			txtNetwork.setText(mainControl.getAccessCfg().getProperty("network.name", ""));
		}
		chatWindow.networkHasChanged();
        infoWindow.networkHasChanged();
	}

	/**
	 * Called when the peer list changes.
	 * @param router the Router
	 */
	public void tableChanged(Router router) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				tableChangedSave();
			}
		});
	}

	public void tableChangedSave() {
		lblName.setToolTipText(mainControl.descriptionForPeer(mainControl.getConnectionManager().getLocalAddr()));
	}


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAccept;
    private javax.swing.JButton btnChat;
    private javax.swing.JButton btnInfo;
    private javax.swing.JButton btnInvite;
    private javax.swing.JButton btnNewNet;
    private javax.swing.JButton btnOptions;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JLabel lblName;
    private javax.swing.JList lstPeers;
    private javax.swing.JLabel txtNetwork;
    // End of variables declaration//GEN-END:variables


	/**
	 * A listener for the peer context menu.
	 */
	class PopupMenuListener implements ActionListener, ClipboardOwner {
		String ip;

		public PopupMenuListener(String ip) {
			this.ip = ip;
		}

		public void actionPerformed(ActionEvent e) {
			Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
			c.setContents(new StringSelection(ip), this);
		}

		public void lostOwnership(Clipboard clipboard, Transferable contents) {
		}
	}
}
