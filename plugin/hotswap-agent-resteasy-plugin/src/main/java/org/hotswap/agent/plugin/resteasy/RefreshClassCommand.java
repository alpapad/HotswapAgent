/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.hotswap.agent.plugin.resteasy;

import java.util.Arrays;

import javax.servlet.ServletContext;

import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.spi.Registry;

/**
 * Works only the first time ...
 * 
 * @author alpapad
 */
public class RefreshClassCommand extends MergeableCommand {

    private static AgentLogger LOGGER = AgentLogger.getLogger(RefreshClassCommand.class);

    ClassLoader classLoader;

    ServletContext context;
    
    String className;
    
    ServletContainerDispatcher servletContainerDispatcher;
    
    public void setupCmd(ClassLoader classLoader, Object context,Object servletContainerDispatcher, String className) {
        this.classLoader = classLoader;
        this.context = (ServletContext)context;
        this.servletContainerDispatcher = (ServletContainerDispatcher)servletContainerDispatcher;
        this.className = className;
    }

    @Override
    public void executeCommand() {
    	LOGGER.error("Re-Loading class: {}", className);
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        LOGGER.error("Re-Loading class: {} , {} , {}", className,oldClassLoader, classLoader );
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
        	Registry registry = (Registry) context.getAttribute(Registry.class.getName());
        	if(registry == null) {
        		registry = servletContainerDispatcher.getDispatcher().getRegistry();
        	}
        	Class<?> c = classLoader.loadClass(className);
        	LOGGER.error("Annotations:" + Arrays.toString(c.getAnnotations()));
        	registry.removeRegistrations(c);
        	registry.addPerRequestResource(c);
        } catch (ClassNotFoundException e) {
        	LOGGER.error("Could not load class {}", e, className);
		} finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classLoader == null) ? 0 : classLoader.hashCode());
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RefreshClassCommand other = (RefreshClassCommand) obj;
		if (classLoader == null) {
			if (other.classLoader != null)
				return false;
		} else if (!classLoader.equals(other.classLoader))
			return false;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "RefreshClassCommand [classLoader=" + classLoader + ", className=" + className + "]";
	}
}