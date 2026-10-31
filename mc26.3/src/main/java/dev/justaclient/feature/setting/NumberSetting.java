package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.DoubleFunction;
import org.jspecify.annotations.Nullable;

/** A number on a slider, snapped to {@code step} and kept inside {@code min..max}. */
public final class NumberSetting extends Setting<Double> {
	public final double min;
	public final double max;
	public final double step;
	public final DoubleFunction<String> format;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step, DoubleFunction<String> format) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
	}

	/** Whole numbers with a unit suffix, e.g. "3 px". */
	public static DoubleFunction<String> unit(String suffix) {
		return v -> Math.round(v) + suffix;
	}

	public static DoubleFunction<String> decimal(String suffix) {
		return v -> String.format("%.1f%s", v, suffix);
	}

	public static DoubleFunction<String> percent() {
		return v -> Math.round(v * 100) + "%";
	}

	public float f() {
		return value.floatValue();
	}

	public int i() {
		return (int) Math.round(value);
	}

	@Override
	public void set(Double value) {
		super.set(Math.max(min, Math.min(max, value)));
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
