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
        LOGGER.debug("Override org.hibernate.cfg.Configuration#buildSessionFactory to create a SessionFactoryProxy proxy.");
        
        clazz.addInterface(classPool.get("org.hotswap.agent.plugin.hibernate3.session.proxy.ReInitializable"));
        
        CtField field = CtField.make("private org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig $$override  = new org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig();", clazz);
        
        clazz.addField(field);
        
        CtMethod oldMethod = clazz.getDeclaredMethod("buildSessionFactory");
        oldMethod.setName("_buildSessionFactory");
        
        CtMethod newMethod = CtNewMethod.make(//
                "public org.hibernate.SessionFactory buildSessionFactory() throws org.hibernate.HibernateException {" + //
                        "  return " + SessionFactoryProxy.class.getName() +//
                        "       .getWrapper(this)" + //
                        "       .proxy(_buildSessionFactory()); " + //
                        "}", clazz);
        clazz.addMethod(newMethod);
        CtMethod reInitMethod = CtNewMethod.make(//
        		"public void reInitialize(){"+//
        		"  this.settingsFactory = new org.hibernate.cfg.SettingsFactory();" + //
        		"  this.reset();" + //
        		"}", clazz);
        
        clazz.addMethod(reInitMethod);

        CtMethod internalPropsMethod = CtNewMethod.make(//
        		"public org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig getOverrideConfig(){"+//
        		"  return $$override;" + //
        		"}", clazz);
        
        clazz.addMethod(internalPropsMethod);
        
        CtConstructor con = clazz.getDeclaredConstructor(new CtClass[]{classPool.getCtClass("org.hibernate.cfg.SettingsFactory")});
        con.getMethodInfo().setAccessFlags(AccessFlag.PUBLIC);
        
        CtMethod[] configures = clazz.getDeclaredMethods("configure");
        if(configures != null) {
        	for(CtMethod c: configures) {
        		c.setName("_configure");
        	}
        }
     
        CtMethod[] setProperties = clazz.getDeclaredMethods("setProperty");
        if(configures != null) {
        	for(CtMethod c: setProperties) {
        		c.setName("_setProperty");
        	}
        }
    }
}
