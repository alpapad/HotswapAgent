package org.hotswap.agent.plugin.elresolver;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.Scheduler.DuplicateSheduleBehaviour;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
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

    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     */
    @OnClassLoadEvent(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(
           		"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();" +
           		 PluginManagerInvoker.buildInitializePlugin(WildFlyELResolverPlugin.class,"$$cl")
       		);
        }
        LOGGER.info("Patched JbossELResolver");
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache(ClassLoader appClassLoader) throws Exception {
    	LOGGER.trace("Running invalidateClassCache {}", appClassLoader);
    	PurgeWildFlyBeanELResolverCacheCommand cmd = new PurgeWildFlyBeanELResolverCacheCommand(appClassLoader);
        scheduler.scheduleCommand(cmd, 250, DuplicateSheduleBehaviour.SKIP);
    }

}