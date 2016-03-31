package org.hotswap.agent.watch.nio;

import java.nio.file.Path;

public class PathPair {
	private final Path target;
	private final Path watched;
	
	public static PathPair get(Path target) {
		return new PathPair(target);
	}
	
	public static PathPair get(Path target, Path watched) {
		return new PathPair(target, watched);
	}
	
	public PathPair(Path target) {
		this(target, target);
	}
	
	public PathPair(Path target, Path watched) {
		this.target = target;
		this.watched = watched;
	}

	public Path getWatched() {
		return watched;
	}

	public Path getTarget() {
		return target;
	}
	
	
	public Path resolve(Path other) {
		return watched.resolve(other);
	}

	public boolean isWatching(Path target){
		return target.startsWith(watched);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((watched == null) ? 0 : watched.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathPair other = (PathPair) obj;
		if (watched == null) {
			if (other.watched != null)
				return false;
		} else if (!watched.equals(other.watched))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PathPair [target=" + target + ", watched=" + watched + "]";
	}
}
