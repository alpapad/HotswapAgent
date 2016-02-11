package org.hotswap.agent.annotation.handler;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * Watch method handler - handle @OnResourceFileEvent annotation on a method.
 *
 * @author Jiri Bubnik
 */
public class WatchHandler<T extends Annotation> implements PluginHandler<T> {
	private static AgentLogger LOGGER = AgentLogger.getLogger(WatchHandler.class);

	protected PluginManager pluginManager;

	public WatchHandler(PluginManager pluginManager) {
		this.pluginManager = pluginManager;
	}

	@Override
	public boolean initField(PluginAnnotation<T> pluginAnnotation) {
		throw new IllegalAccessError("@OnResourceFileEvent annotation not allowed on fields.");
	}

	@Override
	public boolean initMethod(final PluginAnnotation<T> pluginAnnotation) {
		LOGGER.debug("Init for method " + pluginAnnotation.getMethod());
		
		ClassLoader classLoader = pluginManager.getPluginRegistry().getAppClassLoader(pluginAnnotation.getPlugin());
		PluginConfiguration cfg = pluginManager.getPluginConfiguration(classLoader);
		LOGGER.error("Initializing plugin {},\n{}\n{}", pluginAnnotation.getPlugin(), cfg, classLoader);
		try {
			registerResources(pluginAnnotation, classLoader);
		} catch (IOException e) {
			LOGGER.error("Unable to register resources for annotation {} on method {} class {}", e,
					pluginAnnotation.getAnnotation(), pluginAnnotation.getMethod().getName(),
					pluginAnnotation.getMethod().getDeclaringClass().getName());
			return false;
		}

		return true;
	}

	/**
	 * Register resource change listener on URI: - classpath (already should
	 * contain extraClasspath) - plugin configuration - watchResources property
	 */
	private void registerResources(final PluginAnnotation<T> pluginAnnotation, final ClassLoader classLoader) throws IOException {
		final T annot = pluginAnnotation.getAnnotation();
		WatchEventDTO watchEventDTO = WatchEventDTO.parse(annot);

		String path = watchEventDTO.getPath();

		// normalize
		if (path == null || path.equals(".") || path.equals("/")) {
			path = "";
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 2);
		}

		// classpath resources (already should contain extraClasspath)
		Enumeration<URL> en = classLoader.getResources(path);
		if(!en.hasMoreElements()) {
			LOGGER.debug("Can not register any resource on path {} for {} cl:{}", path, pluginAnnotation, classLoader);
		}else {
			
		}
		
		while (en.hasMoreElements()) {
			try {
				URI uri = en.nextElement().toURI();
				LOGGER.trace("Trying {}, {}, {}", uri, classLoader, pluginAnnotation);
				// check that this is a local accessible file (not vfs inside
				// JAR etc.)
				try {
					File f = new File(uri);
					//FIXME: hard-coded to exclude wildfly modules
					if(f.getAbsolutePath().contains("/modules/system/layers/")) {
						LOGGER.debug("Path is module...:{}", f.getAbsolutePath());
						continue;
					}
					LOGGER.debug("Found path:{}", f.getAbsolutePath());
				} catch (Exception e) {
					LOGGER.trace("Skipping uri {}, not a local file.", uri);
					continue;
				}

				LOGGER.info("Registering resource listener on classpath URI {}", uri);
				registerResourceListener(pluginAnnotation, watchEventDTO, classLoader, uri);
			} catch (URISyntaxException e) {
				LOGGER.error("Unable convert root resource path URL to URI", e);
			}
		}

		// add extra directories for watchResources property
		if (!watchEventDTO.isClassFileEvent()) {
			URL[] urls = pluginManager.getPluginConfiguration(classLoader).getWatchResources();
			if(urls.length == 0) {
				LOGGER.debug("Can not register any watchResources for {}", pluginAnnotation);
			}
			
			for (URL url : urls) {
				try {
					Path watchResourcePath = Paths.get(url.toURI());
					Path pathInWatchResource = watchResourcePath.resolve(path);
					if (pathInWatchResource.toFile().exists()) {
						LOGGER.debug("Registering resource listener on watchResources URI {}",	pathInWatchResource.toUri());
						registerResourceListener(pluginAnnotation, watchEventDTO, classLoader,	pathInWatchResource.toUri());
					}
				} catch (URISyntaxException e) {
					LOGGER.error("Unable convert watch resource path URL {} to URI", e, url);
				}
			}
		}
	}

	/**
	 * Using pluginManager.registerResourceListener() add new listener on URI.
	 * <p/>
	 * There might be several same events for a resource change (either from
	 * filesystem or when IDE clears and reloads a class multiple time on
	 * rebuild). Use command scheduler to group same events into single
	 * invocation.
	 */
	private void registerResourceListener(final PluginAnnotation<T> pluginAnnotation, final WatchEventDTO watchEventDTO,final ClassLoader classLoader, URI uri) throws IOException {
		pluginManager.getWatcher().addEventListener(classLoader, uri, new WatchEventListener() {
			@Override
			public void onEvent(WatchFileEvent event) {
				if (watchEventDTO.accept(event)) {
					Command command = new WatchEventCommand<T>(pluginAnnotation, event, classLoader);
					pluginManager.getScheduler().scheduleCommand(command, watchEventDTO.getTimeout());
					LOGGER.trace("Resource changed {} {}", event, classLoader);
				}
			}
		});
	}

}
