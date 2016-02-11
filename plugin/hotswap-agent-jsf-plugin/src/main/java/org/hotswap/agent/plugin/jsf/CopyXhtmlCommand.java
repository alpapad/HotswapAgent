package org.hotswap.agent.plugin.jsf;

import java.net.URL;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;

public class CopyXhtmlCommand extends MergeableCommand{
	private static AgentLogger LOGGER = AgentLogger.getLogger(CopyXhtmlCommand.class);
	
	private final URL fileUrl;
	private final FileEvent evt;
	@SuppressWarnings("unused")
	private final ClassLoader appClassLoader;
	
	
	public CopyXhtmlCommand(URL sourceUrl, FileEvent evt, ClassLoader appClassLoader) {
		this.fileUrl = sourceUrl;
		this.evt = evt;
		this.appClassLoader = appClassLoader;
	}


	@Override
	public void executeCommand() {
		LOGGER.info("CP:{},{}", fileUrl, evt);
	}

}
