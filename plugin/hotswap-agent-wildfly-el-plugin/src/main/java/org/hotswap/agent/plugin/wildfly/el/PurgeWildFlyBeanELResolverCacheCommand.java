package org.hotswap.agent.plugin.wildfly.el;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

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

	private String className;

	public PurgeWildFlyBeanELResolverCacheCommand(ClassLoader appClassLoader, String className) {
		this.appClassLoader = appClassLoader;
		this.className = className;
	}

	@Override
	public void executeCommand() {
		LOGGER.info("Cleaning  BeanPropertiesCache {} {}.", className, appClassLoader);
		if (className != null) {
			try {
				ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

				try {
					Thread.currentThread().setContextClassLoader(appClassLoader);
					Method beanElResolverMethod = appClassLoader.loadClass("org.jboss.el.cache.BeanPropertiesCache").getDeclaredMethod("getProperties", new Class<?>[] {});
					Object o = beanElResolverMethod.invoke(null);

					@SuppressWarnings("unchecked")
					Map<Class<?>, Object> m = Map.class.cast(o);

					Iterator<Map.Entry<Class<?>, Object>> it = m.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Class<?>, Object> entry = it.next();
						if(entry.getKey().getClassLoader() == appClassLoader) {
							if (entry.getKey().getName().equals(className) || (entry.getKey().getName()).equals(className + "$Proxy$_$$_WeldSubclass")) {
								it.remove();
							}
						}
					}
				} finally {
					Thread.currentThread().setContextClassLoader(oldContextClassLoader);
				}
			} catch (Exception e) {
				LOGGER.error("Error cleaning BeanPropertiesCache. {}", e, className);
			}
		} else {

			try {
				LOGGER.info("Cleaning  BeanPropertiesCache {}.", appClassLoader);
				Method beanElResolverMethod = resolveClass("org.jboss.el.cache.BeanPropertiesCache").getDeclaredMethod("clear", ClassLoader.class);
				beanElResolverMethod.setAccessible(true);
				beanElResolverMethod.invoke(null, appClassLoader);
			} catch (Exception e) {
				LOGGER.error("Error cleaning BeanPropertiesCache. {}", e, appClassLoader);
			}
			try {
				LOGGER.info("Cleaning  FactoryFinderCache {}.", appClassLoader);
				Method beanElResolverMethod = resolveClass("org.jboss.el.cache.FactoryFinderCache").getDeclaredMethod("clearClassLoader", ClassLoader.class);
				beanElResolverMethod.setAccessible(true);
				beanElResolverMethod.invoke(null, appClassLoader);
			} catch (Exception e) {
				LOGGER.error("Error cleaning FactoryFinderCache. {}", e, appClassLoader);
			}
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
