package org.hotswap.agent.plugin.jsf;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Set jsf project to development stage, enabling facelets refresh
 * 
 * @author alpapad
 */
@Plugin(name = "MyfacesPlugin", //
		description = "Set jsf project to development stage, enabling facelets refresh", //
		testedVersions = { "2.2.9" }, //
		expectedVersions = { "2.2" })
public class MyfacesPlugin {

	private static AgentLogger LOGGER = AgentLogger.getLogger(FacesServletPlugin.class);


	@OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.ee6.MyFacesContainerInitializer")
	public static void patchMyFacesContainerInitializer(ClassPool classPool, CtClass ctClass) {
		try {
			ctClass.getDeclaredMethod("onStartup",	new CtClass[] {classPool.get("java.util.Set"), classPool.get("javax.servlet.ServletContext") })//
					.insertBefore(ApplyFacesDevParams.class.getName() + ".apply(servletContext);");
			LOGGER.info("Patched org.apache.myfaces.ee6.MyFacesContainerInitializer");
		} catch (NotFoundException | CannotCompileException e) {
			LOGGER.error("Error patching org.apache.myfaces.webapp.StartupServletContextListener", e);
		}
	}
	
	
	@OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.webapp.StartupServletContextListener")
	public static void startupServletContextListener(ClassPool classPool, CtClass ctClass) {
		try {
			ctClass.getDeclaredMethod("contextInitialized",	new CtClass[] { classPool.get("javax.servlet.ServletContextEvent") })//
					.insertBefore(ApplyFacesDevParams.class.getName() + ".apply(event.getServletContext());");
			LOGGER.info("Patched org.apache.myfaces.webapp.StartupServletContextListener");
		} catch (NotFoundException | CannotCompileException e) {
			LOGGER.error("Error patching org.apache.myfaces.webapp.StartupServletContextListener", e);
		}
	}
}
