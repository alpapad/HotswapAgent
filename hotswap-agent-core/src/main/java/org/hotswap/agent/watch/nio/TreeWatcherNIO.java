package org.hotswap.agent.watch.nio;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

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
public class TreeWatcherNIO extends AbstractNIO2Watcher {

	public TreeWatcherNIO() throws IOException {
		super();
	}

	/**
	 * Register the given directory with the WatchService
	 */
	private void register(Path parent, Path dir) throws IOException {
		// check duplicate registration
		if (keys.values().contains(dir)) {
			return;
		}
		
		if(parent != null && keys.values().contains(parent)) {
			return;
		}
		
		for(Path p: keys.values()) {
			if(dir.startsWith(p)) {
				LOGGER.debug("Path {} watched via {}", dir, p);
				return;
			}
		}
		
		// try to set high sensitivity
		WatchEvent.Modifier high = Util.get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH();
		WatchEvent.Modifier fileTree = Util.get_com_sun_nio_file_ExtendedWatchEventModifier_FILE_TREE();

		WatchKey key;
		if (high == null) {
			LOGGER.debug("WATCHING:ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree} {}", dir);
			key = dir.register(watcher, //
					new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }, //
					new WatchEvent.Modifier[] { fileTree });
		} else {
			LOGGER.debug("WATCHING: ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree,high {}", dir);
			key = dir.register(watcher, //
					new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }, //
					new WatchEvent.Modifier[] { fileTree, high });
		}
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	protected void registerAll(final Path parent, final Path start) throws IOException {
		// register directory and sub-directories
		LOGGER.info("Registering directory  {} under parent {}", parent,  start);
		if(parent != null && keys.values().contains(parent)) {
			LOGGER.info("Registering directory  {} under parent {}, SKIPPED", parent,  start);
			return;
		}
		
		register(parent, start);
	}
}