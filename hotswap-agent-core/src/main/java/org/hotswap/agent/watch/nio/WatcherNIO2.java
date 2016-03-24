package org.hotswap.agent.watch.nio;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * NIO2 watcher implementation.
 * <p/>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * <p/>
 * By http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 * @author Jiri Bubnik
 */
public class WatcherNIO2 extends AbstractNIO2Watcher {

	public WatcherNIO2() throws IOException {
		super();
	}


	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	@Override
	protected void registerAll(final Path parent, Path start) throws IOException {
		// register directory and sub-directories
		LOGGER.info("Registering directory  {} under parent {}", start, parent);
		if(parent == null) {
			start = start.getParent();
		}
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Register the given directory with the WatchService
	 */
	private void register(Path dir) throws IOException {
//		// check duplicate registration
//		if (keys.values().contains(dir)) {
//			return;
//		}

		// try to set high sensitivity
		WatchEvent.Modifier high = Util.get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH();
		WatchKey key = high == null ? dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
				: dir.register(watcher, new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }, high);

		
		keys.put(key, dir);
	}
}