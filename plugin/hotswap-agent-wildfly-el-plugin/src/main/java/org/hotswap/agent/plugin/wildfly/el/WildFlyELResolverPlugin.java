package org.hotswap.agent.plugin.wildfly.el;

import java.net.URL;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.Scheduler.DuplicateSheduleBehaviour;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;


/**
 * Clear javax.el.BeanELResolver cache after any class redefinition.
 * @author alpapad
 *
 */
@Plugin(name = "WildFlyELResolver",
        description = "Purge WildFlyELResolver class cache on any class redefinition.",
        testedVersions = {"1.0.5.Final"},
        expectedVersions = {"1.0.5.Final"})
public class WildFlyELResolverPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WildFlyELResolverPlugin.class);

    public static final String PURGE_CLASS_CACHE_METHOD_NAME = "__resetCache";

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;
    
    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     * @throws NotFoundException 
     */
    @OnClassLoadEvent(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(
           		"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" +
           		 PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class,"$$cl")
       		);
        }
        
        LOGGER.info("Patched JbossELResolver");
    }

    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     * @throws NotFoundException 
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.cache.BeanPropertiesCache")
    public static void beanPropertiesCache(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(
           		"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" +
           		 PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class,"$$cl")
       		);
        }
        LOGGER.info("Patched org.jboss.el.cache.BeanPropertiesCache");
    }
    
    /**
     * Hook on org.jboss.el.cache.BeanPropertiesCache.SoftConcurrentHashMap class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     * @throws NotFoundException 
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.cache.BeanPropertiesCache.SoftConcurrentHashMap")
    public static void beanPropertiesCache$(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        ctClass.addMethod(CtMethod.make("public java.util.Set entrySet() { return map.entrySet();}", ctClass));
        LOGGER.info("Patched org.jboss.el.cache.BeanPropertiesCache$SoftConcurrentHashMap");
    }
    //
    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     * @throws NotFoundException 
     */
    @OnClassLoadEvent(classNameRegexp = "org.jboss.el.cache.FactoryFinderCache")
    public static void factoryFinderCache(CtClass ctClass, ClassPool classPool) throws CannotCompileException, NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(
           		"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" +
           		 PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class,"$$cl")
       		);
        }       
        LOGGER.info("Patched org.jboss.el.cache.FactoryFinderCache");
    }
    
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache(Class<?> original) throws Exception {
    	LOGGER.trace("Running invalidateClassCache {}", appClassLoader);
    	PurgeWildFlyBeanELResolverCacheCommand cmd = new PurgeWildFlyBeanELResolverCacheCommand(appClassLoader, original.getName());
        scheduler.scheduleCommand(cmd, 250, DuplicateSheduleBehaviour.SKIP);
    }
    
	@OnResourceFileEvent(path = "/", filter = ".*\\.properties")
	public void refreshJsfResourceBundles(URL fileUrl, FileEvent evt, ClassLoader appClassLoader) {
		PurgeWildFlyBeanELResolverCacheCommand cmd = new PurgeWildFlyBeanELResolverCacheCommand(appClassLoader, fileUrl.getFile());
        scheduler.scheduleCommand(cmd, 250, DuplicateSheduleBehaviour.SKIP);
	}
	
	@Init
	public void initializeInstance(PluginConfiguration pluginConfiguration) {
		LOGGER.info("WildFlyELResolverPlugin Initializing");
	}
}