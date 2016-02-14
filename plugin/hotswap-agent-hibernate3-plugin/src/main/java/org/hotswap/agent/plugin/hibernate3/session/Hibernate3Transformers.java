package org.hotswap.agent.plugin.hibernate3.session;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.bytecode.AccessFlag;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.session.proxy.SessionFactoryProxy;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for Hibernate plugin.
 */
public class Hibernate3Transformers {
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3Transformers.class);


    /**
     * Remove final flag from SessionFactoryImpl - we need to create a proxy on session factory and cannot
     * use SessionFactory interface, because hibernate makes type cast to impl.
     */
    @OnClassLoadEvent(classNameRegexp = "org.hibernate.impl.SessionFactoryImpl")
    public static void removeSessionFactoryImplFinalFlag(CtClass clazz) throws Exception {
    	int flags = clazz.getClassFile().getAccessFlags();
    	flags = AccessFlag.setPublic(flags);
    	flags = AccessFlag.clear(flags, AccessFlag.FINAL);
        clazz.getClassFile().setAccessFlags(flags);
        LOGGER.debug("Override org.hibernate.impl.SessionFactoryImpl.");
    }

    @OnClassLoadEvent(classNameRegexp = "org.hibernate.cfg.Configuration")
    public static void proxySessionFactory(ClassLoader classLoader, ClassPool classPool, CtClass clazz) throws Exception {
    	
    	LOGGER.debug("Adding interface o.h.a.p.h.s.p.ReInitializable to org.hibernate.cfg.Configuration.");
        
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.hibernate3.session.proxy.ReInitializable"));
        
        CtField field = CtField.make("private org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig $$override  = new org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig();", clazz);
        
        clazz.addField(field);
        
    	LOGGER.debug("Patching org.hibernate.cfg.Configuration#buildSessionFactory to create a SessionFactoryProxy proxy.");

        CtMethod oldMethod = clazz.getDeclaredMethod("buildSessionFactory");
        oldMethod.setName("_buildSessionFactory");
        
        CtMethod newMethod = CtNewMethod.make(//
                "public org.hibernate.SessionFactory buildSessionFactory() throws org.hibernate.HibernateException {" + //
                        "  return " + SessionFactoryProxy.class.getName() +//
                        "       .getWrapper(this)" + //
                        "       .proxy(_buildSessionFactory()); " + //
                        "}", clazz);
        clazz.addMethod(newMethod);
        
        LOGGER.debug("Adding org.hibernate.cfg.Configuration.reInitialize() method");
        CtMethod reInitMethod = CtNewMethod.make(//
        		"public void reInitialize(){"+//
        		"  this.settingsFactory = new org.hibernate.cfg.SettingsFactory();" + //
        		"  this.reset();" + //
        		"}", clazz);
        
        clazz.addMethod(reInitMethod);

        LOGGER.debug("Adding org.hibernate.cfg.Configuration.getOverrideConfig() method");
        CtMethod internalPropsMethod = CtNewMethod.make(//
        		"public org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig getOverrideConfig(){"+//
        		"  return $$override;" + //
        		"}", clazz);
        
        clazz.addMethod(internalPropsMethod);
        
        //new CtClass[]{classPool.getCtClass("org.hibernate.cfg.SettingsFactory")}
        CtConstructor con = clazz.getDeclaredConstructor(new CtClass[]{});
        
        LOGGER.debug("Patching org.hibernate.cfg.Configuration.<init>");
        con.insertAfter(//
				"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"//
				+ PluginManagerInvoker.buildInitializePlugin(Hibernate3Plugin.class, "$$cl")//
				+ "java.lang.String $$version = org.hibernate.Version.getVersionString();" //
				+ PluginManagerInvoker.buildCallPluginMethod("$$cl", Hibernate3Plugin.class, "setVersion", "$$version", "java.lang.String")//
        );
        
        LOGGER.debug("Renaming org.hibernate.cfg.Configuration.configure methods");
        CtMethod[] configures = clazz.getDeclaredMethods("configure");
        if(configures != null) {
        	for(CtMethod c: configures) {
        		c.setName("_configure");
        	}
        }
        
        LOGGER.debug("Renaming org.hibernate.cfg.Configuration.setProperty method");
        CtMethod[] setProperties = clazz.getDeclaredMethods("setProperty");
        if(configures != null) {
        	for(CtMethod c: setProperties) {
        		c.setName("_setProperty");
        	}
        }

        LOGGER.info("Hibernate3Plugin, patched org.hibernate.cfg.Configuration");
    }
}
