package dev.justaclient.common;

/** A pausable timer, for the session timer widget. Plain Java, shared by both Minecraft versions. */
public final class Stopwatch {
	private long startedAt = -1;
	private long banked;

	public boolean running() {
		return startedAt >= 0;
	}

	public void start(long nowMillis) {
		if (startedAt < 0) {
			startedAt = nowMillis;
		}
	}

	public void pause(long nowMillis) {
		if (startedAt >= 0) {
			banked += nowMillis - startedAt;
			startedAt = -1;
		}
	}

	public void toggle(long nowMillis) {
		if (running()) {
			pause(nowMillis);
		} else {
			start(nowMillis);
		}
	}

	public void reset(long nowMillis) {
		banked = 0;
		startedAt = running() ? nowMillis : -1;
	}

	public long elapsedMillis(long nowMillis) {
		return banked + (startedAt >= 0 ? nowMillis - startedAt : 0);
	}
}
