/*
 * Copyright 2012, Stuart Douglas, and individual contributors as indicated
 * by the @authors tag.
 *
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

package org.hotswap.agent.plugin.jsf;

import java.util.Enumeration;

import javax.servlet.ServletContext;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;

/**
 * Change parameters to development stage so JSF implementation auto-reloads
 * artifacts
 * 
 * Does not seem to be consistent, or not working at all...
 * 
 * See: Changes in the ServletContext will trigger ServletContextEvent which is
 * the event class for notifications about changes to the servlet context of a
 * web application.
 * 
 * Maybe use a wrapper for the the getAttribute  method?
 * 
 * @author alpapad
 *
 */
public class ApplyFacesDevParams {
	private static AgentLogger LOGGER = AgentLogger.getLogger(ApplyFacesDevParams.class);

	public static void apply(final ServletContext context) {
		LOGGER.info("Applying ServletContext JSF Development parameters");

		// Maybe configurable?
		context.setAttribute("javax.faces.FACELETS_REFRESH_PERIOD", "1");
		context.setAttribute("facelets.REFRESH_PERIOD", "1");
		context.setAttribute("javax.faces.PROJECT_STAGE", "Development");
		context.setInitParameter("javax.faces.FACELETS_REFRESH_PERIOD", "1");
		context.setInitParameter("javax.faces.PROJECT_STAGE", "Development");
		context.setInitParameter("facelets.REFRESH_PERIOD", "1");

		if (LOGGER.isLevelEnabled(Level.TRACE)) {
			Enumeration<String> names = context.getAttributeNames();
			if (names != null) {
				while (names.hasMoreElements()) {
					String k = names.nextElement();
					LOGGER.trace("CONTEXT ATTRIBUTE {} value: {}", k, context.getAttribute(k));
				}
			}

			names = context.getInitParameterNames();
			if (names != null) {
				while (names.hasMoreElements()) {
					String k = names.nextElement();
					LOGGER.trace("CONTEXT INIT PARAM {} value: {}", k, context.getInitParameter(k));
				}
			}
		}
	}
}
