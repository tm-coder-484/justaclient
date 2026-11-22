package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Icons;

/**
 * Copies the active profile and theme to {@code config/justaclient/backups/} on launch and every so often while it
 * changes, keeping the newest few. Restore one from the profile menu's "Import from file".
 */
public final class ProfileBackupFeature extends Feature {
	private final BoolSetting onLaunch = bool("on_launch", "Back up on launch", true);
	private final NumberSetting every = number("every", "Back up every", 30, 0, 120, 5, v -> v == 0 ? "Off" : (int) v + " min");
	private final NumberSetting keep = number("keep", "Backups kept", 20, 3, 50, 1, NumberSetting.unit(""));
	private int lastPrint;
	private long lastBackup = System.currentTimeMillis();

	public ProfileBackupFeature() {
		super("profile_backup", "Auto backup", "Keeps rotating copies of your profile and theme in config/justaclient/backups", Category.TOOLS,
			Icons.DOWNLOAD, true);
	}

	public int keep() {
		return keep.i();
	}

	/** Called once the profile is loaded at start-up. */
	public void launched() {
		lastPrint = Profiles.fingerprint();
		if (isEnabled() && onLaunch.on()) {
			Profiles.backup("launch");
		}
		lastBackup = System.currentTimeMillis();
	}

	@Override
	public void tick() {
		if (every.i() == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBackup < every.i() * 60_000L) {
			return;
		}
		lastBackup = now;
		int print = Profiles.fingerprint();
		if (print != lastPrint) {
			lastPrint = print;
			Profiles.backup("auto");
		}
	}
}
