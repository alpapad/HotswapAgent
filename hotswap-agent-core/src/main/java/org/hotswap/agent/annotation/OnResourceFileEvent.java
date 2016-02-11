package org.hotswap.agent.annotation;

import static org.hotswap.agent.annotation.FileEvent.CREATE;
import static org.hotswap.agent.annotation.FileEvent.DELETE;
import static org.hotswap.agent.annotation.FileEvent.MODIFY;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event for a resource change (change of a file on the filesystem).
 * <p/>
 * Use with a non static method.
 * <p/>
 * Method attribute types:
 * <ul>
 * <li>URI - URI of the watched resource</li>
 * <li>URL - URL of the watched resource</li>
 * <li>ClassLoader - the application classloader</li>
 * <li>ClassPool - initialized javassist classpool</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnResourceFileEvent {

	/**
	 * Prefix of resource path to watch.
	 */
	String path();

	/**
	 * Regexp expression to filter resources.
	 */
	String filter() default "";

	/**
	 * Filter watch event types. Default is all events (CREATE, MODIFY, DELETE).
	 */
	FileEvent[] events() default { CREATE, MODIFY, DELETE };

	/**
	 * Watch only for regular files. By default all other types (including
	 * directories) are filtered out.
	 *
	 * @return true to filter out other types than regular types.
	 */
	public boolean onlyRegularFiles() default true;

	/**
	 * Merge multiple same watch events up to this timeout into a single watch
	 * event (useful to merge multiple MODIFY events).
	 */
	public int timeout() default 50;
}