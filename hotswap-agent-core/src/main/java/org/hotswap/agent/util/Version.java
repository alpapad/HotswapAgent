package org.hotswap.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Information about hotswap agent version.
 *
 * @author Jiri Bubnik
 */
public class Version {

	/**
	 * Return current version.
	 * 
	 * @return the version.
	 */
	public static String version() {
		try {
			Properties prop = new Properties();
			InputStream in = Version.class.getResourceAsStream("/version.properties");
			prop.load(in);
			in.close();

			return prop.getProperty("version");
		} catch (IOException e) {
			return "unknown";
		}
	}
}
