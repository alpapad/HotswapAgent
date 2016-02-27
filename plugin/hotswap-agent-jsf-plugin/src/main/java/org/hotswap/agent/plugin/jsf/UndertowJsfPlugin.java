package org.hotswap.agent.plugin.jsf;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Set jsf project to development stage, enabling facelets refresh
 * 
 * @author alpapad
 */
@Plugin(name = "JbossfacesPlugin", //
		description = "Set jsf project to development stage, enabling facelets refresh", //
		testedVersions = { "2.2.9" }, //
		expectedVersions = { "2.2" })
public class JbossfacesPlugin {

	private static AgentLogger LOGGER = AgentLogger.getLogger(FacesServletPlugin.class);

	// io.undertow.servlet.api.DeploymentInfo
	// public DeploymentInfo addInitParameter(final String name, final String
	// value)
	//
	// io.undertow.servlet.spec.ServletContextImpl
	// public More ...ServletContextImpl(final
	// io.undertow.servlet.api.ServletContainer servletContainer, final
	// io.undertow.servlet.api.Deployment deployment) {

	// org.apache.myfaces.ee6.MyFacesContainerInitializer
	@OnClassLoadEvent(classNameRegexp = "io.undertow.servlet.spec.ServletContextImpl")
	public static void patchMyFacesContainerInitializer(ClassPool classPool, CtClass ctClass) {
		// public void onStartup(Set<Class<?>> clazzes, ServletContext
		// servletContext) throws ServletException
		try {
			CtConstructor ct = ctClass
					.getDeclaredConstructor(new CtClass[] { classPool.get("io.undertow.servlet.api.ServletContainer"),
							classPool.get("io.undertow.servlet.api.Deployment") });
			ct.insertBefore("{" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"javax.faces.FACELETS_REFRESH_PERIOD\",\"1\");" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"facelets.REFRESH_PERIOD\",\"1\");" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"javax.faces.PROJECT_STAGE\",\"Development\");" + //
					"}");

			LOGGER.info("Patched io.undertow.servlet.spec.ServletContextImpl");
		} catch (NotFoundException | CannotCompileException e) {
			LOGGER.error("Error patching io.undertow.servlet.spec.ServletContextImpl", e);
		}
	}
}
