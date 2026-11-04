package dev.justaclient.feature;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.hud.FrameCost;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Something the player can switch on in the click GUI. Every feature is visual only: it may change how things are drawn,
 * never what the player can know or do.
 */
public abstract class Feature {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final Icons icon;
	/** Toggles the feature from anywhere in game. Every feature gets one, unbound by default. */
	public final KeySetting toggleKey = new KeySetting("toggle_key", "Toggle key", KeySetting.NONE);
	/** Where this feature's render and tick time is booked for the Performance page. */
	public final FrameCost.Section cost;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final boolean enabledByDefault;
	private boolean enabled;
	/** Whether {@link #onEnable} has run without a matching {@link #onDisable}. Starts in step with the default state. */
	private boolean hooked;
	private boolean experimental;

	protected Feature(String id, String name, String description, Category category, Icons icon, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.cost = FrameCost.section(name);
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
		this.hooked = enabledByDefault;
	}

	/** Marks the feature as experimental: it is listed on the Labs screen and shows a Labs badge. */
	protected final void markExperimental() {
		experimental = true;
	}

	public boolean isExperimental() {
		return experimental;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean enabledByDefault() {
		return enabledByDefault;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		syncHooks();
	}

	/**
	 * Runs {@link #onEnable} or {@link #onDisable} if the state differs from the last hook that ran, so every path that
	 * changes the state (toggle, reset, profile switch) keeps side effects such as a window mode or a worker in step.
	 */
	private void syncHooks() {
		if (enabled && !hooked) {
			hooked = true;
			onEnable();
		} else if (!enabled && hooked) {
			hooked = false;
			onDisable();
		}
	}

	/**
	 * Replaces this feature's state: turns it off with its old settings, lets {@code load} reset or load the settings, then
	 * turns it on with the new ones if {@code wanted} is true.
	 */
	public void replaceState(boolean wanted, Runnable load) {
		boolean cycle = hooked;
		if (cycle) {
			enabled = false;
			syncHooks();
		}
		load.run();
		enabled = wanted;
		syncHooks();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick while enabled. */
	public void tick() {
	}

	/** The feature's own settings, in display order. The toggle key is separate and always last in the GUI. */
	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	/** Draws the picture at the top of the feature's settings. The default is the feature's icon on an accent wash. */
	public void drawPreview(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		int accent = JacConfig.accent();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		Draw.gradient(g, x, y, w, h, radius, Colors.withAlpha(accent, 0x55), Colors.withAlpha(accent, 0x30), Colors.withAlpha(accent, 0x10),
			Colors.withAlpha(accent, 0x30));
		float size = Math.min(w, h) * 0.42f;
		JacText.icon(g, icon, x + (w - size) / 2f, y + (h - size) / 2f, size, 0xFFFFFFFF);
	}

	public void resetToDefaults() {
		replaceState(enabledByDefault, this::resetSettings);
	}

	/** Puts the settings and toggle key back to their defaults without changing whether the feature is on. */
	public void resetSettings() {
		settings().forEach(Setting::reset);
		toggleKey.reset();
	}

	/** True if the query matches the name, description, category or any setting name. */
	public boolean matches(String query) {
		String q = query.toLowerCase(Locale.ROOT).trim();
		if (q.isEmpty()) {
			return true;
		}
		if (name.toLowerCase(Locale.ROOT).contains(q) || description.toLowerCase(Locale.ROOT).contains(q)
			|| category.label.toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		return settings.stream().anyMatch(s -> s.name.toLowerCase(Locale.ROOT).contains(q));
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	protected BoolSetting bool(String id, String name, boolean value) {
		return add(new BoolSetting(id, name, value));
	}

	protected NumberSetting number(String id, String name, double value, double min, double max, double step, DoubleFunction<String> format) {
		return add(new NumberSetting(id, name, value, min, max, step, format));
	}

	protected <E extends Enum<E> & ChoiceSetting.Named> ChoiceSetting<E> choice(String id, String name, E value) {
		return add(new ChoiceSetting<>(id, name, value));
	}

	protected ColorSetting color(String id, String name, String value) {
		return add(new ColorSetting(id, name, value));
	}

	protected KeySetting key(String id, String name, int key) {
		return add(new KeySetting(id, name, key));
	}
}
