package org.hotswap.agent.plugin.jsf;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "FacesServletPlugin", description = "JSF 2.2 Copy .xml, .properties and .xhtml files", testedVersions = {
		"2.2" }, expectedVersions = { "2.2" })
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
	public static void facesConfigManagerInitialized(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
		LOGGER.error("FacesServletPlugin, patching javax.faces.webapp.FacesServlet");
		ctClass.getDeclaredMethod("init", new CtClass[] { classPool.get("javax.servlet.ServletConfig") })
				.insertAfter(
						"java.lang.ClassLoader $$cl = Thread.currentThread().getContextClassLoader();"
						+ PluginManagerInvoker.buildInitializePlugin(FacesServletPlugin.class,"$$cl")
						+ "java.lang.String $$RealPath = servletConfig.getServletContext().getRealPath(\"/\");"
						+ PluginManagerInvoker.buildCallPluginMethod("$$cl",FacesServletPlugin.class, "setRealPath", "$$RealPath", "java.lang.String"));
	}

	@Init
	public void initializeInstance(PluginConfiguration pluginConfiguration){
		LOGGER.error("FacesServletPlugin 2.2 plugin Initialized INSTANCE at classLoader {}", appClassLoader);

//		for(Map.Entry<URL,Properties> e: pluginConfiguration.getConfigurations().entrySet()) {
//			LOGGER.error(" Base path:{}", e.getKey());
//			LOGGER.error("      Type:{}", e.getValue().getProperty("packaging"));
//			LOGGER.error("Class Path:{}", e.getValue().getProperty("extraClasspath"));
//		}
	}
	
	public void setRealPath(String realPath) {
		this.realPath = realPath;
		LOGGER.error("FacesServletPlugin REALPATH: {}, {}, {}", this.realPath, appClassLoader, pluginConfiguration);
	}

	
	private final MergeableCommand command = new MergeableCommand(){

		@Override
		public void executeCommand() {
			LOGGER.error("RUNCOMMAND");
			List<String> files = new ArrayList<>();
			synchronized(pending){
				LOGGER.error("MODIFIED FILE:{}", pending.toString());
				files.addAll(pending);
				pending.clear();
			}
			for(String p: files) {
				LOGGER.error("Copying file {}, {}, {} ",p, Arrays.toString(pluginConfiguration.getWatchResources()),  Arrays.toString(pluginConfiguration.getExtraClasspath()));
				File f = new File(p);
				for(URL u : pluginConfiguration.getWatchResources()) {
					LOGGER.error("Trying path: {}", u);
					try{
						File d = new File(u.getFile());
						if(f.getAbsolutePath().startsWith(d.getAbsolutePath())) {
							String x = f.getAbsolutePath().replace(d.getAbsolutePath(), "/");
							if(x.endsWith("//")) {
								x = x.replace("//","/");
							}
							LOGGER.error("Copying file {} to {} ",p,  new File(realPath,x).getAbsolutePath());
							try {
								Files.copy(f.toPath(), new File(realPath,x).toPath(), StandardCopyOption.REPLACE_EXISTING);
							} catch (IOException e) {
								LOGGER.error("Error copying file {} to {} ",p,  d +x);
								e.printStackTrace();
							}
						} else {
							LOGGER.error("Not a match for  copying file {} to {} ",p,  d);
						}
					} catch (Exception e) {
						LOGGER.error("Error copying file {} to {} ",p, u);
						e.printStackTrace();
					}
				}
			}
		}
	};
	
    @OnResourceFileEvent(path = "/", filter = ".*")
    public void refreshJsfResourceBundles(URL fileUrl, FileEvent evt, ClassLoader appClassLoader) {
    	//LOGGER.error("XX File updated: {}, {}, {}", fileUrl, evt, appClassLoader);
    	if(FileEvent.DELETE.equals(evt) || fileUrl.getFile().endsWith(".class")){
    		return;
    	}
    	synchronized(pending){
    		pending.add(fileUrl.getFile());
    	}
        scheduler.scheduleCommand(command, 500);
    }
}
