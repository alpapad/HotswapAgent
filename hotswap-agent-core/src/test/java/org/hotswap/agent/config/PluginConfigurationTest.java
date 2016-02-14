package org.hotswap.agent.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

//import java.io.File;
//
//import static org.junit.Assert.assertEquals;

/**
 * Basic tests for configuration.
 *
 * @author Jiri Bubnik
 */
public class PluginConfigurationTest {

    @Test
    public void testGetWatchResources() throws Exception {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(getClass().getClassLoader());
        java.net.URL u = getClass().getClassLoader().getResource("haha");
        
//        File tempFile = File.createTempFile("test", "test");
        
        // find by URL
        //pluginConfiguration.base.setProperty("watchResources", tempFile.toURI().toURL().toString());
        assertEquals(u, pluginConfiguration.getWatchResources()[0]);

        // find by file name
        //pluginConfiguration.base.setProperty("watchResources", tempFile.getAbsolutePath());

        // On Mac OS X, 10.9.4, the temp folders use a path like "/var/..." and the canonical path is like "/private/var/..."
        // the getWatchResources() uses a getCanonicalFile() internally, so it returns "/private/var/...", so using
        // the cananicalFile as the expectation in the assertEquals to let this test succeed.  
        // Instead, could also change getWatchResources() to use getAbsouluteFile() instead of getCanonicalFile()?
        //File canonicalFile = tempFile.getCanonicalFile();
        //assertEquals(canonicalFile.toURI().toURL(), pluginConfiguration.getWatchResources()[0]);
    }
}
