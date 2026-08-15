package ru.gasada.chatresponder;

public final class ServerLookupCoordinator {
	private Object owner;

	public boolean tryAcquire(Object candidate) {
		if (candidate == null) return false;
		if (owner == null || owner == candidate) {
			owner = candidate;
			return true;
		}
		return false;
	}

	public void release(Object candidate) {
		if (owner == candidate) owner = null;
	}

	public void reset() {
		owner = null;
	}
}
