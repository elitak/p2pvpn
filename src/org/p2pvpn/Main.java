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
package org.p2pvpn;

import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import org.p2pvpn.gui.MainWindow;
import org.p2pvpn.network.ConnectionManager;
import org.p2pvpn.network.VPNConnector;
import org.p2pvpn.tools.AdvProperties;


public class Main {

	public static void main(String[] args) {
		if (args.length == 5) {
            try {
                AdvProperties accessCfg = new AdvProperties();
				accessCfg.load(new FileInputStream(args[0]));

                ConnectionManager cm = new ConnectionManager(accessCfg, Integer.parseInt(args[2]));

                if (!args[3].equals("none")) {
                    cm.getRouter().setLocalPeerInfo("vpn.ip", args[3]);
					VPNConnector vpnc = VPNConnector.getVPNConnector();
					vpnc.setRouter(cm.getRouter());
					vpnc.getTunTap().setIP(args[3], args[4]);
                }
                cm.getRouter().setLocalPeerInfo("name", args[1]);
				cm.getConnector().addIPs(accessCfg);
            } catch (Exception exception) {
				Logger.getLogger("").log(Level.SEVERE, "Error during Startup", exception);
				System.exit(1);
            }
		} else {
			try {
				UIManager.setLookAndFeel(
					UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				Logger.getLogger("").log(Level.INFO, "Unable to load native look and feel", ex);
			}
			//new PeerConfig().setVisible(true);
			new MainWindow().setVisible(true);
		}
	}
	
/*
	public static void main(String[] args) {
		System.out.println("Start");
		AdvProperties props = new AdvProperties();
		
		try {
			if (args.length>0) props.load(new FileReader(args[0]));
		} catch (FileNotFoundException e1) {
		} catch (IOException e1) {
		}
		
		int port = props.getPropertyInt("localPort", 0);
		
		ConnectionManager cm = new ConnectionManager(port);
		
		String ip = props.getProperty("vpnIP", "none");
		if (!"none".equals(ip)) {
			TunTap tunTap = new TunTap();
			tunTap.setIP(ip);
			new VPNConnector(cm, tunTap, cm.getRouter());
		}
		
		for(int i=0; true; i++) {
			String host = props.getProperty("connectTo."+i);
			if (host==null) break;
			cm.connectTo(host);
		}
		
		if (props.getPropertyInt("showGUI", 1)==1) {
			gui.Main.open(cm);
		}
		
//		try {
//			System.in.read();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		
//		cm.close();
//		
//		System.out.println("End");
	}
*/
}
