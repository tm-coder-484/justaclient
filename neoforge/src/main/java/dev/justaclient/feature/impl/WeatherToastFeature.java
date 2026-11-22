package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** A toast when rain or a thunderstorm starts or clears. You would see and hear it anyway; this just says so. */
public final class WeatherToastFeature extends Feature {
	private final BoolSetting clearing = bool("clearing", "Also when it clears", true);
	private int last = -1;
	private ClientLevel lastLevel;

	public WeatherToastFeature() {
		super("weather_toast", "Weather toasts", "A toast when rain or thunder starts", Category.UTILITY, Icons.CLOUD_FOG, false);
	}

	@Override
	public void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level != lastLevel) {
			lastLevel = level;
			last = -1;
			return;
		}
		// 0 clear, 1 rain, 2 thunder. Use the eased levels with a margin so the fade in and out does not flicker.
		int now = level.getThunderLevel(1f) > 0.6f ? 2 : level.getRainLevel(1f) > 0.6f ? 1 : level.getRainLevel(1f) < 0.1f ? 0 : last;
		if (last >= 0 && now != last) {
			switch (now) {
				case 2 -> Notifications.push(Icons.ZAP, "Thunderstorm", "rolling in");
				case 1 -> Notifications.push(Icons.CLOUD_FOG, "Rain", last == 2 ? "the thunder stopped" : "started");
				default -> {
					if (clearing.on()) {
						Notifications.push(Icons.SUN, "Weather", "cleared up");
					}
				}
			}
		}
		last = now;
	}
}
