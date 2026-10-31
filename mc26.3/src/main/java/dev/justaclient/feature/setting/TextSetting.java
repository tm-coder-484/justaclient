package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/** A short line of text, such as a display name or a list of names separated by commas. */
public final class TextSetting extends Setting<String> {
	public final int maxLength;
	public final String placeholder;

	public TextSetting(String id, String name, String defaultValue, int maxLength) {
		this(id, name, defaultValue, maxLength, "");
	}

	public TextSetting(String id, String name, String defaultValue, int maxLength, String placeholder) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
		this.placeholder = placeholder;
	}

	@Override
	public void set(String value) {
		String v = value == null ? "" : value;
		super.set(v.length() > maxLength ? v.substring(0, maxLength) : v);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
