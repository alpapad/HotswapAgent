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
	private void register(Path watched, Path target) throws IOException {
		
		for(PathPair p: keys.values()) {
			// This may NOT be correct for all cases (ensure resolve will work!)
			if(p.isWatching(target)) {
				LOGGER.debug("Path {} watched via {}", target, p.getWatched());
				return;
			}
		}
		
		// try to set high sensitivity
		WatchEvent.Modifier high = Util.get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH();
		WatchEvent.Modifier fileTree = Util.get_com_sun_nio_file_ExtendedWatchEventModifier_FILE_TREE();

		WatchKey key;
		if (high == null) {
			LOGGER.debug("WATCHING:ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree} {}", watched);
			key = watched.register(watcher, //
					new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }, //
					new WatchEvent.Modifier[] { fileTree });
		} else {
			LOGGER.debug("WATCHING: ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree,high {}", watched);
			key = watched.register(watcher, //
					new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }, //
					new WatchEvent.Modifier[] { fileTree, high });
		}
		keys.put(key, new PathPair(target, watched));
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	protected void registerAll(Path watched, Path target) throws IOException {
		if(watched == null){
			watched = target.getParent();
		}
		LOGGER.info("Registering directory target {} via watched: {}", target, watched);
		
		register(watched, target);
	}
}