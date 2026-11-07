package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Icons;

/**
 * Settings for sending feedback from inside the client. Off by default: nothing goes online until you switch it on.
 * The form itself is the Feedback page in the menu.
 */
public final class FeedbackFeature extends Feature {
	public final TextSetting server = add(new TextSetting("server", "Server address", "", 120, "https://justaclient.<you>.workers.dev"));
	public final BoolSetting anonymous = bool("anonymous", "Send anonymously", false);
	public final BoolSetting attachLog = bool("attach_log", "Attach the latest log", true);
	public final BoolSetting attachCrash = bool("attach_crash", "Attach the newest crash report", true);
	public final BoolSetting attachMods = bool("attach_mods", "Attach the mod list", true);
	public final BoolSetting attachSettings = bool("attach_settings", "Attach your settings", false);

	public FeedbackFeature() {
		super("feedback", "Feedback", "Send bug reports and ideas, with logs, from the menu's Feedback page", Category.TOOLS, Icons.FLAG, false);
	}
}
