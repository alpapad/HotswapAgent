package org.hotswap.agent.config;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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

	// if the property is not defined in this classloader, look for parent
	// classloader and it's configuration

	// this configuration adheres to this classloader
	private final ClassLoader classLoader;

	private final MergedProperties merged;

	private final int me;

	public PluginConfiguration(ClassLoader classLoader) {
		this.me = cnt.incrementAndGet();

		this.classLoader = classLoader;

		URL configurationURL = findResource(classLoader, PLUGIN_CONFIGURATION);

		merged = new MergedProperties(configurationURL);
		bootStrap(classLoader);
		init();

		if (merged.isContainsPropertyFileDirectly()) {
			LOGGER.debug("Configuration URL" + configurationURL.getPath());
		}
	}

	private static final AtomicInteger cnt = new AtomicInteger();

	public PluginConfiguration(PluginConfiguration parent, ClassLoader classLoader) {
		this.me = cnt.incrementAndGet();
		this.classLoader = classLoader;

		URL configurationURL = null;

		LOGGER.debug("Initializing configuration for classloader:{}", classLoader);
		// search for resources not known by parent classloader (defined in THIS
		// classloader exclusively)
		// this is necessary in case of parent classloader precedence
		Enumeration<URL> urls = findResources(classLoader, PLUGIN_CONFIGURATION);

		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();

			boolean found = false;

			if (parent != null) {
				ClassLoader parentClassLoader = parent.getClassLoader();

				Enumeration<URL> parentUrls = findResources(parentClassLoader, PLUGIN_CONFIGURATION);

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

		merged = new MergedProperties(parent.merged, configurationURL);
		bootStrap(classLoader);
		init();
	}

	private void bootStrap(ClassLoader classLoader) {

		Enumeration<URL> turls = findResources(classLoader, PLUGIN_CONFIGURATION);
		if (!turls.hasMoreElements()) {
			LOGGER.trace("No Nested Configurations found");
		}

		while (turls.hasMoreElements()) {
			URL url = turls.nextElement();
			LOGGER.info("Nested Configuration (" + me + ") URL:" + url.getPath());
			merged.put(url);
		}
		String[] overlays = merged.getAllPropertiesAsArray("overlays");
		if(overlays != null ) {
			for(String overlay: overlays){
				if(overlay.trim().length() >0){
					Enumeration<URL> ohs = findResources(classLoader, overlay.trim());
					while (ohs.hasMoreElements()) {
						URL url = ohs.nextElement();
						LOGGER.info("Overlay Configuration (" + me + ") URL:" + url.getPath());
						merged.put(url);
					}
				}
			}
		}
	}

	private static Enumeration<URL> findResources(ClassLoader classLoader, String name) {
		try {
			return classLoader == null ? ClassLoader.getSystemResources(name) : classLoader.getResources(name);
		} catch (IOException e) {
			LOGGER.error("Error while loading '{}' from classloader {}", e, name, classLoader);
		}
		return Collections.emptyEnumeration();
	}

	private static URL findResource(ClassLoader classLoader, String name) {
		return classLoader == null ? ClassLoader.getSystemResource(name) : classLoader.getResource(name);
	}

	/**
	 * Initialize the configuration.
	 */
	protected void init() {
		LogConfigurationHelper.configureLog(merged);

		initPluginPackage();

		initExtraClassPath();
	}

	private void initPluginPackage() {
		// only for self property (not parent)
		if (merged.containsKey("pluginPackages")) {
			String pluginPackages = merged.getProperty("pluginPackages");
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
		return merged.getProperty(property);
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
		return merged.getProperty(property, defaultValue);
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
		return merged.getPropertyBoolean(property);
	}

	/**
	 * Get configuration property value
	 *
	 * @param property
	 *            property name
	 * @return the property value or null if not defined
	 */
	public String getAllProperty(String property) {
		return merged.getAllProperties(property);
	}

	/**
	 * Get extraClasspath property as URL[].
	 *
	 * @return extraClasspath or empty array (never null)
	 */
	public URL[] getExtraClasspath() {
		URL[] extraClassPath = merged.getUrls("extraClasspath");
		if (LOGGER.isLevelEnabled(Level.DEBUG)) {
			LOGGER.debug("Getting extraClasspath {}. ", Arrays.toString(extraClassPath));
		}
		return extraClassPath;
	}

	/**
	 * Converts watchResources property to URL array. Invalid URLs will be
	 * skipped and logged as error.
	 */
	public URL[] getWatchResources() {
		URL[] watchResources = merged.getUrls("watchResources");
		if (LOGGER.isLevelEnabled(Level.DEBUG)) {
			LOGGER.debug("Getting watchResources {}. ", Arrays.toString(watchResources));
		}
		return watchResources;
	}

	/**
	 * Return configuration property webappDir as URL.
	 */
	public URL getWebappDir() {
		URL u = merged.getUrl("webappDir");
		if (u == null) {
			LOGGER.error(
					"Invalid configuration value for webappDir: '{}' is not a valid URL or path and will be skipped.",
					getProperty("webappDir"));
			return null;
		}
		return u;
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
		return merged.isContainsPropertyFileDirectly();
	}

	// public URL getConfigurationURL() {
	// return configurationURL;
	// }

	@Override
	public String toString() {
		return "PluginConfiguration [me=" + me + ", getExtraClasspath()=" + Arrays.toString(getExtraClasspath())
				+ ", getWatchResources()=" + Arrays.toString(getWatchResources()) + ", getDisabledPlugins()="
				+ getDisabledPlugins() + ", containsPropertyFile()=" + containsPropertyFile() + "]";
	}

}
