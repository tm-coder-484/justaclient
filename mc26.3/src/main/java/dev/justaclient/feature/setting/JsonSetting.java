package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Structured data a feature keeps in its profile and edits on its own screen, such as the look of every biome. The
 * feature owns the data: this only hands it to the profile when saving and back when loading. It never shows a control.
 */
public final class JsonSetting extends Setting<JsonObject> {
	private final Supplier<JsonObject> write;
	private final Consumer<JsonObject> read;
	private final Runnable defaults;

	public JsonSetting(String id, String name, Supplier<JsonObject> write, Consumer<JsonObject> read, Runnable defaults) {
		super(id, name, new JsonObject());
		this.write = write;
		this.read = read;
		this.defaults = defaults;
		visibleWhen(() -> false);
	}

	@Override
	public JsonObject get() {
		return write.get();
	}

	@Override
	public void set(JsonObject value) {
		read.accept(value);
	}

	@Override
	public void reset() {
		defaults.run();
	}

	@Override
	public JsonElement save() {
		return write.get();
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonObject()) {
			read.accept(json.getAsJsonObject());
		}
	}
}
