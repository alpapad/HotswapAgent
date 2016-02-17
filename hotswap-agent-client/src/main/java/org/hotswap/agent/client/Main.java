package org.hotswap.agent.client;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.VirtualMachine;

import sun.jvmstat.monitor.HostIdentifier;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;
import sun.management.ConnectorAddressLink;
import sun.tools.jps.Arguments;

public class Main {

	public static void main(String[] args) throws MalformedObjectNameException, AttributeNotFoundException,
	InstanceNotFoundException, MBeanException, ReflectionException, IOException {
Arguments arguments;
try {
	arguments = new Arguments(args);
} catch (IllegalArgumentException e) {
	System.err.println(e.getMessage());
	Arguments.printUsage(System.err);
	return;
}

if (arguments.isHelp()) {
	Arguments.printUsage(System.out);
	System.exit(0);
}

try {
	HostIdentifier hostId = arguments.hostId();
	MonitoredHost monitoredHost = MonitoredHost.getMonitoredHost(hostId);

	// get the set active JVMs on the specified host.
	Set<Integer> jvms = monitoredHost.activeVms();

	for (Integer integer : jvms) {
		StringBuilder output = new StringBuilder();
		Throwable lastError = null;

		int lvmid = integer.intValue();

	
		output.append(String.valueOf(lvmid));

		if (arguments.isQuiet()) {
			System.out.println(output);
			continue;
		}

		MonitoredVm vm = null;
		String vmidString = "//" + lvmid + "?mode=r";

		try {
			VmIdentifier id = new VmIdentifier(vmidString);
			vm = monitoredHost.getMonitoredVm(id, 0);
		} catch (URISyntaxException e) {
			// unexpected as vmidString is based on a validated hostid
			lastError = e;
			assert false;
		} catch (Exception e) {
			lastError = e;
		} finally {
			if (vm == null) {
				/*
				 * we ignore most exceptions, as there are race
				 * conditions where a JVM in 'jvms' may terminate before
				 * we get a chance to list its information. Other
				 * errors, such as access and I/O exceptions should stop
				 * us from iterating over the complete set.
				 */
				output.append(" -- process information unavailable");
				if (arguments.isDebug()) {
					if (lastError != null && lastError.getMessage() != null) {
						output.append("\n\t");
						output.append(lastError.getMessage());
					}
				}
				System.out.println(output);
				if (arguments.printStackTrace()) {
					lastError.printStackTrace();
				}
				continue;
			}
		}

		output.append(" ");
		output.append(MonitoredVmUtil.mainClass(vm, arguments.showLongPaths()));

		if("jboss-modules.jar".equals(MonitoredVmUtil.mainClass(vm, false))){
			try {
				MBeanServerConnection mbx = getLocalMBeanServerConnectionStatic(lvmid);
				if (mbx != null) {
					ObjectName name = new ObjectName("org.hotswap.agent:type=Watcher");
					if (mbx.isRegistered(name)) {
						Boolean x = Boolean.class.cast(mbx.getAttribute(name, "paused"));
						System.err.println("================>" + x);
						mbx.setAttribute(name, new Attribute("paused", !x));
						System.err.println("================>" + mbx.getAttribute(name, "paused"));
					}
				} else {
					System.err.println("Nulll");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (arguments.showMainArgs()) {
			String mainArgs = MonitoredVmUtil.mainArgs(vm);
			if (mainArgs != null && mainArgs.length() > 0) {
				output.append(" ").append(mainArgs);
			}
		}
		if (arguments.showVmArgs()) {
			String jvmArgs = MonitoredVmUtil.jvmArgs(vm);
			if (jvmArgs != null && jvmArgs.length() > 0) {
				output.append(" ").append(jvmArgs);
			}
		}
		if (arguments.showVmFlags()) {
			String jvmFlags = MonitoredVmUtil.jvmFlags(vm);
			if (jvmFlags != null && jvmFlags.length() > 0) {
				output.append(" ").append(jvmFlags);
			}
		}

		System.out.println(output);

		monitoredHost.detach(vm);
	}
} catch (MonitorException e) {
	if (e.getMessage() != null) {
		System.err.println(e.getMessage());
	} else {
		Throwable cause = e.getCause();
		if (cause != null && cause.getMessage() != null) {
			System.err.println(cause.getMessage());
		} else {
			e.printStackTrace();
		}
	}
}
}

public static MBeanServerConnection getLocalMBeanServerConnectionStatic(int pid) {
try {
	String address = ConnectorAddressLink.importFrom(pid);
	if (address == null) {
		startManagementAgent(String.valueOf(pid));
	}
	address = ConnectorAddressLink.importFrom(pid);
	JMXServiceURL jmxUrl = new JMXServiceURL(address);
	MBeanServerConnection ctx = JMXConnectorFactory.connect(jmxUrl).getMBeanServerConnection();
	if (ctx == null) {
		startManagementAgent(String.valueOf(pid));
	} else {
		return ctx;
	}
	return JMXConnectorFactory.connect(jmxUrl).getMBeanServerConnection();
} catch (IOException e) {
	throw new RuntimeException("Of course you still have to implement a good connection handling");
}
}

private static void startManagementAgent(String pid) throws IOException {
/*
 * JAR file normally in ${java.home}/jre/lib but may be in
 * ${java.home}/lib with development/non-images builds
 */
String home = System.getProperty("java.home");
String agent = home + File.separator + "jre" + File.separator + "lib" + File.separator + "management-agent.jar";
File f = new File(agent);
if (!f.exists()) {
	agent = home + File.separator + "lib" + File.separator + "management-agent.jar";
	f = new File(agent);
	if (!f.exists()) {
		throw new RuntimeException("management-agent.jar missing");
	}
}
agent = f.getCanonicalPath();

System.out.println("Loading " + agent + " into target VM ...");

try {
	VirtualMachine.attach(pid).loadAgent(agent);
} catch (Exception x) {
	throw new IOException(x.getMessage());
}
}

}
