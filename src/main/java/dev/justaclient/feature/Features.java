package dev.justaclient.feature;

import dev.justaclient.feature.impl.BlockHighlightFeature;
import dev.justaclient.feature.impl.CopyCoordinatesFeature;
import dev.justaclient.feature.impl.CrosshairFeature;
import dev.justaclient.feature.impl.FireOverlayFeature;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.hud.widgets.ArmorHud;
import dev.justaclient.hud.widgets.CooldownsHud;
import dev.justaclient.hud.widgets.CoordinatesHud;
import dev.justaclient.hud.widgets.NotificationsHud;
import dev.justaclient.hud.widgets.PlayerInfoHud;
import dev.justaclient.hud.widgets.PotionsHud;
import dev.justaclient.profile.Profiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Every feature in the client, in the order the GUI lists them. */
public final class Features {
	private static final List<Feature> ALL = new ArrayList<>();
	private static final Map<Class<?>, Feature> BY_TYPE = new HashMap<>();
	private static final Map<Feature, Boolean> KEY_WAS_DOWN = new HashMap<>();

	private Features() {
	}

	public static void init() {
		register(new PlayerInfoHud());
		register(new CoordinatesHud());
		register(new ArmorHud());
		register(new PotionsHud());
		register(new CooldownsHud());
		register(new NotificationsHud());
		register(new CrosshairFeature());
		register(new FireOverlayFeature());
		register(new BlockHighlightFeature());
		register(new ZoomFeature());
		register(new CopyCoordinatesFeature());
	}

	private static void register(Feature feature) {
		ALL.add(feature);
		BY_TYPE.put(feature.getClass(), feature);
	}

	public static List<Feature> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<Feature> in(Category category) {
		return ALL.stream().filter(f -> f.category == category).toList();
	}

	public static List<HudFeature> hud() {
		return ALL.stream().filter(f -> f instanceof HudFeature).map(f -> (HudFeature) f).toList();
	}

	@SuppressWarnings("unchecked")
	public static <T extends Feature> T get(Class<T> type) {
		return (T) BY_TYPE.get(type);
	}

	/** Runs feature ticks and toggle keys. Keys only fire in game, not while a menu is open. */
	public static void tick(Minecraft client) {
		for (Feature feature : ALL) {
			boolean down = client.screen == null && feature.toggleKey.isDown();
			boolean was = KEY_WAS_DOWN.getOrDefault(feature, false);
			KEY_WAS_DOWN.put(feature, down);
			if (down && !was) {
				feature.toggle();
				Notifications.toggled(feature);
				Profiles.save();
			}
			if (feature.isEnabled()) {
				feature.tick();
			}
		}
	}
}
