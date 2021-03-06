/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package org.hotswap.agent.javassist.scopedpool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;

/**
 * An implementation of <code>ScopedClassPoolRepository</code>. It is an
 * singleton.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.4 $
 */
public class ScopedClassPoolRepositoryImpl implements org.hotswap.agent.javassist.scopedpool.ScopedClassPoolRepository {
	/**
	 * The instance
	 */
	private static final ScopedClassPoolRepositoryImpl instance = new ScopedClassPoolRepositoryImpl();

	/**
	 * Whether to prune
	 */
	private boolean prune = true;

	/**
	 * Whether to prune when added to the classpool's cache
	 */
	boolean pruneWhenCached;

	/**
	 * The registered classloaders
	 */
	protected Map<ClassLoader, ScopedClassPool> registeredCLs = Collections.synchronizedMap(new WeakHashMap<ClassLoader, ScopedClassPool>());

	/**
	 * The default class pool
	 */
	protected ClassPool classpool;

	/**
	 * The factory for creating class pools
	 */
	protected org.hotswap.agent.javassist.scopedpool.ScopedClassPoolFactory factory = new ScopedClassPoolFactoryImpl();

	/**
	 * Get the instance.
	 *
	 * @return the instance.
	 */
	public static org.hotswap.agent.javassist.scopedpool.ScopedClassPoolRepository getInstance() {
		return instance;
	}

	/**
	 * Singleton.
	 */
	private ScopedClassPoolRepositoryImpl() {
		classpool = ClassPool.getDefault();
		// FIXME This doesn't look correct
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		classpool.insertClassPath(new LoaderClassPath(cl));
	}

	/**
	 * Returns the value of the prune attribute.
	 *
	 * @return the prune.
	 */
	@Override
	public boolean isPrune() {
		return prune;
	}

	/**
	 * Set the prune attribute.
	 *
	 * @param prune
	 *            a new value.
	 */
	@Override
	public void setPrune(boolean prune) {
		this.prune = prune;
	}

	/**
	 * Create a scoped classpool.
	 *
	 * @param cl
	 *            the classloader.
	 * @param src
	 *            the original classpool.
	 * @return the classpool
	 */
	@Override
	public org.hotswap.agent.javassist.scopedpool.ScopedClassPool createScopedClassPool(ClassLoader cl, ClassPool src) {
		return factory.create(cl, src, this);
	}

	@Override
	public ClassPool findClassPool(ClassLoader cl) {
		if (cl == null) {
			return registerClassLoader(ClassLoader.getSystemClassLoader());
		}

		return registerClassLoader(cl);
	}

	/**
	 * Register a classloader.
	 *
	 * @param ucl
	 *            the classloader.
	 * @return the classpool
	 */
	@Override
	public ClassPool registerClassLoader(ClassLoader ucl) {
		synchronized (registeredCLs) {
			// FIXME: Probably want to take this method out later
			// so that AOP framework can be independent of JMX
			// This is in here so that we can remove a UCL from the ClassPool as
			// a
			// ClassPool.classpath
			if (registeredCLs.containsKey(ucl)) {
				return registeredCLs.get(ucl);
			}
			org.hotswap.agent.javassist.scopedpool.ScopedClassPool pool = createScopedClassPool(ucl, classpool);
			registeredCLs.put(ucl, pool);
			return pool;
		}
	}

	/**
	 * Get the registered classloaders.
	 */
	@Override
	public Map<ClassLoader, ScopedClassPool> getRegisteredCLs() {
		clearUnregisteredClassLoaders();
		return registeredCLs;
	}

	/**
	 * This method will check to see if a register classloader has been
	 * undeployed (as in JBoss)
	 */
	@Override
	public void clearUnregisteredClassLoaders() {
		ArrayList<ClassLoader> toUnregister = null;
		synchronized (registeredCLs) {
			Iterator<ScopedClassPool> it = registeredCLs.values().iterator();
			while (it.hasNext()) {
				org.hotswap.agent.javassist.scopedpool.ScopedClassPool pool = it
						.next();
				if (pool.isUnloadedClassLoader()) {
					it.remove();
					ClassLoader cl = pool.getClassLoader();
					if (cl != null) {
						if (toUnregister == null) {
							toUnregister = new ArrayList<ClassLoader>();
						}
						toUnregister.add(cl);
					}
				}
			}
			if (toUnregister != null) {
				for (int i = 0; i < toUnregister.size(); i++) {
					unregisterClassLoader(toUnregister.get(i));
				}
			}
		}
	}

	@Override
	public void unregisterClassLoader(ClassLoader cl) {
		synchronized (registeredCLs) {
			org.hotswap.agent.javassist.scopedpool.ScopedClassPool pool = registeredCLs
					.remove(cl);
			if (pool != null) {
				pool.close();
			}
		}
	}

	public void insertDelegate(org.hotswap.agent.javassist.scopedpool.ScopedClassPoolRepository delegate) {
		// Noop - this is the end
	}

	@Override
	public void setClassPoolFactory(org.hotswap.agent.javassist.scopedpool.ScopedClassPoolFactory factory) {
		this.factory = factory;
	}

	@Override
	public org.hotswap.agent.javassist.scopedpool.ScopedClassPoolFactory getClassPoolFactory() {
		return factory;
	}
}
