package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;
import org.hotswap.agent.plugin.hibernate3.session.proxy.OverrideConfig.ConfiguredBy;

public interface ReInitializable {

	default void hotSwap(){
		
		OverrideConfig o = getOverrideConfig();
		reInitialize();
		switch(o.configuredBy) {
		case FILE:
			this.configure(File.class.cast(o.config));
			break;
		case NONE:
			this.configure();
			break;
		case STRING:
			this.configure(String.class.cast(o.config));
			break;
		case URL:
			this.configure(URL.class.cast(o.config));
			break;
		case W3C:
			this.configure(org.w3c.dom.Document.class.cast(o.config));
			break;
		default:
			throw new RuntimeException("Don't know how to reconficure...");
		}
		for(Map.Entry<String, String> e: o.properties.entrySet()) {
			setProperty(e.getKey(), e.getValue());
		}
	}
	
	void reInitialize();
	
	
	OverrideConfig getOverrideConfig();
	
	
	
    default Configuration setProperty(String propertyName, String value) {
    	System.err.println("setProperty..................... key:" + propertyName + ", value:" + value);
    	_setProperty( propertyName, value);
    	getOverrideConfig().properties.put(propertyName, value);
    	return (Configuration)this;
    }
    
    default Configuration configure(String resource) throws HibernateException {
    	System.err.println("Configuring....................." + resource);
    	_configure(resource);
    	getOverrideConfig().config = resource;
    	getOverrideConfig().configuredBy = ConfiguredBy.STRING;
    	getOverrideConfig().properties.clear();
    	return (Configuration)this;
    }
    default Configuration configure(URL url) throws HibernateException {
    	System.err.println("Configuring....................." + url);
    	_configure(url);
    	getOverrideConfig().config = url;
    	getOverrideConfig().configuredBy = ConfiguredBy.URL; 	
    	getOverrideConfig().properties.clear();
    	return (Configuration)this;
    }
    
    default Configuration configure(File configFile) throws HibernateException{
    	System.err.println("Configuring....................." + configFile);
    	_configure(configFile);
    	getOverrideConfig().properties.clear();
    	return (Configuration)this;
    }
    
    default Configuration configure(org.w3c.dom.Document document) throws HibernateException {
    	System.err.println("Configuring....................." + document);
    	_configure(document);
    	getOverrideConfig().config = document;
    	getOverrideConfig().configuredBy = ConfiguredBy.W3C;
    	getOverrideConfig().properties.clear();
    	return (Configuration)this;
    }
	
    default Configuration configure() throws HibernateException{
    	System.err.println("Configuring..................... EMPTY..");
    	_configure();
    	getOverrideConfig().config = null;
    	getOverrideConfig().configuredBy = ConfiguredBy.NONE;
    	getOverrideConfig().properties.clear();
    	return (Configuration)this;
    }
    
    // Hiden..
    org.hibernate.SessionFactory _buildSessionFactory() throws org.hibernate.HibernateException;
    
    Configuration _setProperty(String propertyName, String value);
    Configuration _configure() throws HibernateException;
    Configuration _configure(String resource) throws HibernateException ;
    Configuration _configure(URL url) throws HibernateException ;
    Configuration _configure(File configFile) throws HibernateException;
    Configuration _configure(org.w3c.dom.Document document) throws HibernateException;
}
