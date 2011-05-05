package org.p2pvpn.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author steve.kliebisch
 */
public class ProfileManager {

    private static Properties props = new Properties();
    private static final String file = "profile.ini";
    private static String networkName = "default";


    static final File profileFile = new File(file);

    /*
    static{
         props = new Properties();//Create the properties object
        //create new File if not exist
        if(!profileFile.exists())
        {
            try {
                setDefaults(networkName);
                write(profileFile); 
            } catch (IOException ex) {
                Logger.getLogger(ProfileManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
*/
    public ProfileManager load(String network){
        networkName = network;

        //load values
        try {
            read(profileFile); //Read the ini file
        } catch (IOException ex) {
            Logger.getLogger(ProfileManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return this;
    }

    public  void flush(){
        try {
            write(profileFile);
        } catch (IOException ex) {
            Logger.getLogger(ProfileManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void putGlobal(String key, String val){
        props.setProperty(key, val);
    }

    public void put(String key, String val){
        props.setProperty(networkName+'.'+key, val);
    }

    public void  putInt(String key, Integer val){
         props.setProperty(networkName+'.'+key, val.toString() );
    }

    public void putBoolean(String key, Boolean val){
         props.setProperty(networkName+'.'+key, val.toString() );
    }

    public void putDouble(String key, Double val){
         props.setProperty(networkName+'.'+key, val.toString() );
    }


    public String getGlobal(String key, String def){
        return props.getProperty(key, def);
    }

    public String get(String key, String def){
        return props.getProperty(networkName+'.'+key, def);
    }

    public Integer getInt(String key, Integer def){
        return Integer.parseInt(  props.getProperty(networkName+'.'+key, def.toString() ) );
    }

    public Boolean getBoolean(String key, Boolean def){
        return ( props.getProperty(networkName + '.' + key, def.toString() ).equals("true") ) ? true : false;
    }

    public Double getDouble(String key, Integer def){
        Double d = 0.0;
        try {
            d = Double.valueOf( props.getProperty(networkName + '.' + key, def.toString() ) );
        } catch (NumberFormatException e) {}
        return d;
    }

    public void remove(String key)
    {
        props.remove(key);
    }

	public ProfileDescription[] getProfiles() {
		final List<ProfileDescription> result = new ArrayList<ProfileDescription>();

		for (Entry<Object, Object> entry : props.entrySet()) {
			final String key = entry.getKey().toString();
			final String value = entry.getValue().toString();

			if (key.endsWith(".name")) {
				result.add(new ProfileDescription(
						key.substring(0, key.length()-5),
						value));
			}
		}

		ProfileDescription[] resultArray = new ProfileDescription[result.size()];
		result.toArray(resultArray);

		Arrays.sort(resultArray, new Comparator<ProfileDescription>() {
			public int compare(ProfileDescription p1, ProfileDescription p2) {
				return p1.getName().compareTo(p2.getName());
			}
		});

		return resultArray;
	}

    public static void setDefaults(String defaultName){
        props.setProperty(defaultName+'.'+"name", "no name");
        props.setProperty(defaultName+'.'+"serverPort", "0");
        props.setProperty(defaultName+'.'+"popupChat", "1");
        props.setProperty(defaultName+'.'+"sendLimit", "0.0");
        props.setProperty(defaultName+'.'+"recLimit", "0.0");
        props.setProperty(defaultName+'.'+"sendBufferSize", "10");
        props.setProperty(defaultName+'.'+"tcpFlush", "0");
    }


    private static void read(File file) throws IOException {
            FileInputStream fis = new FileInputStream(file);//Create a FileInputStream
            props.load(fis);//load the ini to the Properties file
            fis.close();//close
    }

    private static void write(File file) throws IOException {
            FileOutputStream fos = new FileOutputStream(file);//Create a FileOutputStream
            props.store(fos, "");//write the Properties object values to our ini file
            fos.close();//close
    }

	public class ProfileDescription {
		private final String key;
		private final String name;

		public ProfileDescription(String key, String name) {
			this.key = key;
			this.name = name;
		}

		public String getKey() {
			return key;
		}

		public String getName() {
			return name;
		}
	}
}
