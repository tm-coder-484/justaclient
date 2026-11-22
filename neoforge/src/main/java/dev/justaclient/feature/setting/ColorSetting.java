package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A colour that is either the theme accent (the default for anything with a colour, so the client stays theme-neutral)
 * or a fixed #RRGGBB.
 */
public final class ColorSetting extends Setting<String> {
	public static final String ACCENT = "accent";
	/** Presets offered next to the accent swatch. */
	public static final List<String> PRESETS = List.of(ACCENT, "#FFFFFF", "#F43F5E", "#F59E0B", "#22C55E", "#14B8A6", "#38BDF8", "#8B5CF6");

	public ColorSetting(String id, String name, String defaultValue) {
		super(id, name, defaultValue);
	}

	public static int resolve(String value) {
		return ACCENT.equals(value) ? JacConfig.accent() : Colors.parse(value, 0xFFFFFFFF);
	}

	/** The colour as ARGB, following the live accent when set to it. */
	public int argb() {
		return resolve(value);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			String s = json.getAsString();
			if (ACCENT.equals(s) || s.matches("#[0-9A-Fa-f]{6}")) {
				value = s;
			}
		}
	}
}
