package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * Container to merge attributes from similar annotations: OnClassFileEvent and
 * OnResourceFileEvent.
 */
public class WatchEventDTO {
	private final boolean classFileEvent;
	private final int timeout;
	private final FileEvent[] events;
	private final String classNameRegexp;
	private final String filter;
	private final String path;
	private final boolean onlyRegularFiles;

	/**
	 * Parse the annotation to fill in the container.
	 */
	public static <T extends Annotation> WatchEventDTO parse(T annotation) {
		if (annotation instanceof OnClassFileEvent) {
			return new WatchEventDTO((OnClassFileEvent) annotation);
		} else if (annotation instanceof OnResourceFileEvent) {
			return new WatchEventDTO((OnResourceFileEvent) annotation);
		} else {
			throw new IllegalArgumentException("Invalid annotation type " + annotation);
		}
	}

	public WatchEventDTO(OnClassFileEvent annotation) {
		classFileEvent = true;
		timeout = annotation.timeout();
		classNameRegexp = annotation.classNameRegexp();
		events = annotation.events();
		onlyRegularFiles = true;
		filter = null;
		path = null;
	}

	public WatchEventDTO(OnResourceFileEvent annotation) {
		classFileEvent = false;
		timeout = annotation.timeout();
		filter = annotation.filter();
		path = annotation.path();
		events = annotation.events();
		onlyRegularFiles = annotation.onlyRegularFiles();
		classNameRegexp = null;
	}

	public boolean isClassFileEvent() {
		return classFileEvent;
	}

	public int getTimeout() {
		return timeout;
	}

	public FileEvent[] getEvents() {
		return events;
	}

	public String getClassNameRegexp() {
		return classNameRegexp;
	}

	public String getFilter() {
		return filter;
	}

	public String getPath() {
		return path;
	}

	public boolean isOnlyRegularFiles() {
		return onlyRegularFiles;
	}

	/**
	 * Check if this handler supports actual event.
	 *
	 * @param event
	 *            file event fired by filesystem
	 * @return true if supports - should continue handling
	 */
	public boolean accept(WatchFileEvent event) {

		// all handlers currently support only files
		if (!event.isFile()) {
			return false;
		}

		// load class files only from files named ".class"
		if (isClassFileEvent() && !event.getURI().toString().endsWith(".class")) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (classFileEvent ? 1231 : 1237);
		result = prime * result + (classNameRegexp == null ? 0 : classNameRegexp.hashCode());
		result = prime * result + Arrays.hashCode(events);
		result = prime * result + (filter == null ? 0 : filter.hashCode());
		result = prime * result + (onlyRegularFiles ? 1231 : 1237);
		result = prime * result + (path == null ? 0 : path.hashCode());
		result = prime * result + timeout;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WatchEventDTO other = (WatchEventDTO) obj;
		if (classFileEvent != other.classFileEvent) {
			return false;
		}
		if (classNameRegexp == null) {
			if (other.classNameRegexp != null) {
				return false;
			}
		} else if (!classNameRegexp.equals(other.classNameRegexp)) {
			return false;
		}
		if (!Arrays.equals(events, other.events)) {
			return false;
		}
		if (filter == null) {
			if (other.filter != null) {
				return false;
			}
		} else if (!filter.equals(other.filter)) {
			return false;
		}
		if (onlyRegularFiles != other.onlyRegularFiles) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		if (timeout != other.timeout) {
			return false;
		}
		return true;
	}

}
