package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/** Shows a time of day of your choice. Only your own sky and lighting change; the server's clock carries on. */
public final class TimeFeature extends Feature {
	public enum Preset implements ChoiceSetting.Named {
		SUNRISE("Sunrise", 23200), MORNING("Morning", 1000), NOON("Noon", 6000), SUNSET("Sunset", 12500), NIGHT("Night", 14500),
		MIDNIGHT("Midnight", 18000), CUSTOM("Custom", -1);

		private final String label;
		final int ticks;

		Preset(String label, int ticks) {
			this.label = label;
			this.ticks = ticks;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Preset> preset = choice("preset", "Time", Preset.SUNSET);
	private final NumberSetting custom = number("custom", "Custom time", 6000, 0, 23999, 100, v -> clock((long) v)).visibleWhen(
		() -> preset.get() == Preset.CUSTOM);
	private final NumberSetting moon = number("moon", "Moon phase", 0, 0, 7, 1, v -> new String[] {"Full", "Waning gibbous", "Last quarter",
		"Waning crescent", "New", "Waxing crescent", "First quarter", "Waxing gibbous"}[(int) v]);

	public TimeFeature() {
		super("time", "Time of day", "Pick the time your sky shows; the server keeps its own", Category.WORLD, Icons.CLOCK, false);
	}

	private static String clock(long ticks) {
		long minutes = ((ticks + 6000) % 24000) * 60 / 1000;
		return String.format("%02d:%02d", minutes / 60, minutes % 60);
	}

	/** The day time to show instead of {@code real}, keeping the day count so the moon phase setting works. */
	public static boolean active() {
		TimeFeature f = Features.get(TimeFeature.class);
		return f != null && f.isEnabled();
	}

	public static long dayTime(long real) {
		TimeFeature f = Features.get(TimeFeature.class);
		if (f == null || !f.isEnabled()) {
			return real;
		}
		long ticks = f.preset.get() == Preset.CUSTOM ? (long) f.custom.get().doubleValue() : f.preset.get().ticks;
		return (long) f.moon.get().doubleValue() * 24000L + ticks;
	}
}
