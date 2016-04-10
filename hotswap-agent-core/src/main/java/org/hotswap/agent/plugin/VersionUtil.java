package org.hotswap.agent.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

public class VersionUtil {

    public static void dumpVersion(String packAge, ClassLoader cl) {
        System.err.println("\n\n\n\n\n\n\n\n\n\\n\nOEEEEEO: " +packAge + " ------->" +  getVersion(packAge, cl));
    }
    public static String getVersion(String packAge, ClassLoader cl) {
        Package p = Package.getPackage(packAge);
        if(p != null && p.getImplementationVersion() != null) {
            return p.getImplementationVersion();
        }
        return getVersion(cl);
    }
    //Implementation-Vendor-Id: org.wildfly
    //Bundle-SymbolicName: org.apache.myfaces.core.impl
/*
Specification-Version: 3.1

Implementation-Version: 3.1.0
Implementation-Vendor: GlassFish Community
Implementation-Vendor-Id: org.glassfish

Specification-Vendor: Oracle Corporation

Bundle-Name: Java Servlet API
Bundle-Vendor: GlassFish Community
Bundle-Version: 2.2.9
Bundle-SymbolicName: javax.servlet-api

Specification-Title: Hibernate Entity Manager
Specification-Version: 3.6.10.Final
Specification-Vendor: Hibernate.org

Implementation-Title: Hibernate Entity Manager
Implementation-Title: Hibernate Core
Implementation-Version: 3.6.10.Final
Implementation-Vendor-Id: org.hibernate
Implementation-Vendor: Hibernate.org
Implementation-URL: http://hibernate.org

 */
    private static String getVersion(ClassLoader cl) {

        try (InputStream is = cl.getResourceAsStream("META-INF/MANIFEST.MF")) {
            Manifest man = new Manifest(is);

            String path = "";
            String sealed = null;
            String specTitle= null;
            String specVersion= null;
            String specVendor= null;
            String implTitle= null;
            String implVersion= null;
            String implVendor= null;
            Attributes attr = man.getAttributes(path);
            if (attr != null) {
                specTitle   = attr.getValue(Name.SPECIFICATION_TITLE);
                specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
                specVendor  = attr.getValue(Name.SPECIFICATION_VENDOR);
                implTitle   = attr.getValue(Name.IMPLEMENTATION_TITLE);
                implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
                implVendor  = attr.getValue(Name.IMPLEMENTATION_VENDOR);
                sealed      = attr.getValue(Name.SEALED);
            }
            attr = man.getMainAttributes();
            if (attr != null) {
                if (specTitle == null) {
                    specTitle = attr.getValue(Name.SPECIFICATION_TITLE);
                }
                if (specVersion == null) {
                    specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
                }
                if (specVendor == null) {
                    specVendor = attr.getValue(Name.SPECIFICATION_VENDOR);
                }
                if (implTitle == null) {
                    implTitle = attr.getValue(Name.IMPLEMENTATION_TITLE);
                }
                if (implVersion == null) {
                    implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
                }
                if (implVendor == null) {
                    implVendor = attr.getValue(Name.IMPLEMENTATION_VENDOR);
                }
                if (sealed == null) {
                    sealed = attr.getValue(Name.SEALED);
                }
            }
            System.err.println("specTitle:" + specTitle +",specVersion:" +specVersion + ",specVendor:" + specVendor +",implTitle:" +implTitle +",implVersion:"+implVersion +",implVendor:" +implVendor);
            return implVersion;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}
