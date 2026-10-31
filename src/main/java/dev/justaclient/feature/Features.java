package dev.justaclient.feature;

import dev.justaclient.feature.impl.AmbientParticlesFeature;
import dev.justaclient.feature.impl.AutoReconnectFeature;
import dev.justaclient.feature.impl.BlockHighlightFeature;
import dev.justaclient.feature.impl.BossBarFeature;
import dev.justaclient.feature.impl.CameraFeature;
import dev.justaclient.feature.impl.ColorGradingFeature;
import dev.justaclient.feature.impl.CopyCoordinatesFeature;
import dev.justaclient.feature.impl.CosmeticsFeature;
import dev.justaclient.feature.impl.CrosshairFeature;
import dev.justaclient.feature.impl.DiscordPresenceFeature;
import dev.justaclient.feature.impl.FireOverlayFeature;
import dev.justaclient.feature.impl.FogFeature;
import dev.justaclient.feature.impl.EndCrystalFeature;
import dev.justaclient.feature.impl.HitboxesFeature;
import dev.justaclient.feature.impl.CleanWorldFeature;
import dev.justaclient.feature.impl.WaterColorFeature;
import dev.justaclient.feature.impl.LightColorFeature;
import dev.justaclient.feature.impl.TimeFeature;
import dev.justaclient.feature.impl.FriendsFeature;
import dev.justaclient.feature.impl.GlintFeature;
import dev.justaclient.feature.impl.HandFeature;
import dev.justaclient.feature.impl.HandGlowFeature;
import dev.justaclient.feature.impl.HitParticlesFeature;
import dev.justaclient.feature.impl.HitSoundsFeature;
import dev.justaclient.feature.impl.HotbarFeature;
import dev.justaclient.feature.impl.ItemOverlayFeature;
import dev.justaclient.feature.impl.JumpCirclesFeature;
import dev.justaclient.feature.impl.KillEffectsFeature;
import dev.justaclient.feature.impl.LoadingScreenFeature;
import dev.justaclient.feature.impl.LowHealthFeature;
import dev.justaclient.feature.impl.MotionBlurFeature;
import dev.justaclient.feature.impl.NameProtectFeature;
import dev.justaclient.feature.impl.OverlaysFeature;
import dev.justaclient.feature.impl.ScoreboardFeature;
import dev.justaclient.feature.impl.SkyFeature;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.impl.TargetMarkerFeature;
import dev.justaclient.feature.impl.TitleScreenFeature;
import dev.justaclient.feature.impl.ToggleSprintFeature;
import dev.justaclient.feature.impl.TotemPopFeature;
import dev.justaclient.feature.impl.TrailsFeature;
import dev.justaclient.feature.impl.WaypointsFeature;
import dev.justaclient.feature.impl.WeatherToastFeature;
import dev.justaclient.feature.impl.WindowFeature;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.hud.widgets.ArmorHud;
import dev.justaclient.hud.widgets.ConsumablesHud;
import dev.justaclient.hud.widgets.CooldownsHud;
import dev.justaclient.hud.widgets.CoordinatesHud;
import dev.justaclient.hud.widgets.GpsHud;
import dev.justaclient.hud.widgets.NowPlayingHud;
import dev.justaclient.hud.widgets.InventoryHud;
import dev.justaclient.hud.widgets.KeybindsHud;
import dev.justaclient.hud.widgets.MinimapHud;
import dev.justaclient.hud.widgets.ModuleListHud;
import dev.justaclient.hud.widgets.NotificationsHud;
import dev.justaclient.hud.widgets.PlayerInfoHud;
import dev.justaclient.hud.widgets.PotionsHud;
import dev.justaclient.hud.widgets.SessionStatsHud;
import dev.justaclient.hud.widgets.TargetHud;
import dev.justaclient.hud.widgets.WatermarkHud;
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
		register(new TargetHud());
		register(new WatermarkHud());
		register(new ModuleListHud());
		register(new KeybindsHud());
		register(new InventoryHud());
		register(new ConsumablesHud());
		register(new MinimapHud());
		register(new GpsHud());
		register(new NowPlayingHud());
		register(new SessionStatsHud());
		register(new LowHealthFeature());
		register(new BossBarFeature());
		register(new ScoreboardFeature());
		register(new HotbarFeature());
		register(new ItemOverlayFeature());
		register(new CrosshairFeature());
		register(new HitParticlesFeature());
		register(new HitSoundsFeature());
		register(new KillEffectsFeature());
		register(new TotemPopFeature());
		register(new TargetMarkerFeature());
		register(new EndCrystalFeature());
		register(new HandFeature());
		register(new HandGlowFeature());
		register(new FireOverlayFeature());
		register(new OverlaysFeature());
		register(new BlockHighlightFeature());
		register(new SkyFeature());
		register(new FogFeature());
		register(new TimeFeature());
		register(new LightColorFeature());
		register(new WaterColorFeature());
		register(new CleanWorldFeature());
		register(new HitboxesFeature());
		register(new TrailsFeature());
		register(new JumpCirclesFeature());
		register(new ColorGradingFeature());
		register(new MotionBlurFeature());
		register(new GlintFeature());
		register(new AmbientParticlesFeature());
		register(new CosmeticsFeature());
		register(new ZoomFeature());
		register(new CameraFeature());
		register(new CopyCoordinatesFeature());
		register(new WaypointsFeature());
		register(new FriendsFeature());
		register(new StreamModeFeature());
		register(new ToggleSprintFeature());
		register(new AutoReconnectFeature());
		register(new DiscordPresenceFeature());
		register(new NameProtectFeature());
		register(new TitleScreenFeature());
		register(new LoadingScreenFeature());
		register(new WeatherToastFeature());
		register(new WindowFeature());
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
				feature.cost.begin();
				feature.tick();
				feature.cost.end();
			}
		}
	}
}
