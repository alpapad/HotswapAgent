package org.hotswap.agent.watch;

import java.io.IOException;

import org.hotswap.agent.watch.nio.TreeWatcherNIO;
//import org.hotswap.agent.watch.nio.WatcherNIO2;

/**
 * Resolve watcher from java version (Java NIO2 implementation is preferred if
 * available.)
 *
 * @author Jiri Bubnik
 */
public class WatcherFactory {

	public static double JAVA_VERSION = getVersion();

	static double getVersion() {
		String version = System.getProperty("java.version");
		int pos = 0, count = 0;
		for (; pos < version.length() && count < 2; pos++) {
			if (version.charAt(pos) == '.') {
				count++;
			}
		}
		return Double.parseDouble(version.substring(0, pos - 1));
	}

	public Watcher getWatcher() throws IOException {
		if (JAVA_VERSION >= 1.7) {
			return new TreeWatcherNIO();
		} else {
			throw new UnsupportedOperationException("Watcher is implemented only for Java 1.7 (NIO2). "
					+ "JNotify implementation should be added in the future for older Java version support.");
		}

	}
}
