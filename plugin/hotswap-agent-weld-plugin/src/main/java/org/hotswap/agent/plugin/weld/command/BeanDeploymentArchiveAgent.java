package org.hotswap.agent.plugin.weld.command;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.logging.AgentLogger.Level;
import org.hotswap.agent.plugin.weld.ProxyClassSignatureHelper;
import org.hotswap.agent.plugin.weld.WeldPlugin;
import org.hotswap.agent.plugin.weld.beans.ContextualReloadHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.jlr.EnhancedAnnotatedTypeImpl;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.jboss.weld.annotated.slim.backed.BackedAnnotatedType;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.bean.SessionBean;
import org.jboss.weld.bean.attributes.BeanAttributesFactory;
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.resources.ReflectionCache;
import org.jboss.weld.resources.ReflectionCacheFactory;
import org.jboss.weld.resources.SharedObjectCache;
import org.jboss.weld.util.Beans;

/**
 * Handles creating/redefinition of bean classes in BeanDeploymentArchive
 *
 * @author Vladimir Dvorak
 */
public class BeanDeploymentArchiveAgent {

	private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDeploymentArchiveAgent.class);

	/**
	 * Flag to check reload status. In unit test we need to wait for reload
	 * finish before the test can continue. Set flag to true in the test class
	 * and wait until the flag is false again.
	 */
	public static boolean reloadFlag = false;

	private BeanDeploymentArchive deploymentArchive;

	private String archivePath;

	private boolean registered = false;

	// private org.jboss.weld.bootstrap.BeanDeployer deployer;
	/**
     * Register bean archive with appropriate bean archive path.
     *
     * @param beanArchive
     * @param archivePath
     */
    @SuppressWarnings("unused")
    public static void registerArchive(ClassLoader classLoader, BeanDeploymentArchive beanArchive,String beanArchiveType) {
		BeansXml beansXml = beanArchive.getBeansXml();
		String archivePath = null;
		if (beansXml != null && beanArchiveType != null && ("EXPLICIT".equals(beanArchiveType) || "IMPLICIT".equals(beanArchiveType))) {
			String beansXmlPath = beansXml.getUrl().getPath();
			if (beansXmlPath.endsWith("META-INF/beans.xml")) {
				archivePath = beansXmlPath.substring(0, beansXmlPath.length() - "META-INF/beans.xm".length() - 1);/* -1 ~ eat "/" at the end of path */
			} else if (beansXmlPath.endsWith("WEB-INF/beans.xml")) {
				archivePath = beansXmlPath.substring(0, beansXmlPath.length() - "beans.xml".length()) + "classes";
			}
		}
    	BeanDeploymentArchiveAgent bdaAgent = null;
        try {
        	LOGGER.debug("BeanDeploymentArchiveAgent registerArchive bdaId='{}' archivePath='{}'.", beanArchive.getId(), archivePath);
            // check that it is regular file
            // toString() is weird and solves HiearchicalUriException for URI like "file:./src/resources/file.txt".
        	if(archivePath != null) {
	            File path = new File(archivePath);
	            boolean contain = (boolean) ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader), "contains", new Class[] {String.class}, archivePath);
	            if (!contain) {
	                bdaAgent = new BeanDeploymentArchiveAgent(beanArchive, archivePath);
	                ReflectionHelper.invoke(null, Class.forName(BdaAgentRegistry.class.getName(), true, classLoader),
	                    "put", new Class[] {String.class, BeanDeploymentArchiveAgent.class}, archivePath, bdaAgent);
	                bdaAgent.register();
	            }
        	}
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to watch BeanDeploymentArchive with id={}", beanArchive.getId());
        }
        catch (Exception e) {
            LOGGER.error("registerArchive() exception {}.",e, e.getMessage());
        }


    }

	/**
	 * Gets the collection of registered BeanDeploymentArchive(s)
	 *
	 * @return the instances
	 */
	public static Collection<BeanDeploymentArchiveAgent> getInstances() {
		return BdaAgentRegistry.values();
	}

	private BeanDeploymentArchiveAgent(BeanDeploymentArchive deploymentArchive, String archivePath) {
		this.deploymentArchive = deploymentArchive;
		this.archivePath = archivePath;
	}

	/**
	 * Gets the bdaId.
	 *
	 * @return the bdaId
	 */
	public String getBdaId() {
		return deploymentArchive.getId();
	}

	/**
	 * Gets the archive path.
	 *
	 * @return the archive path
	 */
	public String getArchivePath() {
		return archivePath;
	}

	public BeanDeploymentArchive getDeploymentArchive() {
		return deploymentArchive;
	}

	private void register() {
		if (!registered) {
			registered = true;
			PluginManagerInvoker.callPluginMethod(WeldPlugin.class, getClass().getClassLoader(),
					"registerBeanDeplArchivePath", new Class[] { String.class }, new Object[] { archivePath });
			LOGGER.info("BeanDeploymentArchiveAgent registered bdaId='{}' archivePath='{}'.", getBdaId(), archivePath);
		}
	}

	/**
	 * Called by a reflection command from BeanRefreshCommand transformer.
	 *
	 * @param classLoader
	 * @param archivePath
	 * @param beanClassName
	 * @throws IOException
	 *             error working with classDefinition
	 */
	public static void refreshBeanClass(ClassLoader classLoader, String archivePath,
			Map<Object, Object> registeredProxiedBeans, String beanClassName, String oldClassSignature)
			throws IOException {

		BeanDeploymentArchiveAgent bdaAgent = BdaAgentRegistry.get(archivePath);

		if (bdaAgent == null) {
			LOGGER.error("Archive path '{}' is not associated with any BeanDeploymentArchiveAgent", archivePath);
			return;
		}

		try {
            // BDA classLoader can be different then appClassLoader for
            // Wildfly/EAR deployment therefore we use class loader from
            // BdaAgent class which is classloader for BDA
			Class<?> beanClass = bdaAgent.getClass().getClassLoader().loadClass(beanClassName);

			bdaAgent.refreshProxy(classLoader, registeredProxiedBeans, beanClass, oldClassSignature);
			bdaAgent.reloadBean(classLoader, beanClass);

		} catch (ClassNotFoundException e) {
			LOGGER.error("Bean class not found.", e);
		} finally {
			reloadFlag = false;
		}
	}

	/**
	 * Reload bean in existing bean manager.
	 *
	 * @param bdaId
	 *            the Bean Deployment Archive ID
	 * @param beanClassName
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void reloadBean(ClassLoader classLoader, Class<?> beanClass) {

		ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

		try {
			Thread.currentThread().setContextClassLoader(classLoader);

			// check if it is Object descendant
			if (Object.class.isAssignableFrom(beanClass)) {
				BeanManagerImpl beanManager = ((BeanManagerProxy) CDI.current().getBeanManager()).unwrap();
				
				Set<Bean<?>> beans = beanManager.getBeans(beanClass);

				if (beans != null && !beans.isEmpty()) {
					for (Bean<?> bean : beans) {
						if (bean instanceof ManagedBean) {
							reloadManagedBean(beanManager, beanClass, (ManagedBean) bean);
						} else if (bean instanceof SessionBean) {
							reloadSessionBean(beanManager, beanClass, (SessionBean) bean);
						} else {
							LOGGER.warning("reloadBean() : class '{}' reloading is not implemented ({}).", bean.getClass().getName(), bean.getBeanClass());
						}
					}
					LOGGER.debug("Bean reloaded '{}'", beanClass.getName());
				} else {
					try {
						ClassTransformer classTransformer = getClassTransformer();
						SlimAnnotatedType annotatedType = getAnnotatedType(getBdaId(), classTransformer, beanClass);
						boolean managedBeanOrDecorator = Beans.isTypeManagedBeanOrDecoratorOrInterceptor(annotatedType);

						if (managedBeanOrDecorator) {
							EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
							defineManagedBean(beanManager, eat);
							// define managed bean
							LOGGER.debug("Bean defined '{}'", beanClass.getName());
						} else {
							// TODO : define session bean
							LOGGER.warning("Bean NOT? defined '{}', session bean?", beanClass.getName());
						}
					} catch (Exception e) {
						LOGGER.debug("Bean definition failed", e);
					}
				}
			}
		} finally {
			Thread.currentThread().setContextClassLoader(oldContextClassLoader);
		}
	}

	private static Map<Class<? extends Annotation>, List<Context>> getContexts(BeanManagerImpl beanManager){
        
        try {
            Field contextsField = BeanManagerImpl.class.getField("contexts");
            contextsField.setAccessible(true);
            
            Map<Class<? extends Annotation>, List<Context>> ctxs= Map.class.cast(contextsField.get(beanManager));
            return ctxs;
            
        } catch (IllegalAccessException |IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            LOGGER.warning("BeanManagerImpl.contexts not accessible", e);
        } 
        return Collections.emptyMap();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void reloadManagedBean(BeanManagerImpl beanManager, Class<?> beanClass, ManagedBean managedBean) {
		ClassTransformer classTransformer = getClassTransformer();
		SlimAnnotatedType annotatedType = getAnnotatedType(getBdaId(), classTransformer, beanClass);
		EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);
		
		if (!eat.isAbstract() || !eat.getJavaClass().isInterface()) { // injectionTargetCannotBeCreatedForInterface
			managedBean.setProducer(beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, managedBean, false));

			try {
				Map<Class<? extends Annotation>, List<Context>> ctxs= getContexts(beanManager);
				
				List<Context> contextxs = ctxs.get(managedBean.getScope());
				
				if((contextxs != null) && (contextxs.size() > 0)) {
					for(Context context: contextxs) {
						if (context != null) {
							LOGGER.debug("Inspecting context..... '{}' {}", context.getClass(), managedBean.getScope());
							if(ContextualReloadHelper.addToReloadSet(context, managedBean)) {
							    LOGGER.debug("Bean {}, added to reload set in context {}", managedBean, context.getClass());
							} else {
							    // try to reinitialize injection points instead...
							    try {
					                Object get = context.get(managedBean);
					                if (get != null) {
					                    LOGGER.debug("Bean injection points are reinitialized '{}'", beanClass.getName());
					                    managedBean.getProducer().inject(get, beanManager.createCreationalContext(managedBean));
					                }
					            } catch (Exception e) {
					                if(LOGGER.isLevelEnabled(Level.DEBUG)) {
					                    LOGGER.debug("Context {} not active for bean: {} in scope: {}",e, context.getClass(), beanClass.getName(), managedBean.getScope());
					                } else {
                                        LOGGER.warning("Context {} not active for bean: {} in scope: {}", context.getClass(), beanClass.getName(), managedBean.getScope());
					                }
					            }
							}
						} else {
							LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
						}
					}
				} else {
					LOGGER.debug("No active contexts for bean: {} in scope: {}",  managedBean.getScope(), beanClass.getName());
				}
			} catch (org.jboss.weld.context.ContextNotActiveException e) {
				LOGGER.warning("No active contexts for {}", e, beanClass.getName());
			} catch (Exception e) {
				LOGGER.warning("Context for {} failed to reload", e, beanClass.getName());
			}
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void reloadSessionBean(BeanManagerImpl beanManager, Class<?> beanClass, SessionBean sessionBean) {
		ClassTransformer classTransformer = getClassTransformer();
		SlimAnnotatedType annotatedType = getAnnotatedType(getBdaId(), classTransformer, beanClass);
		EnhancedAnnotatedType eat = EnhancedAnnotatedTypeImpl.of(annotatedType, classTransformer);

		sessionBean.setProducer(beanManager.getLocalInjectionTargetFactory(eat).createInjectionTarget(eat, sessionBean, false));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void defineManagedBean(BeanManagerImpl beanManager, EnhancedAnnotatedType eat) throws Exception {
		BeanAttributes attributes = BeanAttributesFactory.forBean(eat, beanManager);
		ManagedBean<?> bean = ManagedBean.of(attributes, eat, beanManager);
		Field field = beanManager.getClass().getDeclaredField("beanSet");
		field.setAccessible(true);
		field.set(beanManager, Collections.synchronizedSet(new HashSet<Bean<?>>()));
		// TODO:
		beanManager.addBean(bean);
		beanManager.getBeanResolver().clear();
		bean.initializeAfterBeanDiscovery();
	}

	private ClassTransformer getClassTransformer() {
		TypeStore store = new TypeStore();
		SharedObjectCache cache = new SharedObjectCache();
		ReflectionCache reflectionCache = ReflectionCacheFactory.newInstance(store);
		ClassTransformer classTransformer = new ClassTransformer(store, cache, reflectionCache, "STATIC_INSTANCE");
		return classTransformer;
	}

	@SuppressWarnings("rawtypes")
	private SlimAnnotatedType getAnnotatedType(String bdaId, ClassTransformer classTransformer, Class<?> beanClass) {
		BackedAnnotatedType<?> annotatedType = classTransformer.getBackedAnnotatedType(beanClass, beanClass, bdaId);
		return annotatedType;
	}

	private void refreshProxy(ClassLoader classLoader, Map<Object, Object> registeredProxiedBeans, Class<?> beanClass,
			String oldClassSignature) {
		if (oldClassSignature != null && registeredProxiedBeans != null) {
			String newClassSignature = ProxyClassSignatureHelper.getJavaClassSignature(beanClass);
			if (newClassSignature != null && !newClassSignature.equals(oldClassSignature)) {
				synchronized (registeredProxiedBeans) {
					if (!registeredProxiedBeans.isEmpty()) {
						doRefreshProxy(classLoader, registeredProxiedBeans, beanClass);
					}
				}
			}
		}
	}

	private void doRefreshProxy(ClassLoader classLoader, Map<Object, Object> registeredBeans, Class<?> proxyClass) {
		ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

		ProxyClassLoadingDelegate.beginProxyRegeneration();

		try {
			Class<?> proxyFactoryClass = null;

			for (Entry<Object, Object> entry : registeredBeans.entrySet()) {
				Bean<?> bean = (Bean<?>) entry.getKey();

				if (bean != null) {
					Set<Type> types = bean.getTypes();
					if (types.contains(proxyClass)) {
						Thread.currentThread().setContextClassLoader(bean.getBeanClass().getClassLoader());
						if (proxyFactoryClass == null) {
							proxyFactoryClass = classLoader.loadClass("org.jboss.weld.bean.proxy.ProxyFactory");
						}
						Object proxyFactory = entry.getValue();
						LOGGER.info("Recreate proxyClass {} for bean class {}.", proxyClass.getName(), bean.getClass());
						ReflectionHelper.invoke(proxyFactory, proxyFactoryClass, "getProxyClass", new Class[] {});
					}
				}
			}

		} catch (Exception e) {
			LOGGER.error("recreateProxyFactory() exception {}.", e, e.getMessage());
		} finally {
			Thread.currentThread().setContextClassLoader(oldContextClassLoader);
			ProxyClassLoadingDelegate.endProxyRegeneration();
		}
	}

}
