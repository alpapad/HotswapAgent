package org.hotswap.agent.plugin.weld;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Hook into WeldBeanDeploymentArchive or BeanDeploymentArchiveImpl(WildFly)
 * constructors to initialize WeldPlugin
 *
 * @author Vladimir Dvorak
 */
public class BeanDeploymentArchiveTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveTransformer.class);

	/**
	 * Basic WeldBeanDeploymentArchive transformation.
	 *
	 * @param clazz
	 * @param classPool
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive")
	public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
		CtClass[] constructorParams = new CtClass[] { classPool.get("java.lang.String"),
				classPool.get("java.util.Collection"), classPool.get("org.jboss.weld.bootstrap.spi.BeansXml"),
				classPool.get("java.util.Set") };

		StringBuilder src = new StringBuilder("{");
		src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class));
		src.append(PluginManagerInvoker.buildCallPluginMethod(WeldPlugin.class, "init"));
		src.append(
				"org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent.registerArchive(getClass().getClassLoader(), this, this.getId());");
		src.append("}");

		CtConstructor declaredConstructor = clazz.getDeclaredConstructor(constructorParams);
		declaredConstructor.insertAfter(src.toString());

		LOGGER.debug(
				"Class 'org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive' patched with BDA registration.");
	}

	/**
	 * Should be moved to a separate module just for wildfly. Note that cdi 1.1+
	 * can scan an archive and consider it as a cdi deployment even without a
	 * beans.xml (implicit)
	 * 
	 * Jboss BeanDeploymentArchiveImpl transformation.
	 *
	 * @param clazz
	 * @param classPool
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl")
	public static void transformJbossBda(CtClass clazz, ClassPool classPool)
			throws NotFoundException, CannotCompileException {
		StringBuilder src = new StringBuilder("{");
		src.append(
				"if (beansXml!=null&& beanArchiveType!=null && (\"EXPLICIT\".equals(beanArchiveType.toString()) || \"IMPLICIT\".equals(beanArchiveType.toString()))){");
		src.append("  String beansXmlPath = beansXml.getUrl().getPath();");
		src.append("  String archPath = null;");
		src.append("  if(beansXmlPath.endsWith(\"META-INF/beans.xml\")) {");
		src.append(
				"    archPath = beansXmlPath.substring(0, beansXmlPath.length()-\"META-INF/beans.xml\".length()-1);"); /*
																														 * -1
																														 * ~
																														 * eat
																														 * "/"
																														 * at
																														 * the
																														 * end
																														 * of
																														 * path
																														 */
		src.append("  } else if (beansXmlPath.endsWith(\"WEB-INF/beans.xml\")) {");
		src.append(
				"    archPath = beansXmlPath.substring(0, beansXmlPath.length()-\"beans.xml\".length()) + \"classes\";");
		src.append("  }");
		// src.append(" if(archPath != null) {");
		src.append(PluginManagerInvoker.buildInitializePlugin(WeldPlugin.class, "module.getClassLoader()"));
		src.append(PluginManagerInvoker.buildCallPluginMethod("module.getClassLoader()", WeldPlugin.class,
				"initInJBossAS"));
		src.append(
				"    Class agC = Class.forName(\"org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent\", true, module.getClassLoader());");
		src.append(
				"    java.lang.reflect.Method agM  = agC.getDeclaredMethod(\"registerArchive\", new Class[] {java.lang.ClassLoader.class, org.jboss.weld.bootstrap.spi.BeanDeploymentArchive.class, java.lang.String.class});");
		src.append("    agM.invoke(null, new Object[] { module.getClassLoader(),this, beanArchiveType.toString()});");
		// src.append(" }");
		src.append("}}");

		for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
			constructor.insertAfter(src.toString());
		}

		LOGGER.debug("Class 'org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl' patched with BDA registration.");
	}

	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.manager.BeanManagerImpl")
	public static void transformBeanManagerImpl(CtClass clazz, ClassPool classPool) throws NotFoundException {
		CtField f = clazz.getField("contexts");
		f.setModifiers(Modifier.PUBLIC);
		
	}
	//org.jboss.weld.context.AbstractConversationContext
	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.context.AbstractBoundContext")
	public static void transformAbstractConversationContext(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
		//    public void activate(String cid) {
		//javax.enterprise.context.spi.Contextual
		
		clazz.addField(CtField.make("public java.util.List toRedefine = new java.util.ArrayList();", clazz));
		//CtMethod activate = clazz.getDeclaredMethod("activate", new CtClass[] {classPool.get("java.lang.String")});
		
		//activate.insertAfter("{System.err.println(\"activate called on org.jboss.weld.context.AbstractConversationContext:\" + this);}");
		
		//protected void initialize(String cid) 
//		List a = new ArrayList();
//		a.iterator().next()
		CtMethod initialize = clazz.getDeclaredMethod("activate");
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("java.util.Iterator it = toRedefine.iterator();\n");
		sb.append("while(it.hasNext()){\n");

		sb.append(" org.jboss.weld.bean.ManagedBean c = org.jboss.weld.bean.ManagedBean.class.cast(it.next());\n");
		sb.append(" System.err.println(\"Reloading........\" + c + \", :\" + this);\n");
		sb.append(" try{c.getProducer().inject(c.getProducer().produce(c.getBeanManager().createCreationalContext(c)), c.getBeanManager().createCreationalContext(c));} catch(java.lang.Exception e) { e.printStackTrace();}\n");
		sb.append(" try{this.destroy((javax.enterprise.context.spi.Contextual)c);} catch(java.lang.Exception e) { e.printStackTrace();}\n");		
//		sb.append(" it.remove(); ");	
//		sb.append("for(Object o: toRedefine) {");
//		sb.append(" this.destroy(javax.enterprise.context.spi.Contextual.class.cast(o)); ");
		sb.append("}\n");
		//sb.append("toRedefine.clear(); ");
		sb.append("{System.err.println(\"initialize called on org.jboss.weld.context.AbstractBoundContext:\" + this);}\n");
		sb.append(" }\n");
		initialize.insertAfter(sb.toString());
		
		LOGGER.debug("Class 'org.jboss.weld.context.AbstractBoundContext' patched with BDA registration.");
	}
}
