package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

public class BeanDeployerTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeployerTransformer.class);

	/**
	 * Basic WeldBeanDeploymentArchive transformation.
	 *
	 * @param clazz
	 * @param classPool
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bootstrap.AbstractBeanDeployer")
	public static void transform(CtClass ctClass, ClassPool classPool)
			throws NotFoundException, CannotCompileException {
		/*
		 * CtClass[] constructorParams = new CtClass[] {
		 * classPool.get("java.lang.String"), classPool.get("java.lang.Class"),
		 * classPool.get("java.util.Set"), classPool.get("java.lang.String"),
		 * classPool.get("javax.enterprise.inject.spi.Bean"),
		 * classPool.get("boolean") };
		 */
		classPool.importPackage("org.hotswap.agent.plugin.weld.command");
		CtConstructor declaredConstructor = ctClass.getDeclaredConstructors()[0];
		declaredConstructor.insertAfter("org.jboss.weld.bootstrap.BeanDeployerHelper.setDeployer(this);");
		LOGGER.info("AbstractBeanDeployer patched");
	}
	//BeanDeployer
	
	/**
	 * Basic WeldBeanDeploymentArchive transformation.
	 *
	 * @param clazz
	 * @param classPool
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	//@OnClassLoadEvent(classNameRegexp = "org.jboss.weld.bootstrap.BeanDeployer")
	public static void transformBeanDeployer(CtClass ctClass, ClassPool classPool)
			throws NotFoundException, CannotCompileException {
		/*
		 * CtClass[] constructorParams = new CtClass[] {
		 * classPool.get("java.lang.String"), classPool.get("java.lang.Class"),
		 * classPool.get("java.util.Set"), classPool.get("java.lang.String"),
		 * classPool.get("javax.enterprise.inject.spi.Bean"),
		 * classPool.get("boolean") };
		 */
		CtConstructor declaredConstructor = ctClass.getDeclaredConstructors()[0];
		declaredConstructor.insertAfter("org.jboss.weld.bootstrap.BeanDeployerHelper.setDeployer(this);");
		LOGGER.info("AbstractBeanDeployer patched");
	}
}
