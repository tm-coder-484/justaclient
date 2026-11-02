package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** A keyboard key (GLFW key code), or none. */
public final class KeySetting extends Setting<Integer> {
	public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

	public KeySetting(String id, String name, int defaultKey) {
		super(id, name, defaultKey);
	}

	public boolean isBound() {
		return value != NONE;
	}

	/**
	 * True while the key is physically held. Ignored while F3 is held, so vanilla's F3 combinations (F3 + B for hitboxes,
	 * F3 + C, ...) don't also trigger a feature bound to the same letter.
	 */
	public boolean isDown() {
		if (!isBound()) {
			return false;
		}
		var window = Minecraft.getInstance().getWindow();
		if (value != InputConstants.KEY_F3 && InputConstants.isKeyDown(window, InputConstants.KEY_F3)) {
			return false;
		}
		return InputConstants.isKeyDown(window, value);
	}

	public String keyName() {
		return isBound() ? InputConstants.Type.KEYSYM.getOrCreate(value).getDisplayName().getString() : "None";
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = json.getAsInt();
		}
	}
}
