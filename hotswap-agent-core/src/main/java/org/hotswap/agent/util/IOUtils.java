package org.hotswap.agent.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.logging.AgentLogger;

/**
 * IO utils (similar to apache commons).
 */
public class IOUtils {
	private static AgentLogger LOGGER = AgentLogger.getLogger(IOUtils.class);

	/** URL protocol for a file in the file system: "file" */
	public static final String URL_PROTOCOL_FILE = "file";

	/** URL protocol for a JBoss VFS resource: "vfs" */
	public static final String URL_PROTOCOL_VFS = "vfs";

	// some IDEs remove and recreate whole package multiple times while
	// recompiling -
	// we may need to wait for a file to be available on a filesystem
	private static boolean fileIsMutating(File file) {
		if (!file.exists()) {
			LOGGER.debug("File does not exist!.... {}", file);
			return true;
		}
		long oldSize = 0L;
		long newSize = 1L;
		boolean fileIsOpen = true;

		int count = 0;
		// File file = new File(uri);

		while ((newSize > oldSize) || fileIsOpen) {
			if (count > 2) {
				return true;
			}
			oldSize = file.length();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				LOGGER.debug("Thread interupted.... {}", e, file);
				return true;
			}
			newSize = file.length();

			try (InputStream is = new FileInputStream(file)) {
				fileIsOpen = false;
			} catch (Exception e) {
				LOGGER.trace("File is locked/open {}", e, file);
				count++;
			}
		}

		return false;

	}

	/**
	 * Download URI to byte array.
	 *
	 * Wait for the file to exists up to 5 seconds - it may be recreated while
	 * IDE recompilation, automatic retry will avoid false errors.
	 *
	 * @param uri
	 *            uri to process
	 * @return byte array
	 * @throws IOException
	 * @throws IllegalArgumentException
	 *             for download problems
	 */
	public static byte[] toByteArray(URI uri) throws IOException {
		if (fileIsMutating(new File(uri))) {
			return null;
		}
		return Files.readAllBytes(new File(uri).toPath());
	}

	public static void copy(File source, File dest) throws IOException {
		if (fileIsMutating(source)) {
			throw new IllegalArgumentException("File  " + source + " can not be opened!");
		}
		try {
			if (!dest.mkdirs()) {
				LOGGER.error("Can not create path up to: {}", dest.toPath());
			}
		} catch (Exception e) {
			LOGGER.error("Can not create path up to: {}", e, dest.toPath());
		}
		Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Convert input stream to a string.
	 * 
	 * @param is
	 *            stream
	 * @return string (at least empty string for empty stream)
	 */
	public static String streamToString(InputStream is) {
		@SuppressWarnings("resource")
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
		String ss = s.hasNext() ? s.next() : "";
		s.close();
		return ss;
	}

	/**
	 * Determine whether the given URL points to a resource in the file system,
	 * that is, has protocol "file" or "vfs".
	 * 
	 * @param url
	 *            the URL to check
	 * @return whether the URL has been identified as a file system URL
	 * @author Juergen Hoeller (org.springframework.util.ResourceUtils)
	 */
	public static boolean isFileURL(URL url) {
		String protocol = url.getProtocol();
		return URL_PROTOCOL_FILE.equals(protocol) || protocol.startsWith(URL_PROTOCOL_VFS);
	}

	/**
	 * Determine whether the given URL points to a directory in the file system
	 * 
	 * @param url
	 *            the URL to check
	 * @return whether the URL has been identified as a file system URL
	 */
	public static boolean isDirectoryURL(URL url) {
		try {
			File f = new File(url.toURI());
			if (f.exists() && f.isDirectory()) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}

	/**
	 * Return fully qualified class name of class file on a URI.
	 *
	 * @param uri
	 *            uri of class file
	 * @return name
	 * @throws IOException
	 *             any exception on class instantiation
	 */
	public static String urlToClassName(URI uri) throws IOException {
		return ClassPool.getDefault().makeClass(uri.toURL().openStream()).getName();
	}
}
