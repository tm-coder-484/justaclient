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

	/** True while the key is physically held. */
	public boolean isDown() {
		return isBound() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), value);
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
