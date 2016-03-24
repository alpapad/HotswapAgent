package org.hotswap.agent.watch.nio;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * NIO2 watcher implementation for systems which support
 * ExtendedWatchEventModifier.FILE_TREE
 * <p/>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * <p/>
 * By http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 * @author Jiri Bubnik
 */
public abstract class AbstractNIO2Watcher implements Watcher, DynamicMBean {
	protected AgentLogger LOGGER = AgentLogger.getLogger(this.getClass());

	protected final WatchService watcher;
	protected final Map<WatchKey, Path> keys;
	private final Map<Path, List<WatchEventListener>> listeners = new HashMap<Path, List<WatchEventListener>>();

	// keep track about which classloader requested which event
	protected Map<WatchEventListener, ClassLoader> classLoaderListeners = new HashMap<WatchEventListener, ClassLoader>();

	private Thread runner;
	
	private boolean stopped;
	
	private volatile boolean paused = false;

	public AbstractNIO2Watcher() throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	@Override
	public synchronized void addEventListener(ClassLoader classLoader, URI pathPrefix, WatchEventListener listener) {
		File path;
		try {
			// check that it is regular file
			// toString() is weird and solves HiarchicalUriException for URI
			// like "file:./src/resources/file.txt".
			path = new File(pathPrefix);
		} catch (IllegalArgumentException e) {
			if (!LOGGER.isLevelEnabled(Level.TRACE)) {
				LOGGER.warning("Unable to watch for path {}, not a local regular file or directory.", pathPrefix);
			} else {
				LOGGER.trace("Unable to watch for path {} exception", e, pathPrefix);
			}
			return;
		}

		try {
			addDirectory(path.toURI());
		} catch (IOException e) {
			if (!LOGGER.isLevelEnabled(Level.TRACE)) {
				LOGGER.warning("Unable to watch for path {}, not a local regular file or directory.", pathPrefix);
			} else {
				LOGGER.trace("Unable to watch path with prefix '{}' for changes.", e, pathPrefix);
			}
			return;
		}

		List<WatchEventListener> list = listeners.get(Paths.get(pathPrefix));
		if (list == null) {
			list = new ArrayList<WatchEventListener>();
			listeners.put(Paths.get(pathPrefix), list);
		}
		list.add(listener);

		if (classLoader != null) {
			classLoaderListeners.put(listener, classLoader);
		}
	}

	@Override
	public void addEventListener(ClassLoader classLoader, URL pathPrefix, WatchEventListener listener) {
		try {
			addEventListener(classLoader, pathPrefix.toURI(), listener);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Unable to convert URL to URI " + pathPrefix, e);
		}
	}

	/**
	 * Remove all transformers registered with a classloader
	 * 
	 * @param classLoader
	 */
	@Override
	public void closeClassLoader(ClassLoader classLoader) {
		for (Iterator<Map.Entry<WatchEventListener, ClassLoader>> entryIterator = classLoaderListeners.entrySet()
				.iterator(); entryIterator.hasNext();) {
			Map.Entry<WatchEventListener, ClassLoader> entry = entryIterator.next();
			if (entry.getValue().equals(classLoader)) {
				entryIterator.remove();
				for (List<WatchEventListener> transformerList : listeners.values()) {
					transformerList.remove(entry.getKey());
				}
			}
		}
		LOGGER.debug("All watch listeners removed for classLoader {}", classLoader);
	}

	/**
	 * Registers the given directory
	 */
	public void addDirectory(URI path) throws IOException {
		try {
			Path dir = Paths.get(path);

			if (keys.values().contains(dir)) {
				return;
			}
			registerAll(null, dir);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid URI format " + path, e);
		} catch (FileSystemNotFoundException e) {
			throw new IOException("Invalid URI " + path, e);
		} catch (SecurityException e) {
			throw new IOException("Security exception for URI " + path, e);
		}
	}

	protected abstract void registerAll(final Path real, final Path start) throws IOException;

	/**
	 * Process all events for keys queued to the watcher
	 *
	 * @return true if should continue
	 */
	private boolean processEvents() {

		// wait for key to be signalled
		WatchKey key;
		try {
			key = watcher.take();
		} catch (InterruptedException x) {
			return false;
		}

		Path dir = keys.get(key);
		if (dir == null) {
			LOGGER.warning("WatchKey '{}' not recognized", key);
			return true;
		}

		for (WatchEvent<?> event : key.pollEvents()) {
			WatchEvent.Kind<?> kind = event.kind();

			if (kind == OVERFLOW) {
				LOGGER.warning("WatchKey '{}' overflowed", key);
				continue;
			}

			// Context for directory entry event is the file name of entry
			WatchEvent<Path> ev = cast(event);
			Path name = ev.context();
			Path child = dir.resolve(name);

			LOGGER.debug("Watch event '{}' on '{}'", event.kind().name(), child);

			//if(!paused) {
				callListeners(event, child);
			//}else {
			//	LOGGER.debug("Polling is paused...");
			//}
			// if directory is created, and watching recursively, then
			// register it and its sub-directories
			if (kind == ENTRY_CREATE) {
				try {
					if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
						registerAll(dir, child);
					}
				} catch (IOException x) {
					LOGGER.warning("Unable to register events for directory {}", x, child);
				}
			}
		}

		// reset key and remove from set if directory no longer accessible
		boolean valid = key.reset();
		if (!valid) {
			LOGGER.warning("Watcher on {} not valid, removing...", keys.get(key));
			keys.remove(key);
			// all directories are inaccessible
			if (keys.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// notify listeners about new event
	private void callListeners(final WatchEvent<?> event, final Path path) {
		//LOGGER.debug("Checking {} --->  {}", path, event.kind());
		boolean matchedOne = false;
		for (Map.Entry<Path, List<WatchEventListener>> list : listeners.entrySet()) {
			//LOGGER.debug("Checking {} ---> {}, {}", path, list.getKey(), event.kind());
			if (path.startsWith(list.getKey())) {
				matchedOne = true;
				for (WatchEventListener listener : list.getValue()) {
					WatchFileEvent agentEvent = new HotswapWatchFileEvent(event, path);
					try {
						listener.onEvent(agentEvent);
					} catch (Throwable e) {
						LOGGER.error("Error in watch event '{}' listener '{}'", e, agentEvent, listener);
					}
				}
			}
		}
		if(!matchedOne) {
			LOGGER.error("No match for  watch event '{}',  path '{}'",  event, path);
		}
	}

	@Override
	public void run() {

		runner = new Thread() {
			@Override
			public void run() {
				for (;;) {
					if (stopped || !processEvents()) {
						break;
					}
				}
			}
		};
		runner.setDaemon(true);
		runner.start();
	}

	@Override
	public void stop() {
		stopped = true;
	}



	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	@Override
	public Object getAttribute(String attribute)
			throws AttributeNotFoundException, MBeanException, ReflectionException {
		return paused;
	}

	@Override
	public void setAttribute(Attribute attribute)
			throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
		paused = (Boolean) attribute.getValue();
		
		LOGGER.info("Setting wather to paused = '{}'",  paused);

	}

	@Override
	public AttributeList getAttributes(String[] attributes) {
		AttributeList l = new AttributeList();
		l.add(new Attribute("paused", paused));
		return l;
	}

	@Override
	public AttributeList setAttributes(AttributeList attributes) {
		AttributeList l = new AttributeList();
		l.add(new Attribute("paused", paused));
		return l;
	}

	@Override
	public Object invoke(String actionName, Object[] params, String[] signature)
			throws MBeanException, ReflectionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MBeanInfo getMBeanInfo() {

		MBeanAttributeInfo attr = new MBeanAttributeInfo("paused", "boolean", "paused", true, true, true);

		MBeanInfo info = new MBeanInfo(this.getClass().getName(), "HotSwap", new MBeanAttributeInfo[] { attr },
				new MBeanConstructorInfo[] {}, new MBeanOperationInfo[] {}, new MBeanNotificationInfo[] {});
		return info;
	}

}