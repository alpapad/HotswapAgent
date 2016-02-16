package org.hotswap.agent.plugin.hibernate3.session.proxy;

import org.hibernate.impl.SessionFactoryImpl;

public interface Proxied {

	SessionFactoryImpl getCurrentInstance();
	void setCurrentInstance(SessionFactoryImpl currentInstance);
	
}
