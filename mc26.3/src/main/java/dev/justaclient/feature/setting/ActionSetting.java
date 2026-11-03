package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import org.jspecify.annotations.Nullable;

/** A button in a feature's settings that opens a screen or runs something. It has no value and saves nothing. */
public final class ActionSetting extends Setting<Boolean> {
	public final Runnable action;

	public ActionSetting(String id, String name, Runnable action) {
		super(id, name, false);
		this.action = action;
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(@Nullable JsonElement json) {
	}
}
