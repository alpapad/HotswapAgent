package org.hotswap.agent.plugin.elresolver;

import java.lang.reflect.Method;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;


/**
 * Purge BeanPropertiesCache && FactoryFinderCache
 * 
 * @author alpapad
 */
public class PurgeWildFlyBeanELResolverCacheCommand extends MergeableCommand {

	private static AgentLogger LOGGER = AgentLogger.getLogger(PurgeWildFlyBeanELResolverCacheCommand.class);

	private ClassLoader appClassLoader;

	public PurgeWildFlyBeanELResolverCacheCommand(ClassLoader appClassLoader) {
		this.appClassLoader = appClassLoader;
	}

	@Override
	public void executeCommand() {
		try {
			LOGGER.error("Cleaning  BeanPropertiesCache {}.",appClassLoader);
			Method beanElResolverMethod = resolveClass("org.jboss.el.cache.BeanPropertiesCache").getDeclaredMethod("clear", ClassLoader.class);
			beanElResolverMethod.setAccessible(true);
			beanElResolverMethod.invoke(null, appClassLoader);
		} catch (Exception e) {
			LOGGER.error("Error cleaning BeanPropertiesCache. {}", e, appClassLoader);
		}
		try {
			LOGGER.error("Cleaning  FactoryFinderCache {}.",appClassLoader);
			Method beanElResolverMethod = resolveClass("org.jboss.el.cache.FactoryFinderCache")
					.getDeclaredMethod("clearClassLoader", ClassLoader.class);
			beanElResolverMethod.setAccessible(true);
			beanElResolverMethod.invoke(null, appClassLoader);
		} catch (Exception e) {
			LOGGER.error("Error cleaning FactoryFinderCache. {}", e, appClassLoader);
		}		
	}

	private Class<?> resolveClass(String name) throws ClassNotFoundException {
		return Class.forName(name, true, appClassLoader);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		PurgeWildFlyBeanELResolverCacheCommand that = (PurgeWildFlyBeanELResolverCacheCommand) o;

		if (!appClassLoader.equals(that.appClassLoader))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = appClassLoader.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "PurgeWildFlyBeanELResolverCacheCommand{" + "appClassLoader=" + appClassLoader + '}';
	}
}
