package org.hotswap.agent.util.classloader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

public class ClassLoaderHelperTest {
    @Test
    public void testIsClassLoaded() throws Exception {
        ClassLoader testClassLoader = new URLClassLoader(new URL[] {});

        String className = "org.hotswap.agent.testData.SimplePlugin";
        assertFalse("Class not loaded", ClassLoaderHelper.isClassLoaded(testClassLoader, className));

        Class.forName(className, true, testClassLoader);

        assertTrue("Class loaded", ClassLoaderHelper.isClassLoaded(testClassLoader, className));

    }
}