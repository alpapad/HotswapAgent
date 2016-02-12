package org.hotswap.agent.plugin.elresolver;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Clear javax.el.BeanELResolver cache after any class redefinition.
 *
 * @author Vladimir Dvorak
 */
@Plugin(name = "ELResolver",
        description = "Purge BeanELResolver class cache on any class redefinition.",
        testedVersions = {"2.2"},
        expectedVersions = {"2.2"})
public class ELResolverPlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ELResolverPlugin.class);

    public static final String PURGE_CLASS_CACHE_METHOD_NAME = "__resetCache";

    @Init
    Scheduler scheduler;

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    /**
     * Hook on BeanELResolver class and for each instance:
     * - ensure plugin is initialized
     * - register instances using registerBeanELResolver() method
     */
    @OnClassLoadEvent(classNameRegexp = "javax.el.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {

        boolean found = false;
        if (checkJuelEL(ctClass)) {
            found = true;
            LOGGER.error("JuelEL - javax.el.BeanELResolver - method added " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader). ");
        } else if (checkApacheEL(ctClass)) {
            found = true;
            LOGGER.error("ApacheEL - javax.el.BeanELResolver - method added " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader). ");
        } else if (checkJBoss_3_0_EL(ctClass)) {
            found = true;
            LOGGER.error("JBossEL 3.0 - javax.el.BeanELResolver - method added " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader). ");
        }

        if (!found) {
            LOGGER.warning("Unable to add javax.el.BeanELResolver." + PURGE_CLASS_CACHE_METHOD_NAME + "() method. Purging will not be available.");
        } else {
            String initPlugin = PluginManagerInvoker.buildInitializePlugin(ELResolverPlugin.class);
            String registerThis = PluginManagerInvoker.buildCallPluginMethod(ELResolverPlugin.class, "registerBeanELResolver",
                    "this", "java.lang.Object");

            for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                constructor.insertAfter(initPlugin);
                constructor.insertAfter(registerThis);
            }
        }
    }


    private static boolean checkJuelEL(CtClass ctClass)
    {
        try {
            // JUEL, (JSF BeanELResolver[s])
            // check if we have purgeBeanClasses method
            ctClass.getDeclaredMethod("purgeBeanClasses");
            ctClass.addMethod(CtNewMethod.make("public void " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader) {" +
                    "   java.beans.Introspector.flushCaches(); " +
                    "   purgeBeanClasses(classLoader); " +
                    "}", ctClass));
            return true;
        } catch (NotFoundException | CannotCompileException e) {
            // purgeBeanClasses method not found -do nothing
        }
        return false;

    }

    private static boolean checkApacheEL(CtClass ctClass)
    {
        try {
            ctClass.getField("cache");
            // Apache BeanELResolver (has cache property)
            ctClass.addField(new CtField(CtClass.booleanType, "__purgeRequested", ctClass), CtField.Initializer.constant(false));

            ctClass.addMethod(CtNewMethod.make("public void " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader) {" +
                    "   __purgeRequested=true;" +
                    "}", ctClass));
            CtMethod mGetBeanProperty = ctClass.getDeclaredMethod("property");
            mGetBeanProperty.insertBefore(
                "   if(__purgeRequested) {" +
                "       __purgeRequested=false;" +
                "       java.beans.Introspector.flushCaches(); " +
                "       this.cache = new javax.el.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                "   }");
            return true;
        } catch(NotFoundException e1) {
        } catch (CannotCompileException e2) {
        }
        return false;
    }

    private static boolean checkJBoss_3_0_EL(CtClass ctClass) {

        // JBoss EL Resolver - is recognized by "javax.el.BeanELResolver.properties" property
        try {
            CtField field = ctClass.getField("properties");
            if ((field.getModifiers() & Modifier.STATIC) != 0) {
                field.setModifiers(Modifier.STATIC);
                patchJBossEl(ctClass);
            }
            return true;
        } catch (NotFoundException e1) {
            // do nothing
        }
        return false;

    }

    /*
     * JBossEL has weak reference cache. Values are stored in ThreadGroupContext cache, that must be flushed from appropriate thread.
     * Therefore we must create request for cleanup cache in PURGE_CLASS_CACHE_METHOD and own cleanup is executed indirectly when
     * application calls getBeanProperty(...).
     */
    private static void patchJBossEl(CtClass ctClass) {
        try {
            ctClass.addField(new CtField(CtClass.booleanType, "__purgeRequested", ctClass), CtField.Initializer.constant(false));

            ctClass.addMethod(CtNewMethod.make("public void " + PURGE_CLASS_CACHE_METHOD_NAME + "(java.lang.ClassLoader classLoader) {" +
                    "   __purgeRequested=true;" +
                    "}", ctClass));
            try {
                CtMethod mGetBeanProperty = ctClass.getDeclaredMethod("getBeanProperty");
                mGetBeanProperty.insertBefore(
                    "   if(__purgeRequested) {" +
                    "       __purgeRequested=false;" +
                    "       java.beans.Introspector.flushCaches(); " +
                    "       java.lang.reflect.Method meth = javax.el.BeanELResolver.SoftConcurrentHashMap.class.getDeclaredMethod(\"__createNewInstance\", null);" +
                    "       properties = (javax.el.BeanELResolver.SoftConcurrentHashMap) meth.invoke(properties, null);" +
                    "   }");
            } catch (NotFoundException e) {
                LOGGER.debug("FIXME : checkJBoss_3_0_EL() 'getBeanProperty(...)' not found in javax.el.BeanELResolver.");
            }

        } catch (CannotCompileException e) {
            LOGGER.error("patchJBossEl() exception {}", e.getMessage());
        }
    }

    @OnClassLoadEvent(classNameRegexp = "javax.el.BeanELResolver\\$SoftConcurrentHashMap")
    public static void patchJbossElSoftConcurrentHashMap(CtClass ctClass) throws CannotCompileException {
        try {
            ctClass.addMethod(CtNewMethod.make("public javax.el.BeanELResolver.SoftConcurrentHashMap __createNewInstance() {" +
                    "   return new javax.el.BeanELResolver.SoftConcurrentHashMap();" +
                    "}", ctClass));
        } catch (CannotCompileException e) {
            LOGGER.error("patchJbossElSoftConcurrentHashMap() exception {}", e.getMessage());
        }
    }

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
        LOGGER.debug("ELResolverPlugin - BeanELResolver registered : " + beanELResolver.getClass().getName());
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache(ClassLoader appClassLoader) throws Exception {
        PurgeBeanELResolverCacheCommand cmd = new PurgeBeanELResolverCacheCommand(appClassLoader, registeredBeanELResolvers);
        scheduler.scheduleCommand(cmd);
    }

}