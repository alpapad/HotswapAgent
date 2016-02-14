package org.hotswap.agent.watch.nio;

import java.lang.reflect.Field;
import java.nio.file.WatchEvent;

public class Util {

	

	/**
	 * Get modifier for high sensitivity on Watch events.
	 *
	 * @see <a href="https://github.com/HotswapProjects/HotswapAgent/issues/41">
	 *      Issue#41</a>
	 * @see <a href=
	 *      "http://stackoverflow.com/questions/9588737/is-java-7-watchservice-slow-for-anyone-else">
	 *      Is Java 7 WatchService Slow for Anyone Else?</a>
	 */
	public static WatchEvent.Modifier get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH() {
		try {
			Class<?> c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
			Field f = c.getField("HIGH");
			return (WatchEvent.Modifier) f.get(c);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE modifier.
	 * Supported at least for Windows
	 */
	public static  WatchEvent.Modifier get_com_sun_nio_file_ExtendedWatchEventModifier_FILE_TREE() {
		try {
			Class<?> c = Class.forName("com.sun.nio.file.ExtendedWatchEventModifier");
			Field f = c.getField("FILE_TREE");
			return (WatchEvent.Modifier) f.get(c);
		} catch (Exception e) {
			return null;
		}
	}
}
