package org.hotswap.agent.plugin.watchResources;

import java.net.URL;
import java.net.URLClassLoader;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.HotswapAgentClassLoaderExt;
import org.hotswap.agent.util.classloader.URLClassLoaderHelper;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoader;
import org.hotswap.agent.util.classloader.WatchResourcesClassLoaderExt;
import org.hotswap.agent.watch.Watcher;

/**
 * Support for watchResources configuration property.
 *
 * This plugin creates special WatchResourcesClassLoader witch returns only
 * modified resources on watchResources path. It then modifies application
 * classloader to look for resources first in WatchResourcesClassLoader and only
 * if the resource is not found, standard execution proceeds.
 *
 * Works for any java.net.URLClassLoader which delegates to URLClassPath
 * property to findResource() (typical scenario).
 */
/*
@Plugin(name = "WatchResources", description = "Support for watchResources configuration property.", testedVersions = {
		"JDK 1.7.0_45" }, expectedVersions = { "JDK 1.6+" })
		
*/
public class WatchResourcesPlugin {
	private static AgentLogger LOGGER = AgentLogger.getLogger(WatchResourcesPlugin.class);

	@Init
	Watcher watcher;

	@Init
	ClassLoader appClassLoader;


	/**
	 * For each classloader check for watchResources configuration instance with
	 * hotswapper.
	 */
	@Init
	public static void init(PluginManager pluginManager, PluginConfiguration pluginConfiguration, ClassLoader appClassLoader) {
		LOGGER.debug("Init plugin at classLoader {}", appClassLoader);

		// synthetic classloader, skip
		if (appClassLoader instanceof WatchResourcesClassLoader.UrlOnlyClassLoader) {
			return;
		}

		// init only if the classloader contains directly the property file (not
		// in parent classloader)
		if (!pluginConfiguration.containsPropertyFile()) {
			LOGGER.debug("ClassLoader {} does not contain hotswap-agent.properties file, WatchResources skipped.",
					appClassLoader);
			return;
		}

		// and watch resources are set
		URL[] watchResources = pluginConfiguration.getWatchResources();
		if (watchResources.length == 0) {
			LOGGER.debug("ClassLoader {} has hotswap-agent.properties watchResources empty.", appClassLoader);
			return;
		}

		LOGGER.warning("********************* Classloader '{}' is of type '{}'",	appClassLoader, appClassLoader.getClass());
				
		
		if (!(appClassLoader instanceof URLClassLoader) || !(appClassLoader instanceof HotswapAgentClassLoaderExt)) {
			LOGGER.warning(
					"Unable to modify application classloader. Classloader '{}' is of type '{}',"
						+ "but only URLClassLoader is supported.\n"
							+ "*** watchResources configuration property will not be handled on JVM level ***",
					appClassLoader, appClassLoader.getClass());
			return;
		}

		// create new plugin instance
		WatchResourcesPlugin plugin = (WatchResourcesPlugin) pluginManager.getPluginRegistry()
				.initializePlugin(WatchResourcesPlugin.class.getName(), appClassLoader);

		// and init it with watchResources path
		plugin.init(watchResources);
	}

	/**
	 * Init the plugin instance for resources.
	 *
	 * @param watchResources
	 *            resources to watch
	 */
	private void init(URL[] watchResources) {
		
		if(appClassLoader instanceof URLClassLoader){
			// Classloader to return only modified resources on watchResources path.
			WatchResourcesClassLoader watchResourcesClassLoader = new WatchResourcesClassLoader(false);
			// configure the classloader to return only changed resources on
			// watchResources path
			watchResourcesClassLoader.initWatchResources(watchResources, watcher);
	
			// modify the application classloader to look for resources first in
			// watchResourcesClassLoader
			URLClassLoaderHelper.setWatchResourceLoader((URLClassLoader) appClassLoader, watchResourcesClassLoader);
		} else {
			WatchResourcesClassLoaderExt watchResourcesClassLoaderExt = new WatchResourcesClassLoaderExt();
			watchResourcesClassLoaderExt.initWatchResources(appClassLoader, watchResources, watcher);
		}
	}
}
