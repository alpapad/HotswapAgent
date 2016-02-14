package org.hotswap.agent.config;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;
import org.hotswap.agent.util.classloader.URLClassLoaderHelper;

/**
 * Plugin configuration.
 * <p/>
 * Single instance exists for each classloader.
 *
 * @author Jiri Bubnik
 */
public class PluginConfiguration {
	private static AgentLogger LOGGER = AgentLogger.getLogger(PluginConfiguration.class);

	private static final String PLUGIN_CONFIGURATION = "hotswap-agent.properties";

	private List<Properties> properties = new ArrayList<>();

	Properties base = new Properties();

	// if the property is not defined in this classloader, look for parent
	// classloader and it's configuration
	PluginConfiguration parent;

	// this configuration adheres to this classloader
	ClassLoader classLoader;

	// the hotswap-agent.properties file (or null if not defined for this
	// classloader)
	URL configurationURL;

	LinkedHashSet<URL> knownUrls = new LinkedHashSet<>();

	//Map<URL, Properties> configurations = new TreeMap<>();

	// is property file defined directly in this classloader?
	boolean containsPropertyFileDirectly = false;

	public PluginConfiguration(ClassLoader classLoader) {
		this.classLoader = classLoader;
		configurationURL = classLoader == null ? ClassLoader.getSystemResource(PLUGIN_CONFIGURATION) : classLoader.getResource(PLUGIN_CONFIGURATION);

		try {
			if (configurationURL != null) {
				containsPropertyFileDirectly = true;
				bootStrap(classLoader, configurationURL);
				init();
				LOGGER.debug("Configuration URL" + configurationURL.getPath());
			}
		} catch (Exception e) {
			LOGGER.error("Error while loading 'hotswap-agent.properties' from base URL " + configurationURL, e);
		}
	}

	private static final AtomicInteger cnt = new AtomicInteger();

	int me = 0;

	public PluginConfiguration(PluginConfiguration parent, ClassLoader classLoader) {
		me = cnt.incrementAndGet();
		this.parent = parent;
		this.classLoader = classLoader;

		LOGGER.debug("Initializing configuration for classloader:{}", classLoader);
		// search for resources not known by parent classloader (defined in THIS classloader exclusively)
		// this is necessary in case of parent classloader precedence
		try {
			Enumeration<URL> urls = classLoader == null ? ClassLoader.getSystemResources(PLUGIN_CONFIGURATION) : classLoader.getResources(PLUGIN_CONFIGURATION);
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();

				boolean found = false;

				if (parent != null) {
					ClassLoader parentClassLoader = parent.getClassLoader();
					Enumeration<URL> parentUrls = parentClassLoader == null
							? ClassLoader.getSystemResources(PLUGIN_CONFIGURATION)
							: parentClassLoader.getResources(PLUGIN_CONFIGURATION);
					while (parentUrls.hasMoreElements()) {
						if (url.equals(parentUrls.nextElement())) {
							found = true;
						}
					}
				}
				if (!found) {
					configurationURL = url;
					break;
				}
			}
		} catch (IOException e) {
			LOGGER.error("Error while loading 'hotswap-agent.properties' from URL " + configurationURL, e);
		}

		if (configurationURL == null && parent != null) {
			configurationURL = parent.configurationURL;
			LOGGER.debug("Classloader does not contain 'hotswap-agent.properties', using parent file '{}'", parent.configurationURL);
		} else {
			LOGGER.debug("Classloader contains 'hotswap-agent.properties' at location '{}'", configurationURL);
			containsPropertyFileDirectly = true;
		}

		if (configurationURL != null) {
			knownUrls.add(configurationURL);
		}

		try {
			if (configurationURL != null) {
				bootStrap(classLoader, configurationURL);
			}
			init();
		} catch (Exception e) {
			LOGGER.error("Error while loading 'hotswap-agent.properties' from URL " + configurationURL, e);
		}
	}

	private void bootStrap(ClassLoader classLoader, URL configurationURL) throws IOException {

		if (configurationURL != null) {
			base.load(configurationURL.openStream());
		}

		Enumeration<URL> turls;
		try {
			turls = classLoader == null ? ClassLoader.getSystemResources(PLUGIN_CONFIGURATION)
					: classLoader.getResources(PLUGIN_CONFIGURATION);
			if(!turls.hasMoreElements()) {
				LOGGER.trace("No Nested Configurations found");
			}
			
			while (turls.hasMoreElements()) {
				URL url = turls.nextElement();
				LOGGER.info("Nested Configuration (" + me + ") URL:" + url.getPath());
				Properties p = load(url);
				if(p!= null) {
					properties.add(p);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Error while loading 'hotswap-agent.properties' from classloader " + classLoader, e);
			e.printStackTrace();
		}

	}

	private Properties load(URL u) {
		LOGGER.debug("LoadedProperties Loading: {}", u);
		Properties p = new Properties();
		try {
			p.load(u.openStream());
			LOGGER.debug("LoadedProperties {} {}", u, p.toString());
			return p;
		} catch (IOException e) {
			LOGGER.error("Error while loading 'hotswap-agent.properties' from URL " + configurationURL, e);
		}
		return null;
	}

	/**
	 * Initialize the configuration.
	 */
	protected void init() {
		LogConfigurationHelper.configureLog(base);

		initPluginPackage();

		initExtraClassPath();
	}

	private void initPluginPackage() {
		// only for self property (not parent)
		if (base.containsKey("pluginPackages")) {
			String pluginPackages = base.getProperty("pluginPackages");
			for (String pluginPackage : pluginPackages.split(",")) {
				PluginManager.getInstance().getPluginRegistry().scanPlugins(getClassLoader(), pluginPackage);
			}
			return;
		}
	}

	private void initExtraClassPath() {
		URL[] extraClassPath = getExtraClasspath();
		
		LOGGER.debug("Setting extraClasspath to {} on classLoader {}. ", Arrays.toString(extraClassPath), classLoader);
		if (extraClassPath.length > 0) {
			if (classLoader instanceof URLClassLoader) {
				URLClassLoaderHelper.prependClassPath((URLClassLoader) classLoader, extraClassPath);
			} else if (classLoader instanceof HotswapAgentClassLoaderExt) {
				((HotswapAgentClassLoaderExt) classLoader).setExtraClassPath(extraClassPath);
			} else {
				LOGGER.debug(
						"Unable to set extraClasspath to {} on classLoader {}. " + "Only URLClassLoader is supported.\n"
								+ "*** extraClasspath configuration property will not be handled on JVM level ***",
						Arrays.toString(extraClassPath), classLoader);
			}
		}
	}

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getProperty(String property) {
		if (base.containsKey(property)) {
			return base.getProperty(property);
		} else if (parent != null) {
			return parent.getProperty(property);
		} else {
			return null;
		}
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
		if (base.containsKey(property)) {
			return Boolean.valueOf(base.getProperty(property));
		} else if (parent != null) {
			return parent.getPropertyBoolean(property);
		} else {
			return false;
		}
	}

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getAllProperty(String property) {
		String v = null;
		if (base.containsKey(property)) {
			v = base.getProperty(property);
		}
		for (Properties p : properties) {
			if (p.containsKey(property)) {
				String pv = p.getProperty(property);
				if (pv == null || pv.trim().length() == 0) {
					continue;
				}
				if (v == null) {
					v = pv.trim();
				} else {
					v = v.trim() + "," + pv.trim();
				}
			}
		}
		if (v == null && parent != null) {
			return parent.getProperty(property);
		} else {
			return v;
		}
	}

	/**
	 * Get extraClasspath property as URL[].
	 *
	 * @return extraClasspath or empty array (never null)
	 */
	public URL[] getExtraClasspath() {
		URL[] extraClassPath = convertToURL(getAllProperty("extraClasspath"));
		if(LOGGER.isLevelEnabled(Level.DEBUG)) {
			LOGGER.debug("Getting extraClasspath {}. ", Arrays.toString(extraClassPath));
		}
		return extraClassPath;
	}

	/**
	 * Converts watchResources property to URL array. Invalid URLs will be
	 * skipped and logged as error.
	 */
	public URL[] getWatchResources() {
		URL[] watchResources = convertToURL(getAllProperty("watchResources"));
		if(LOGGER.isLevelEnabled(Level.DEBUG)) {
			LOGGER.debug("Getting watchResources {}. ", Arrays.toString(watchResources));
		}
		return watchResources;
	}

	/**
	 * Return configuration property webappDir as URL.
	 */
	public URL getWebappDir() {
		try {
			String webappDir = getProperty("webappDir");
			if (webappDir != null && webappDir.length() > 0) {
				return resourceNameToURL(webappDir);
			} else {
				return null;
			}
		} catch (Exception e) {
			LOGGER.error(
					"Invalid configuration value for webappDir: '{}' is not a valid URL or path and will be skipped.",
					getProperty("webappDir"), e);
			return null;
		}
	}

	/**
	 * List of disabled plugin names
	 */
	public List<String> getDisabledPlugins() {
		List<String> ret = new ArrayList<String>();
		for (String disabledPlugin : getProperty("disabledPlugins", "").split(",")) {
			ret.add(disabledPlugin.trim());
		}
		return ret;
	}

	/**
	 * Check if the plugin is disabled (in this classloader)
	 */
	public boolean isDisabledPlugin(String pluginName) {
		return HotswapAgent.isPluginDisabled(pluginName) || getDisabledPlugins().contains(pluginName);
	}

	/**
	 * Check if the plugin is disabled (in this classloader)
	 */
	public boolean isDisabledPlugin(Class<?> pluginClass) {
		Plugin pluginAnnotation = pluginClass.getAnnotation(Plugin.class);
		return isDisabledPlugin(pluginAnnotation.name());
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

	private static URL resourceNameToURL(String resource) throws Exception {
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

	/**
	 * Returns classloader associated with this configuration (i.e. it was
	 * initiated from).
	 *
	 * @return the classloader
	 */
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	/**
	 * Does this classloader contain the property file directly, or is it
	 * acquired through parent classloader.
	 *
	 * @return if this contains directly the property file
	 */
	public boolean containsPropertyFile() {
		return containsPropertyFileDirectly;
	}

	public URL getConfigurationURL() {
		return configurationURL;
	}

	public LinkedHashSet<URL> getKnownUrls() {
		return knownUrls;
	}

	@Override
	public String toString() {
		return "PluginConfiguration [me=" + me + ", getExtraClasspath()=" + Arrays.toString(getExtraClasspath())
				+ ", getWatchResources()=" + Arrays.toString(getWatchResources()) + ", getWebappDir()=" + getWebappDir()
				+ ", getDisabledPlugins()=" + getDisabledPlugins() + ", containsPropertyFile()="
				+ containsPropertyFile() + ", getConfigurationURL()=" + getConfigurationURL() + ", getKnownUrls()="
				+ getKnownUrls() + "]";
	}

}
