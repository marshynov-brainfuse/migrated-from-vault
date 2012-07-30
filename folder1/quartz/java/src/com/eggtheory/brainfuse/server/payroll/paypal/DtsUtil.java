package com.eggtheory.brainfuse.server.payroll.paypal;

import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

import com.eggtheory.brainfuse.server.ServerConfig;
import com.eggtheory.brainfuse.utils.EnhancedProperties;

/**
 * This is used by the main driver.
 * 
 * @author roallan
 */
public class DtsUtil extends HashMap<String, String> {

	static final long serialVersionUID = System.currentTimeMillis();
	
	private static HashMap<String, String> headers = null;
	private static boolean USE_LOGGING = false;
	private static String LEVEL = "OFF";
	private static Vector<String> restrictedList;
	private static final DtsUtil dtsUtil = new DtsUtil();
	
	// initialize the static and global properties of the class
	static {
		try {
			loadProperties();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private DtsUtil() {
		super();
	}

	/**
	 * This class takes all of the 
	 * keys and values from the various
	 * properties files and loads them into a Map oject
	 */
	public static void loadProperties() throws Exception {

		headers = new HashMap<String, String>();
		
		EnhancedProperties paypalProperties = new EnhancedProperties();
		String paypalPropertiesPath = ServerConfig.getInstance().getServerProperty("com.brainfuse.properties.paypal");
		paypalProperties.load(new FileInputStream(paypalPropertiesPath));
		
		Enumeration e = paypalProperties.keys();
		while (e.hasMoreElements()) {
			String key = (String)e.nextElement();
			String value = paypalProperties.getProperty(key);
			
			if (key.startsWith("header.")) {
				headers.put(key, value);
			} else {
				dtsUtil.put(key, value);
			}
		}
		StringTokenizer st = null;
		restrictedList = new Vector<String>();
		st = new StringTokenizer(dtsUtil.get("restricted.list"),
				DtsConsts.COMMA);
		while (st.hasMoreTokens()) {
			restrictedList.add(st.nextToken());
		}
		
	}

	/**
	 * get a resource from a specific properties file
	 *
	 * @param key
	 * @return String
	 */
	public static String getString(String key) {
		return dtsUtil.get(key);
	}

	/**
	 * return the request headers
	 * @return HashMap<String, String>
	 */
	public static HashMap<String, String> getHeaders() {
		return headers;
	}
	
	
}
