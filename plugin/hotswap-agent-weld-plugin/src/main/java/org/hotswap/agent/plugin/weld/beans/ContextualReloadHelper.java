package org.hotswap.agent.plugin.weld.beans;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;

import org.hotswap.agent.logging.AgentLogger;
import org.jboss.weld.bean.ManagedBean;

public class ContextualReloadHelper {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ContextualReloadHelper.class);

    public static void reload(HotSwappingContext ctx) {
        Set<Contextual<Object>> beans = ctx.getBeans();
        
        if (beans.size() > 0) {
            LOGGER.debug("Starting re-loading Contextuals in {}, {}", ctx, beans.size());

            Iterator<Contextual<Object>> it = beans.iterator();
            while (it.hasNext()) {
                Contextual<Object> managedBean = it.next();
                destroy(ctx, managedBean);
            }
            beans.clear();
            LOGGER.debug("Finished re-loading Contextuals in {}", ctx);
        }
    }
    
    public static void addBean(Contextual<Object> bean, HotSwappingContext ctx) {
        ctx.addBean(bean);
    }
    
    
    /**
     * Tries to add the bean in the context so it is reloaded in the next activation of the context.
     * 
     * @param ctx
     * @param managedBean
     * @return
     */
    public static boolean addToReloadSet(Context ctx,  Contextual<Object> managedBean)  {
        try {
            LOGGER.debug("Adding XXXXXXXXXXXXX in '{}' : {}", ctx.getClass(), managedBean);
            Field toRedefine = ctx.getClass().getField("_toRelaod");
            Set.class.cast(toRedefine.get(ctx)).add(managedBean);
            return true;
        } catch(Exception e) {
            LOGGER.warning("Context {} is not patched. Can not add {} to reload set", e, ctx, managedBean);
        }
        return false;
    }
    
    /**
     * Will remove bean from context forcing a clean new instance to be created (eg calling post-construct)
     * 
     * @param ctx
     * @param managedBean
     */
    static void destroy(HotSwappingContext ctx, Contextual<?> managedBean ) {
        try {
            LOGGER.debug("Removing Contextual from Context........ {},: {}", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                ctx.destroy(managedBean);
            }
            get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.error("Error removing ManagedBean {}, it still exists as instance {} ", managedBean, get);
                ctx.destroy(managedBean);
            }
        } catch (Exception e) {
            LOGGER.error("Error destoying bean {},: {}", e, managedBean, ctx);
        }
    }
    
    /**
     * Will re-inject any managed beans in the target. Will not call any other life-cycle methods
     * 
     * @param ctx
     * @param managedBean
     */
    static void reinitialize(Context ctx, Contextual<Object> contextual) {
        try {
            ManagedBean<Object> managedBean = ManagedBean.class.cast(contextual);
            LOGGER.debug("Re-Initializing........ {},: {}", managedBean, ctx);
            Object get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.debug("Bean injection points are reinitialized '{}'", managedBean);
                managedBean.getProducer().inject(get, managedBean.getBeanManager().createCreationalContext(managedBean));
            }
        } catch (Exception e) {
            LOGGER.error("Error reinitializing bean {},: {}", e, contextual, ctx);
        }
    }
}
