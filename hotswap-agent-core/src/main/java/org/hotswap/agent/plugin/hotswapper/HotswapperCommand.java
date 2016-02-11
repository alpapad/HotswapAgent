package org.hotswap.agent.plugin.hotswapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.logging.AgentLogger;

/**
 * Hotswapper command must run in application classloader because tools.jar
 * dependency can be easier added to the application classloader than to java
 * classpath.
 *
 * @author Jiri Bubnik
 */
public class HotswapperCommand {
	private static AgentLogger LOGGER = AgentLogger.getLogger(HotswapperCommand.class);

	// HotSwapperJpda will connect to JPDA on first hotswap command and remain
	// connected.
	// The HotSwapperJpda class from javaassist is copied to the plugin, becuse
	// it needs to reside
	// in the application classloader to avoid NoClassDefFound error on
	// tools.jar classes.
	private static HotSwapperJpda hotSwapper = null;

	public static synchronized void hotswap(String port, final HashMap<Class<?>, byte[]> reloadMap) {
		// synchronize on the reloadMap object - do not allow addition while in
		// process
		synchronized (reloadMap) {
			if (hotSwapper == null) {
				LOGGER.debug("Starting HotSwapperJpda agent on JPDA transport socket - port {}, classloader {}", port,
						HotswapperCommand.class.getClassLoader());
				try {
					hotSwapper = new HotSwapperJpda(port);
				} catch (IOException e) {
					LOGGER.error(
							"Unable to connect to debug session. Did you start the application with debug enabled "
									+ "(i.e. java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000)",
							e);
				} catch (Exception e) {
					LOGGER.error("Unable to connect to debug session. Please check port property setting '{}'.", e,
							port);
				}
			}

			if (hotSwapper != null) {
				LOGGER.debug("Reloading classes {}", Arrays.toString(reloadMap.keySet().toArray()));

				// convert to Map Class name -> bytecode
				// We loose some information here, reload use always first class
				// name that it finds reference to.
				Map<String, byte[]> reloadMapClassNames = new HashMap<String, byte[]>();
				for (Map.Entry<Class<?>, byte[]> entry : reloadMap.entrySet()) {
					reloadMapClassNames.put(entry.getKey().getName(), entry.getValue());
				}
				// actual hotswap via JPDA
				hotSwapper.reload(reloadMapClassNames);

				reloadMap.clear();
				LOGGER.debug("HotSwapperJpda agent reload complete.");	
			}
		}
	}
}
