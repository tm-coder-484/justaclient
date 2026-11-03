package dev.justaclient.common.mods;

/** Where a mod stands now and after the next launch. */
public enum ModStatus {
	ENABLED, DISABLED, DISABLE_PENDING, ENABLE_PENDING, LOCKED;

	public boolean activeNextLaunch() {
		return this == ENABLED || this == ENABLE_PENDING || this == LOCKED;
	}

	public boolean needsRestart() {
		return this == DISABLE_PENDING || this == ENABLE_PENDING;
	}
}
