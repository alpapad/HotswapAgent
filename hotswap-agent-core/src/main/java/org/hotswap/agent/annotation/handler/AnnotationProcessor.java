package org.hotswap.agent.annotation.handler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Process annotations on a plugin, register appropriate handlers.
 *
 * @author Jiri Bubnik
 */
public class AnnotationProcessor {
	private static AgentLogger LOGGER = AgentLogger.getLogger(AnnotationProcessor.class);

	protected PluginManager pluginManager;

	public AnnotationProcessor(PluginManager pluginManager) {
		this.pluginManager = pluginManager;
		init(pluginManager);
	}

	@SuppressWarnings("rawtypes")
	protected Map<Class<? extends Annotation>, PluginHandler> handlers = new HashMap<>();

	public void init(PluginManager pluginManager) {
		WatchHandler<? extends Annotation> hanlder=	new WatchHandler<>(pluginManager);
		
		addAnnotationHandler(Init.class, new InitHandler(pluginManager));
		addAnnotationHandler(OnClassLoadEvent.class, new OnClassLoadedHandler(pluginManager));
		addAnnotationHandler(OnClassFileEvent.class, hanlder);
		addAnnotationHandler(OnResourceFileEvent.class, hanlder);
	}

	public void addAnnotationHandler(Class<? extends Annotation> annotation, PluginHandler<?> handler) {
		handlers.put(annotation, handler);
	}

	/**
	 * Process annotations on the plugin class - only static methods, methods to
	 * hook plugin initialization.
	 *
	 * @param processClass
	 *            class to process annotation
	 * @param pluginClass
	 *            main plugin class (annotated with @Plugin)
	 * @return true if success
	 */
	public boolean processStaticAnnotations(Class<?> processClass, Class<?> pluginClass) {
		LOGGER.debug("Processing annotations on static methods and fields for plugin: '" + pluginClass );
		try {
			for (Field field : processClass.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					if (!processFieldAnnotations(null, field, pluginClass)) {
						return false;
					}
				}

			}

			for (Method method : processClass.getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers())) {
					if (!processMethodAnnotations(null, method, pluginClass)) {
						return false;
					}
				}
			}

			// process annotations on all supporting classes in addition to the plugin itself
			for (Annotation annotation : processClass.getDeclaredAnnotations()) {
				if (annotation instanceof Plugin) {
					for (Class<?> supportClass : ((Plugin) annotation).supportClass()) {
						processStaticAnnotations(supportClass, pluginClass);
					}
				}
			}

			return true;
		} catch (Throwable e) {
			LOGGER.error("Unable to process plugin annotations '{}'", e, pluginClass);
			return false;
		}
	}

	/**
	 * Process annotations on a plugin - non static fields and methods.
	 *
	 * @param plugin
	 *            plugin object
	 * @return true if success
	 */
	public boolean processPluginInstanceAnnotations(Object plugin, ClassLoader classLoader, PluginConfiguration cfg) {
		LOGGER.debug("Processing annotations on plugin '" + plugin + "'.");

		LOGGER.debug("Initializing plugin {},\n{}\n{}", plugin, cfg, classLoader);
				
		Class<?> pluginClass = plugin.getClass();

		for (Field field : pluginClass.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				if (!processFieldAnnotations(plugin, field, pluginClass)) {
					return false;
				}
			}

		}

		for (Method method : pluginClass.getDeclaredMethods()) {
			if (!Modifier.isStatic(method.getModifiers())) {
				if (!processMethodAnnotations(plugin, method, pluginClass)) {
					return false;
				}
			}
		}

		return true;
	}

	private boolean processFieldAnnotations(Object plugin, Field field, Class<?> pluginClass) {
		// for all fields and all handlers
		for (Annotation annotation : field.getDeclaredAnnotations()) {
			for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
				if (annotation.annotationType().equals(handlerAnnotation)) {
					// initialize
					LOGGER.debug("Initializing field for plugin '{}'.{}", plugin, field.getName());

					PluginAnnotation<?> pluginAnnotation = new PluginAnnotation<>(pluginClass, plugin, annotation, field);
					if (!handlers.get(handlerAnnotation).initField(pluginAnnotation)) {
						LOGGER.error("Could not process field annotations for plugin '{}'.{}", plugin, field.getName());
						return false;
					}else {
						LOGGER.debug("Initialized field for plugin '{}'.{}", plugin, field.getName());
					}
				} 
			}
		}
		return true;
	}

	private boolean processMethodAnnotations(Object plugin, Method method, Class<?> pluginClass) {
		// for all methods and all handlers
		for (Annotation annotation : method.getDeclaredAnnotations()) {
			for (Class<? extends Annotation> handlerAnnotation : handlers.keySet()) {
				if (annotation.annotationType().equals(handlerAnnotation)) {
					// initialize
					LOGGER.debug("Initializing method for plugin '{}'.{}", plugin, method.getName());
					PluginAnnotation<?> pluginAnnotation = new PluginAnnotation<>(pluginClass, plugin, annotation, method);
					if (!handlers.get(handlerAnnotation).initMethod(pluginAnnotation)) {
						LOGGER.error("Could not process method annotations for plugin '{}'.{}", plugin, method.getName());
						return false;
					}else {
						LOGGER.debug("Initialized method for plugin '{}'.{}", plugin, method.getName());
					}
				}
			}
		}
		return true;
	}
}
