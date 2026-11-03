package dev.justaclient.common.mods;

/** What switching a mod did. {@code error} is set when it failed. */
public record ToggleResult(Outcome outcome, String error) {
	public enum Outcome {
		RENAMED, RESTART_REQUIRED, UNDONE, DEFERRED, FAILED
	}

	static ToggleResult renamed() {
		return new ToggleResult(Outcome.RENAMED, null);
	}

	static ToggleResult restartRequired() {
		return new ToggleResult(Outcome.RESTART_REQUIRED, null);
	}

	static ToggleResult undone() {
		return new ToggleResult(Outcome.UNDONE, null);
	}

	static ToggleResult deferred() {
		return new ToggleResult(Outcome.DEFERRED, null);
	}

	static ToggleResult failed(String error) {
		return new ToggleResult(Outcome.FAILED, error);
	}
}
