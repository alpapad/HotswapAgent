package org.hotswap.agent.plugin.jsf;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
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
@Plugin(name = "UndertowJsf", //
		description = "Set jsf project to development stage, enabling facelets refresh", //
		testedVersions = { "?" }, //
		expectedVersions = { "?" })
public class UndertowJsfPlugin {

	private static AgentLogger LOGGER = AgentLogger.getLogger(FacesServletPlugin.class);


	@OnClassLoadEvent(classNameRegexp = "io.undertow.servlet.spec.ServletContextImpl")
	public static void patchMyFacesContainerInitializer(ClassPool classPool, CtClass ctClass) {
		try {
			CtConstructor ct = ctClass
					.getDeclaredConstructor(new CtClass[] { classPool.get("io.undertow.servlet.api.ServletContainer"),
							classPool.get("io.undertow.servlet.api.Deployment") });
			ct.insertBefore("{" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"javax.faces.FACELETS_REFRESH_PERIOD\",\"1\");" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"org.apache.myfaces.CONFIG_REFRESH_PERIOD\",\"1\");" +//
					" 			deployment.getDeploymentInfo().addInitParameter(\"facelets.REFRESH_PERIOD\",\"1\");" + //
					" 			deployment.getDeploymentInfo().addInitParameter(\"javax.faces.PROJECT_STAGE\",\"Development\");" + //
					"}");

			LOGGER.info("Patched io.undertow.servlet.spec.ServletContextImpl");
		} catch (NotFoundException | CannotCompileException e) {
			LOGGER.error("Error patching io.undertow.servlet.spec.ServletContextImpl", e);
		}
	}
	
	@Init
	public void initializeInstance(PluginConfiguration pluginConfiguration) {
		LOGGER.info("UndertowJsf  plugin Initializing");
	}	
}
