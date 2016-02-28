package org.hotswap.agent.client;

import java.util.ArrayList;
import java.util.List;

public class Args {

	private List<String> match = new ArrayList<>();
	private boolean pause = false;
	private boolean verbose = false;
	
	private boolean help = false;

	public Args(String[] paramArrayOfString) throws IllegalArgumentException {
		int i = 0;

		if ((paramArrayOfString.length == 1) && ((paramArrayOfString[0].compareTo("-?") == 0) || (paramArrayOfString[0].compareTo("-help") == 0))) {
			System.err.println(" args: [-v(erbose)] -s(tart)|-p(ause) a b c... , where a,b,c etc is a list of strings to match");
			help = true;
			return;
		}

		for (i = 0; (i < paramArrayOfString.length); i++) {
			String str = paramArrayOfString[i];

			if (str.startsWith("-")) {
				for (int j = 1; j < str.length(); j++) {
					switch (str.charAt(j)) {
					case 'p':
						this.pause = true;
						break;
					case 's':
						this.pause = false;
						break;
					case 'v':
						this.verbose = true;
						break;						
					default:
						throw new IllegalArgumentException("illegal argument: " + paramArrayOfString[i]);
					}
				}
			} else {
				if(str != null && str.trim().length()>0) {
					match.add(str.trim());
				}
			}
		}
	}

	public List<String> getMatch() {
		return match;
	}

	public boolean isPause() {
		return pause;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public boolean isHelp() {
		return help;
	}
	
}
