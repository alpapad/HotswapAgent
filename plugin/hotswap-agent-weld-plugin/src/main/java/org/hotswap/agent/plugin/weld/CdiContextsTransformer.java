package org.hotswap.agent.plugin.weld;

import java.lang.reflect.Modifier;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.beans.HotSwappingContext;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;

public class CdiContextsTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(CdiContextsTransformer.class);

	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.manager.BeanManagerImpl")
	public static void transformBeanManagerImpl(CtClass clazz, ClassPool classPool) throws NotFoundException {
		CtField f = clazz.getField("contexts");
		f.setModifiers(Modifier.PUBLIC);
	}

	@OnClassLoadEvent(classNameRegexp = "(org.jboss.weld.context.AbstractManagedContext)|(org.jboss.weld.context.AbstractSharedContext)|(org.jboss.weld.util.ForwardingContext)|(org.apache.myfaces.flow.cdi.FlowScopedContextImpl)|(org.apache.myfaces.cdi.view.ViewScopeContextImpl)")
	public static void transformWeldContexts(CtClass clazz, ClassPool classPool, ClassLoader cl) throws NotFoundException, CannotCompileException {
        
	    LOGGER.debug("Adding interface {} to {}.", HotSwappingContext.class.getName(), clazz.getName());
	    clazz.addInterface(classPool.get(HotSwappingContext.class.getName()));

	    
		clazz.addField(CtField.make("public java.util.Set _toRelaod = new java.util.HashSet();", clazz));

		
		CtMethod addBean = CtMethod.make("public void addBean(javax.enterprise.context.spi.Contextual bean) {_toRelaod.add(bean);}", clazz);
		clazz.addMethod(addBean);
		
	    CtMethod getBeans = CtMethod.make("public java.util.Set getBeans(){return _toRelaod;}", clazz);
	    clazz.addMethod(getBeans);
	
		CtMethod _reload = CtMethod.make("public void _reload() {" + ContextualReloadHelper.class.getName() +".reload(this);}", clazz);
		clazz.addMethod(_reload);
		

		CtMethod _isActive = clazz.getDeclaredMethod("isActive");
		_isActive.setName("_isActive");

		CtMethod isActive = CtMethod.make("public boolean isActive() {  boolean active = _isActive(); if(active){ _reload();} return active;}", clazz);

		clazz.addMethod(isActive);

		//addDestroyMethod(clazz, classPool);
		
		LOGGER.debug("Class '{}' patched with hot-swapping support", clazz.getName() );
	}
	
	
	static void addDestroyMethod(CtClass clazz, ClassPool classPool) {
	    CtMethod destroy = null;
	    try {
	        destroy = clazz.getDeclaredMethod("destroy", new CtClass[] {classPool.get("javax.enterprise.context.spi.Contextual")});
        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	    if(destroy == null) {
	        try {
                clazz.addMethod(CtMethod.make(//
                        "public void destroy(javax.enterprise.context.spi.Contextual c) {\n"+//
                         ContextualReloadHelper.class.getName() +".reinitialize(this, c);\n"+
                        "}\n", clazz));
            } catch (CannotCompileException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
	    }
	}
}
