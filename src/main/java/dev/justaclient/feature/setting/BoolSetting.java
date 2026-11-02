package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public boolean on() {
		return value;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			value = json.getAsBoolean();
		}
	}
}
