package org.hotswap.agent.util.classloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * Special URL classloader to get only changed resources from URL.
 *
 * Use this classloader to support watchResources property.
 *
 * This classloader checks if the resource was modified after application
 * startup and in that case delegates getResource()/getResources() to custom URL
 * classloader. Otherwise returns null or resource from paren classloader
 * (depending on searchParent property).
 *
 * @author Jiri Bubnik
 */
public class WatchResourcesClassLoaderExt {
	private static AgentLogger LOGGER = AgentLogger.getLogger(WatchResourcesClassLoaderExt.class);

	/**
	 * URLs of changed resources. Use this set to check if the resource was
	 * changed and hence should be returned by this classloader.
	 */
	Set<URL> changedUrls = new HashSet<URL>();

	Set<URL> extraUrls = new HashSet<URL>();
	

	private final Object lock = new Object();

	public WatchResourcesClassLoaderExt() {
	}

	/**
	 * Configure new instance with urls and watcher service.
	 *
	 * @param extraPath
	 *            the URLs from which to load resources
	 */
	public void initExtraPath(URL[] extraPath) {
		for (URL url : extraPath) {
			extraUrls.add(url);
		}
	}

	/**
	 * Configure new instance with urls and watcher service.
	 *
	 * @param watchResources
	 *            the URLs from which to load resources
	 * @param watcher
	 *            watcher service to register watch events
	 */
	public void initWatchResources(ClassLoader classLoader, URL[] watchResources, Watcher watcher) {
		// create classloader to serve resources only from watchResources URL's

		final HotswapAgentClassLoaderExt ext = HotswapAgentClassLoaderExt.class.cast(classLoader);
		ext.setExtraClassPath(extraUrls.toArray(new URL[]{}));
		
		LOGGER.warning("Watching...:" + watchResources);
		// register watch resources - on change event each modified resource
		// will be added to changedUrls.
		for (URL resource : watchResources) {
			try {
				URI uri = resource.toURI();
				LOGGER.error("Watching directory '{}' for changes.", uri);
				watcher.addEventListener(classLoader, uri, new WatchEventListener() {
					@Override
					public void onEvent(WatchFileEvent event) {
						try {
							LOGGER.warning("File change:" + event);
							if(event.isFile()) {
								Path path = Paths.get("path/to/file");
								try {
									@SuppressWarnings("unused")
									byte[] data = Files.readAllBytes(path);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
							}
							if (event.isFile() || event.isDirectory()) {
								String p = event.getURI().toURL().getPath();
								p = p.substring(0, p.lastIndexOf('/') +1);
								changedUrls.add(new URI("file:/" +p).toURL());
								LOGGER.warning("File '{}' changed and will be returned instead of original classloader equivalent.", p);
								synchronized(lock){
									LinkedHashSet<URL> uris = new LinkedHashSet<>();
									uris.addAll(changedUrls);
									uris.addAll(extraUrls);
									ext.setExtraClassPath(uris.toArray(new URL[]{}));
								}
							}
						} catch (MalformedURLException|URISyntaxException e) {
							LOGGER.error("Unexpected - cannot convert URI {} to URL.", e, event.getURI());
						} 
					}
				});
			} catch (URISyntaxException e) {
				LOGGER.warning("Unable to convert watchResources URL '{}' to URI. URL is skipped.", e, resource);
			}
		}
	}

	/**
	 * Check if the resource was changed after this classloader instantiaton.
	 *
	 * @param url
	 *            full URL of the file
	 * @return true if was changed after instantiation
	 */
	public boolean isResourceChanged(URL url) {
		return changedUrls.contains(url);
	}
}
