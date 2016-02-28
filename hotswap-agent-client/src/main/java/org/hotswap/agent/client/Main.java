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

@SuppressWarnings("restriction")
public class Main {

	public static void main(String[] args) throws MalformedObjectNameException, AttributeNotFoundException,
			InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		
		Args arg = new Args(args);
		
		Arguments arguments = new Arguments(new String[] { "-lmvV" });
	

		if (arg.isHelp()) {
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

				int lvmid = integer.intValue();

				output.append(String.valueOf(lvmid));

				MonitoredVm vm = null;
				String vmidString = "//" + lvmid + "?mode=r";

				try {
					VmIdentifier id = new VmIdentifier(vmidString);
					vm = monitoredHost.getMonitoredVm(id, 0);
				} catch (URISyntaxException e) {
					assert false;
				} catch (Exception e) {
				} finally {
					if (vm == null) {
						continue;
					}
				}

				output.append(' ').append(MonitoredVmUtil.mainClass(vm, arguments.showLongPaths()));


				String mainArgs = MonitoredVmUtil.mainArgs(vm);
				if (mainArgs != null && mainArgs.length() > 0) {
					output.append(" ").append(mainArgs);
				}
				String jvmArgs = MonitoredVmUtil.jvmArgs(vm);
				if (jvmArgs != null && jvmArgs.length() > 0) {
					output.append(" ").append(jvmArgs);
				}
				String jvmFlags = MonitoredVmUtil.jvmFlags(vm);
				if (jvmFlags != null && jvmFlags.length() > 0) {
					output.append(" ").append(jvmFlags);
				}
				
				if(arg.isVerbose()) {
					System.out.println(output);
				}
				
				boolean proceed = arg.getMatch().size() > 0;
				
				monitoredHost.detach(vm);
				String cmd = output.toString();
				for(String p: arg.getMatch()) {
					if(!cmd.contains(p)) {
						proceed = false;
						break;
					}
				}
				if(proceed) {
					setPaused(lvmid, arg.isPause());
				}
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

	private static void setPaused(int lvmid, boolean value) {
		try {
			MBeanServerConnection mbx = getLocalMBeanServerConnectionStatic(lvmid);
			if (mbx != null) {
				ObjectName name = new ObjectName("org.hotswap.agent:type=Watcher");
				if (mbx.isRegistered(name)) {
					mbx.setAttribute(name, new Attribute("paused", value));
					System.out.println("Set paused to '" + mbx.getAttribute(name, "paused") + "' for lvmid:" + lvmid);
				}
			} else {
				System.err.println("Nulll");
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		//System.out.println("Loading " + agent + " into target VM ...");

		try {
			VirtualMachine.attach(pid).loadAgent(agent);
		} catch (Exception x) {
			throw new IOException(x.getMessage());
		}
	}

}
