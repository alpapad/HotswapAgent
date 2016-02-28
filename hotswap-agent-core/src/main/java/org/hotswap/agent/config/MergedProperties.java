package org.hotswap.agent.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.hotswap.agent.logging.AgentLogger;

public class MergedProperties {
	private static AgentLogger LOGGER = AgentLogger.getLogger(MergedProperties.class);

	private final Properties base;
	
	private Map<String, Properties> properties = new LinkedHashMap<>();

	private final MergedProperties parent;
	
	private boolean containsPropertyFileDirectly;
	
	public MergedProperties() {
		this(null, null);
	}
	
	public MergedProperties(URL url) {
		this(null, url);
	}
	
	public MergedProperties(MergedProperties parent) {
		this(parent, null);
	}
	
	public MergedProperties(MergedProperties parent, URL url) {
		this.parent = parent;
		if(url != null) {
			base = this.load(url);
		} else {
			base = null;
		}
		this.containsPropertyFileDirectly = base != null;
	}

	public Properties put(String key, Properties value) {
		return properties.put(key, value);
	}

	public boolean put(URL url) {
		final String key = url.toExternalForm();
		if(!properties.containsKey(key)){
			Properties p = load(url);
			if(p != null) {
				properties.put(key,p);
				return true;
			}
		}
		return false;
	}

	
	

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getProperty(String property) {
		if (base != null && base.containsKey(property)) {
			return base.getProperty(property);
		} else if (parent != null) {
			return parent.getProperty(property);
		} else {
			return null;
		}
	}
	
	public String getPropertyRecursive(String property) {
		return getPropertyRecursive(property, null);
	}
	
	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getPropertyRecursive(String property, String defaultValue) {
		if (base != null && base.containsKey(property)) {
			return base.getProperty(property);
		} else {
			for (Properties p : properties.values()) {
				if (p.containsKey(property)) {
					return p.getProperty(property);
				}
			}
		}
		if (parent != null) {
			return parent.getProperty(property);
		}
		return defaultValue;
	}

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @param defaultValue
	 *            value to return if property not defined
	 * @return the property value or null if not defined
	 */
	public String getProperty(String property, String defaultValue) {
		String value = getProperty(property);
		return value != null ? value : defaultValue;
	}

	/**
	 * Convenience method to get property as a boolean value using
	 * Boolean.valueOf().
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public boolean getPropertyBoolean(String property) {
		String value = getProperty(property);
		if(value != null) {
			return  Boolean.valueOf(value);
		}
		return false;
	}

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getAllProperties(String property) {
		String value = getProperty(property);
		for (Properties p : properties.values()) {
			if (p.containsKey(property)) {
				String pv = p.getProperty(property);
				if (pv == null || pv.trim().length() == 0) {
					continue;
				}
				if (value == null || value.trim().length() == 0) {
					value = pv.trim();
				} else {
					value = value.trim() + "," + pv.trim();
				}
			}
		}
		return value;
	}
	
	/**
	 * Get all properties as an array
	 * 
	 * @param property
	 * @return
	 */
	public String[] getAllPropertiesAsArray(String property){
		String resources = getAllProperties(property);
		LinkedHashSet<String> ret = new LinkedHashSet<String>();

		if (resources != null) {
			StringTokenizer tokenizer = new StringTokenizer(resources, ",;");
			while (tokenizer.hasMoreTokens()) {
				String name = tokenizer.nextToken().trim();
				if (name != null && name.trim().length() > 0) {
					ret.add(name);
				}
			}
		}
		return ret.toArray(new String[ret.size()]);
	}
	
	public URL[] getUrls(String property) {
		return convertToURL(getAllProperties(property));

	}

	public URL getUrl(String property) {
		String value = this.getProperty(property);
		if(value != null && value.trim().length()>0) {
			try {
				return resourceNameToURL(value);
			} catch (Exception e) {
			}
		}
		return null;
	}
	
	public boolean isContainsPropertyFileDirectly() {
		return containsPropertyFileDirectly;
	}

	public boolean containsKey(String key){
		return base != null && base.containsKey(key);
	}
	
	public void clear() {
		properties.clear();
	}
	
	public Set<String> stringPropertyNames() {
		LinkedHashSet<String> props = new LinkedHashSet<>();
		if(base != null) {
			props.addAll(base.stringPropertyNames());
		}
		for(Properties p: properties.values()) {
			props.addAll(p.stringPropertyNames());
		}
		return props;
	}
	
	private Properties load(URL url) {
		LOGGER.debug("LoadedProperties Loading: {}", url);
		Properties p = new Properties();
		try(InputStream is = url.openStream()) {
			p.load(is);
			LOGGER.debug("LoadedProperties {} {}", url, p.toString());
			return p;
		} catch (IOException e) {
			LOGGER.error("Error while loading properties form URL {} ", e,  url);
		}
		return null;
	}
	
	private URL[] convertToURL(String resources) {
		LinkedHashSet<URL> ret = new LinkedHashSet<>();

		if (resources != null) {
			StringTokenizer tokenizer = new StringTokenizer(resources, ",;");
			while (tokenizer.hasMoreTokens()) {
				String name = tokenizer.nextToken().trim();
				try {
					if (name != null && name.trim().length() > 0) {
						ret.add(resourceNameToURL(name));
					}
				} catch (Exception e) {
					LOGGER.error("Invalid configuration value: '{}' is not a valid URL or path and will be skipped.",
							name, e);
				}
			}
		}

		return ret.toArray(new URL[ret.size()]);
	}

	public static URL resourceNameToURL(String resource) throws Exception {
		try {
			// Try to format as a URL?
			return new URL(resource);
		} catch (MalformedURLException e) {
			// try to locate a file
			if (resource.startsWith("./")) {
				resource = resource.substring(2);
			}

			File file = new File(resource).getCanonicalFile();
			return file.toURI().toURL();
		}
	}
}
