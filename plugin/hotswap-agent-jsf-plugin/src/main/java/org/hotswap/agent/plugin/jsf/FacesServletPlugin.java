package org.hotswap.agent.plugin.jsf;

import static org.hotswap.agent.annotation.FileEvent.CREATE;
import static org.hotswap.agent.annotation.FileEvent.MODIFY;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.command.Scheduler.DuplicateSheduleBehaviour;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * JSF 2.2 Copy faces files to web-app exploaded directory in server
 * 
 * @author alpapad
 */
@Plugin(name = "FacesServletPlugin",  //
		description = "JSF 2.2 Copy faces files to web-app exploaded directory in server", //
		testedVersions = {"2.2" }, //
		expectedVersions = { "2.2" })
public class FacesServletPlugin {
	private static AgentLogger LOGGER = AgentLogger.getLogger(FacesServletPlugin.class);

	private String realPath;

	private Set<String> pending = new LinkedHashSet<>();

	@Init
	Scheduler scheduler;

	@Init
	ClassLoader appClassLoader;

	@Init
	PluginConfiguration pluginConfiguration;

	@OnClassLoadEvent(classNameRegexp = "javax.faces.webapp.FacesServlet")
	public static void patchFacesServlet(ClassPool classPool, CtClass ctClass) {
		try {
			ctClass.getDeclaredMethod("init", new CtClass[] { classPool.get("javax.servlet.ServletConfig") })
					.insertAfter(//
							"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"//
							+ PluginManagerInvoker.buildInitializePlugin(FacesServletPlugin.class, "$$cl")//
							+ "java.lang.String $$RealPath = servletConfig.getServletContext().getRealPath(\"/\");"//
							+ PluginManagerInvoker.buildCallPluginMethod("$$cl", FacesServletPlugin.class, "setRealPath", "$$RealPath", "java.lang.String")//
							+ ApplyFacesDevParams.class.getName()+ ".apply(servletConfig.getServletContext());"//
					);
			
			LOGGER.info("FacesServletPlugin, patched javax.faces.webapp.FacesServlet");

		} catch(NotFoundException | CannotCompileException e){
			LOGGER.error("Error patching FacesServlet",e);
		}
	}

	@Init
	public void initializeInstance(PluginConfiguration pluginConfiguration) {
		LOGGER.info("FacesServletPlugin 2.2 plugin Initializing INSTANCE at classLoader {}, pluginConfiguration: {}", appClassLoader, pluginConfiguration);
	}

	public void setRealPath(String realPath) {
		this.realPath = realPath;
		LOGGER.info("FacesServletPlugin web-app path: {} using classloader {}", this.realPath, appClassLoader);
	}

	private final Command command = new Command() {

		@Override
		public void executeCommand() {
			LOGGER.trace("RUNCOMMAND");
			
			List<String> files = new ArrayList<>();
			while(true) {
				
				// Check pending files
				synchronized (pending) {
					LOGGER.debug("Modified Files:{}", pending);
					files.clear();
					files.addAll(pending);
					pending.clear();
					
					// exit if no files pending...
					if(files.size() == 0) {
						return;
					}
				}
				
				for (String p : files) {
					if(LOGGER.isLevelEnabled(Level.DEBUG)) {
						LOGGER.debug("Copying file {}, {}, {} ", p, Arrays.toString(pluginConfiguration.getWatchResources()), Arrays.toString(pluginConfiguration.getExtraClasspath()));
					}
					
					File f = new File(p);
					for (URL u : pluginConfiguration.getWatchResources()) {
						LOGGER.trace("Trying path: {}", u);
						try {
							File d = new File(u.getFile());
							if (f.getAbsolutePath().startsWith(d.getAbsolutePath())) {
								String x = f.getAbsolutePath().replace(d.getAbsolutePath(), "");
								x = x.replace('\\','/').replace("//", "/");
	
								File toCopy = new File(realPath, x);
								LOGGER.debug("Copying file {} to {} (isFile:{})", f.toPath(), toCopy.getAbsolutePath(), toCopy.isFile());
								if(!toCopy.isFile()) {
									continue;
								}
								try {
									IOUtils.copy(f, toCopy);
								} catch (Exception e) {
									LOGGER.error("Error copying file {} to {} ",e, p, d + x);
								}
							} else {
								LOGGER.trace("Not a match for  copying file {} to {} ", p, d);
							}
						} catch (Exception e) {
							LOGGER.error("Error copying file {} to {} ",e, p, u);
						}
					}
				}
			}
		}
	};

	@OnResourceFileEvent(path = "/", filter = ".*", events= {CREATE, MODIFY})
	public void refreshJsfResourceBundles(URL fileUrl, FileEvent evt, ClassLoader appClassLoader) {
		if (FileEvent.DELETE.equals(evt) || fileUrl.getFile().endsWith(".class")) {
			return;
		}
		synchronized (pending) {
			pending.add(fileUrl.getFile());
		}
		scheduler.scheduleCommand(command, 250, DuplicateSheduleBehaviour.SKIP);
	}
}
