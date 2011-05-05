package org.p2pvpn.tools;


import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author steve.kliebisch
 */
public class AdapterManager {

    private String arch = getArch();
    private String path = System.getProperty("user.dir");

    private String driverFolder = path+"\\driver\\win"+arch+"\\";
    private String driverInstaller = "tapinstall.exe";
    private String driverFile = "OemWin2k.inf";
    private String driverName = "tapoas";

    private Integer adapterNumber = 0;

    public Boolean check(){
        BufferedReader in;
        String text;

    	try {
             String[] cmd = {
                 driverFolder+driverInstaller,
                 "find",
                 driverName
             };
            Logger.getLogger("").log(Level.INFO, "Check adapter: " + cmd);
            Process f = Runtime.getRuntime().exec(cmd);
            in = new BufferedReader( new InputStreamReader( f.getInputStream()) );
            while ( (text = in.readLine() ) != null) {
                if( text.startsWith("ROOT\\NET\\") ){
                    Logger.getLogger("").log(Level.INFO, "Found adapter: "+text);
                    adapterNumber++;
                }
            }
            f.waitFor();
    	} catch (Exception e) { Logger.getLogger("").log(Level.WARNING, "Could not check adapter", e); }

        if(adapterNumber > 0) {
            return true;
        } else {
            Logger.getLogger("").log(Level.WARNING, "No adapter found");
            return false;
        }
    }

    public Boolean install() {
        BufferedReader i;
        String text;
        Boolean done = false;
        Logger.getLogger("").log(Level.INFO, "arch: " + getArch() );
        Boolean install = (JOptionPane.YES_OPTION ==
                        JOptionPane.showConfirmDialog(null,
                        "Install a new Tap-Win32 Adapter ?", "Adapter Install",
                        JOptionPane.YES_NO_OPTION));

        if(install)
        {
            try {
                 //String[] cmd = {"cmd", "/c", "start", "/D", path+"\\driver\\", "/B", "add_adapter.bat"};

                 String[] cmd = {
                     driverFolder+driverInstaller,
                     "install",
                     driverFolder+driverFile,
                     driverName
                 };
                Process f = Runtime.getRuntime().exec(cmd);
                i = new BufferedReader( new InputStreamReader( f.getInputStream()) );
                while ( ( text = i.readLine() ) != null ) {
                        Logger.getLogger("").log(Level.INFO, text);
                        if( text.startsWith("Driver installed") ){
                            done = true;
                        }
                }
                f.waitFor();
            } catch (Exception e) {
                Logger.getLogger("").log(Level.WARNING, "Could not install adapter", e);
                return false;
            }
        }
        return (done) ? true : false;
    }

    public Boolean remove() {
        Logger.getLogger("").log(Level.INFO, "try to remove Virtual Ethernet Adapter");
    	try {
             String[] cmd = {
                 driverFolder+driverInstaller,
                 "remove",
                 driverName
             };
            Process f = Runtime.getRuntime().exec(cmd);
            f.waitFor();
            return true;
    	} catch (Exception e) {
            Logger.getLogger("").log(Level.WARNING, "Could not remove adapter", e);
            return false;
    	}
    }

    private String getArch(){
        return (new File("C:/Program Files (x86)").exists()) ? "64" : "32";
    }


}
