package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * One player-editable value on a feature. Settings know how to save themselves to a profile and which control the click
 * GUI should draw for them.
 */
public abstract class Setting<T> {
	public final String id;
	public final String name;
	protected final T defaultValue;
	protected T value;
	private BooleanSupplier visible = () -> true;

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		value = defaultValue;
	}

	/** Hides the control unless {@code condition} holds, e.g. a sub-option of a toggle. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return (S) this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	public abstract JsonElement save();

	/** Reads a saved value; anything malformed keeps the current value. */
	public abstract void load(@Nullable JsonElement json);
}
