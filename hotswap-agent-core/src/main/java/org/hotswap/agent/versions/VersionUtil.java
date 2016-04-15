package org.hotswap.agent.versions;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

/*
 * Extension-Name : javax.el
 * 
 */
// Implementation-Vendor-Id: org.wildfly
// Bundle-SymbolicName: org.apache.myfaces.core.impl
/*
 * Specification-Version: 3.1
 * 
 * Implementation-Version: 3.1.0 Implementation-Vendor: GlassFish Community
 * Implementation-Vendor-Id: org.glassfish
 * 
 * Specification-Vendor: Oracle Corporation
 * 
 * Bundle-Name: Java Servlet API Bundle-Vendor: GlassFish Community
 * Bundle-Version: 2.2.9 Bundle-SymbolicName: javax.servlet-api
 * 
 * Specification-Title: Hibernate Entity Manager Specification-Version:
 * 3.6.10.Final Specification-Vendor: Hibernate.org
 * 
 * Implementation-Title: Hibernate Entity Manager Implementation-Title:
 * Hibernate Core Implementation-Version: 3.6.10.Final
 * Implementation-Vendor-Id: org.hibernate Implementation-Vendor:
 * Hibernate.org Implementation-URL: http://hibernate.org
 * 
 */

/*
 * Extension-Name : javax.el
 * 
 */
// Implementation-Vendor-Id: org.wildfly
// Bundle-SymbolicName: org.apache.myfaces.core.impl
/*
 * Specification-Version: 3.1
 * 
 * Implementation-Version: 3.1.0 
 * Implementation-Vendor: GlassFish Community
 * Implementation-Vendor-Id: org.glassfish
 * 
 * Specification-Vendor: Oracle Corporation
 * 
 * Bundle-Name: Java Servlet API 
 * Bundle-Vendor: GlassFish Community
 * Bundle-Version: 2.2.9 
 * Bundle-SymbolicName: javax.servlet-api
 * 
 * Specification-Title: Hibernate Entity Manager 
 * Specification-Version: 3.6.10.Final 
 * Specification-Vendor: Hibernate.org
 * 
 * Implementation-Title: Hibernate Entity Manager 
 * Implementation-Title: Hibernate Core 
 * Implementation-Version: 3.6.10.Final
 * Implementation-Vendor-Id: org.hibernate 
 * Implementation-Vendor: Hibernate.org 
 * Implementation-URL: http://hibernate.org
 * 
 */


public class VersionUtil {

    /**
     * <code>Name</code> object for <code>Extension-List</code> manifest
     * attribute used for declaring dependencies on installed extensions.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/extensions/spec.html#dependency">
     *      Installed extension dependency</a>
     */
    public static final Name EXTENSION_LIST = new Name("Extension-List");

    /**
     * <code>Name</code> object for <code>Extension-Name</code> manifest
     * attribute used for declaring dependencies on installed extensions.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/extensions/spec.html#dependency">
     *      Installed extension dependency</a>
     */
    public static final Name EXTENSION_NAME = new Name("Extension-Name");

    /**
     * <code>Name</code> object for <code>Implementation-Title</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_TITLE = new Name(
            "Implementation-Title");

    /**
     * <code>Name</code> object for <code>Implementation-Version</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_VERSION = new Name(
            "Implementation-Version");

    /**
     * <code>Name</code> object for <code>Implementation-Vendor</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_VENDOR = new Name(
            "Implementation-Vendor");

    /**
     * <code>Name</code> object for <code>Implementation-Vendor-Id</code>
     * manifest attribute used for package versioning. Extension mechanism will
     * be removed in a future release. Use class path instead.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/extensions/versioning.html#applet">
     *      Optional Package Versioning</a>
     */
    public static final Name IMPLEMENTATION_VENDOR_ID = new Name(
            "Implementation-Vendor-Id");

    /**
     * <code>Name</code> object for <code>Specification-Version</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_VERSION = new Name(
            "Specification-Version");

    /**
     * <code>Name</code> object for <code>Specification-Vendor</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_VENDOR = new Name(
            "Specification-Vendor");

    /**
     * <code>Name</code> object for <code>Specification-Title</code> manifest
     * attribute used for package versioning.
     * 
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_TITLE = new Name(
            "Specification-Title");

    //Bundle-SymbolicName: javax.servlet-api
    public static final Name BUNDLE_SYMBOLIC_NAME = new Name(
            "Bundle-SymbolicName");

    // Bundle-Name: Java Servlet API
    public static final Name BUNDLE_NAME = new Name("Bundle-Name");

    // Bundle-Version: 2.2.9
    public static final Name BUNDLE_VERSION = new Name("Bundle-Version");

    public static final Name[] VERSIONS = new Name[] { BUNDLE_VERSION,
            IMPLEMENTATION_VERSION, SPECIFICATION_VENDOR };
    public static final Name[] PACKAGE = new Name[] { BUNDLE_SYMBOLIC_NAME,
            IMPLEMENTATION_VENDOR_ID, SPECIFICATION_VENDOR };
    public static final Name[] TITLE = new Name[] { BUNDLE_NAME,
            IMPLEMENTATION_TITLE, SPECIFICATION_VENDOR };

    public static void dumpVersion(ClassLoader cl, Class<?> plugin, String p) {
    	ArtifactVersion vi = getVersion(cl);
        VersionRange r = VersionRange.createFromVersion("1");
        if(vi.getVersion() != null) {
            try {
                r = VersionRange.createFromVersionSpec(vi.getVersion());
            } catch (InvalidVersionSpecificationException e) {
                e.printStackTrace();
            }
        } else {
            try {
                r = VersionRange.createFromVersionSpec("0");
            } catch (InvalidVersionSpecificationException e) {
                e.printStackTrace();
            }
        }
        System.err.println(vi.dump() +  "::" + r + "--" + r.containsVersion(vi) +"//" + r.getRestrictions() +"--->" + plugin + "--->" + p +"--> CL:" + cl);
    }

    private static String getAttribute(Attributes attr, Attributes main,
            Name[] names) {
        if (attr != null) {
            String value;
            for (Name name : names) {
                value = attr.getValue(name);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        if (main != null) {
            String value;
            for (Name name : names) {
                value = main.getValue(name);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    public static ArtifactVersion getVersion(ClassLoader cl) {

        try (InputStream is = cl.getResourceAsStream("META-INF/MANIFEST.MF")) {
            Manifest man = new Manifest(is);

            String path = "";
            Attributes attr = man.getAttributes(path);
            Attributes main = man.getMainAttributes();

            return new ArtifactVersion(getAttribute(attr, main, VERSIONS),
                    getAttribute(attr, main, PACKAGE),
                    getAttribute(attr, main, TITLE));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ArtifactVersion("", "", "");
    }
    
    
}
