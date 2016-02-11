package org.hotswap.agent.watch;

import java.net.URI;

import org.hotswap.agent.annotation.FileEvent;

/**
 * An event on filesystem.
 *
 * @author Jiri Bubnik
 */
public interface WatchFileEvent {

	;

	/**
	 * @return type of the event
	 */
	public FileEvent getEventType();

	/**
	 * URI to file or directory with the event
	 *
	 * @return URI
	 */
	public URI getURI();

	/**
	 * URI is a file.
	 */
	public boolean isFile();

	/**
	 * URI is a directory.
	 */
	public boolean isDirectory();
}
