package org.hotswap.agent.plugin.hibernate3.session.proxy;

import org.hibernate.impl.SessionFactoryImpl;

public class ProxySessionFactoryImpl implements Proxied {

	
	private SessionFactoryImpl currentInstance;

	
	public ProxySessionFactoryImpl() {
		super();
	}

	public SessionFactoryImpl getCurrentInstance() {
		return currentInstance;
	}

	public void setCurrentInstance(SessionFactoryImpl currentInstance) {
		this.currentInstance = currentInstance;
	}
	
}
