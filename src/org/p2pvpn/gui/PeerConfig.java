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

/*
 * PeerConfig.java
 *
 * Created on 17. November 2008, 11:02
 */

package org.p2pvpn.gui;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.p2pvpn.tuntap.TunTap;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.VPNConnector;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;

/**
 *
 * @author  wolfgang
 */
public class PeerConfig extends javax.swing.JFrame {

	static private Random random = new Random();
	
    /** Creates new form PeerConfig */
    public PeerConfig() {
        initComponents();
		
		Preferences prefs = Preferences.userNodeForPackage(this.getClass());
		txtNetwork.setText(prefs.get("network", ""));
		txtAccess.setText(prefs.get("access", ""));
		txtPeerIP.setText(prefs.get("ip", ""));
		spnLocalPort.getModel().setValue(prefs.getInt("port", 0));
		chkVPN.setSelected(prefs.getBoolean("useVPN", true));
		
		txtAccess.getDocument().addDocumentListener(new DocumentListener() {

			public void insertUpdate(DocumentEvent arg0) {
				changedUpdate(arg0);
			}

			public void removeUpdate(DocumentEvent arg0) {
				changedUpdate(arg0);
			}

			public void changedUpdate(DocumentEvent arg0) {
				txtAccesskChanged();
			}
		});
    }

	private void txtAccesskChanged() {
		try {
			AdvProperties accessCfg = new AdvProperties(txtAccess.getText());
			InetAddress net = InetAddress.getByName(accessCfg.getProperty("network.ip.network"));
			InetAddress subnet = InetAddress.getByName(accessCfg.getProperty("network.ip.subnet"));

			byte[] myIPb = new byte[4];
			byte[] netb = net.getAddress();
			byte[] subnetb = subnet.getAddress();

			// TODO don't create a broadcast address
			for (int i = 0; i < 4; i++) {
				myIPb[i] = (byte)(netb[i] ^ ((~subnetb[i]) & (byte)random.nextInt()));
			}

			txtPeerIP.setText((0xFF&myIPb[0])+"."+(0xFF&myIPb[1])+"."+(0xFF&myIPb[2])+"."+(0xFF&myIPb[3]));
		} catch (UnknownHostException ex) {
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

        jPanel1 = new javax.swing.JPanel();
        btnCreateNet = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtNetwork = new javax.swing.JTextArea();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        txtPeerIP = new javax.swing.JTextField();
        chkVPN = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        spnLocalPort = new javax.swing.JSpinner();
        btnCancel = new javax.swing.JButton();
        btnOK = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtAccess = new javax.swing.JTextArea();
        btnCreateAccess = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Peer Configuration");

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Network"));

        btnCreateNet.setText("Create a Network...");
        btnCreateNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCreateNetActionPerformed(evt);
            }
        });

        txtNetwork.setColumns(20);
        txtNetwork.setFont(new java.awt.Font("DejaVu Sans", 0, 8)); // NOI18N
        txtNetwork.setRows(5);
        jScrollPane1.setViewportView(txtNetwork);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 384, Short.MAX_VALUE)
                    .addComponent(btnCreateNet))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnCreateNet)
                .addContainerGap())
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Peer"));

        jLabel2.setText("VPN IP-Address");

        chkVPN.setSelected(true);
        chkVPN.setText("Create VPN");
        chkVPN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkVPNActionPerformed(evt);
            }
        });

        jLabel3.setText("Local Port");

        spnLocalPort.setModel(new javax.swing.SpinnerNumberModel(0, 0, 65535, 1));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtPeerIP, javax.swing.GroupLayout.DEFAULT_SIZE, 273, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(48, 48, 48)
                        .addComponent(spnLocalPort, javax.swing.GroupLayout.DEFAULT_SIZE, 273, Short.MAX_VALUE))
                    .addComponent(chkVPN))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(txtPeerIP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(spnLocalPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkVPN)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        btnCancel.setText("Cancel");
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        btnOK.setText("OK");
        btnOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOKActionPerformed(evt);
            }
        });

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Access"));

        txtAccess.setColumns(20);
        txtAccess.setFont(new java.awt.Font("DejaVu Sans", 0, 8)); // NOI18N
        txtAccess.setRows(5);
        jScrollPane2.setViewportView(txtAccess);

        btnCreateAccess.setText("Create Access");
        btnCreateAccess.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCreateAccessActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 384, Short.MAX_VALUE)
                    .addComponent(btnCreateAccess))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 119, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnCreateAccess)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnOK)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCancel))
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCancel)
                    .addComponent(btnOK))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void chkVPNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkVPNActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_chkVPNActionPerformed

private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
	System.exit(0);
}//GEN-LAST:event_btnCancelActionPerformed

private void btnCreateNetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCreateNetActionPerformed
	NetworkConfig nc = new NetworkConfig(this, true);
	
	nc.setVisible(true);
	AdvProperties settings = nc.getSettings();
	
	if (settings!=null) {
		txtNetwork.setText(nc.getSettings().toString(null, false, true));
	}
}//GEN-LAST:event_btnCreateNetActionPerformed

private void btnOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOKActionPerformed
	
	Preferences prefs = Preferences.userNodeForPackage(this.getClass());
	prefs.put("network", txtNetwork.getText());
	prefs.put("access", txtAccess.getText());
	prefs.put("ip", txtPeerIP.getText());
	prefs.putInt("port", (Integer) spnLocalPort.getModel().getValue());
	prefs.putBoolean("useVPN", chkVPN.isSelected());
	try {
		prefs.flush();
	} catch (BackingStoreException ex) {
		Logger.getLogger("").log(Level.WARNING, null, ex);
	}
	
	try {
        AdvProperties accessCfg = new AdvProperties(txtAccess.getText());
		
		if (!accessCfg.containsKey("network.ip.subnet")) {
	        JOptionPane.showMessageDialog(null, "Please copy an invitation into the Network field", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

        ConnectionManager cm = new ConnectionManager(accessCfg, (Integer) spnLocalPort.getModel().getValue());

        setVisible(false);

		if (chkVPN.isSelected()) {
            cm.getRouter().setLocalPeerInfo("vpn.ip", txtPeerIP.getText());
            TunTap tunTap = TunTap.createTunTap();
            tunTap.setIP(txtPeerIP.getText(), accessCfg.getProperty("network.ip.subnet"));
            new VPNConnector(cm, tunTap, cm.getRouter());
        }
        cm.getRouter().setLocalPeerInfo("name", accessCfg.getProperty("access.name", "none"));
        cm.getConnector().addIPs(accessCfg);
        org.p2pvpn.gui.Main.open(cm);
    } catch (Throwable e) {
		Logger.getLogger("").log(Level.SEVERE, "", e);
        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(0);
    }
}//GEN-LAST:event_btnOKActionPerformed

private void btnCreateAccessActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCreateAccessActionPerformed
// TODO add your handling code here:
	AdvProperties netCfg = new AdvProperties(txtNetwork.getText());
	PrivateKey netPriv = CryptoUtils.decodeRSAPrivateKey(
			netCfg.getPropertyBytes("secret.network.privateKey", null));

	KeyPair accessKp = CryptoUtils.createEncryptionKeyPair();
	
	AdvProperties accessCfg = new AdvProperties();
	accessCfg.setProperty("access.name", "none");
	accessCfg.setProperty("access.expiryDate", "none");
	accessCfg.setPropertyBytes("access.publicKey", accessKp.getPublic().getEncoded());
	accessCfg.sign("access.signature", netPriv);
	accessCfg.setPropertyBytes("secret.access.privateKey", accessKp.getPrivate().getEncoded());
	
	accessCfg.putAll(netCfg.filter("secret", true));
	
	txtAccess.setText(accessCfg.toString(null, false, true));
}//GEN-LAST:event_btnCreateAccessActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnCreateAccess;
    private javax.swing.JButton btnCreateNet;
    private javax.swing.JButton btnOK;
    private javax.swing.JCheckBox chkVPN;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSpinner spnLocalPort;
    private javax.swing.JTextArea txtAccess;
    private javax.swing.JTextArea txtNetwork;
    private javax.swing.JTextField txtPeerIP;
    // End of variables declaration//GEN-END:variables

}
