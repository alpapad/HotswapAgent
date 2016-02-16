package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.impl.SessionFactoryImpl;

/**
 * For Hibernate without EJB (EntityManager).
 * <p/>
 * TODO - Not tested, some additional Configuration cleanup may be necessary
 *
 * @author Jiri Bubnik
 */
public class SessionFactoryProxy {
	private static Map<Configuration, SessionFactoryProxy> proxiedFactories = new HashMap<Configuration, SessionFactoryProxy>();

	public static SessionFactoryProxy getWrapper(Configuration configuration) {
		synchronized (proxiedFactories) {
			if (!proxiedFactories.containsKey(configuration)) {
				proxiedFactories.put(configuration, new SessionFactoryProxy(configuration));
			}

			return proxiedFactories.get(configuration);
		}
	}

	public static void refreshProxiedFactories() {
		synchronized (proxiedFactories) {
			for (SessionFactoryProxy wrapper : proxiedFactories.values())
				try {
					wrapper.refreshProxiedFactory();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
	}

	private SessionFactoryProxy(Configuration configuration) {
		this.configuration = configuration;
	}

	public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReInitializable.class.cast(configuration).hotSwap();
		Method m = Configuration.class.getDeclaredMethod("_buildSessionFactory");
		currentInstance = (SessionFactory) m.invoke(configuration);
		proxy.setCurrentInstance(SessionFactoryImpl.class.cast(currentInstance));
	}

	private Configuration configuration;

	private SessionFactory currentInstance;
	
	private Proxied  proxy;
	
	public SessionFactory proxy(SessionFactory sessionFactory) {
		try {
			this.currentInstance = sessionFactory;
			Class<?> cc = this.getClass().getClassLoader().loadClass("org.hotswap.agent.plugin.hibernate3.session.proxy.ProxySessionFactoryImpl");
			Object o = cc.newInstance();
			proxy = Proxied.class.cast(o);
			proxy.setCurrentInstance(SessionFactoryImpl.class.cast(currentInstance));
			return SessionFactory.class.cast(proxy);
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Unable instantiate SessionFactory proxy", e);
		}
	}
}
