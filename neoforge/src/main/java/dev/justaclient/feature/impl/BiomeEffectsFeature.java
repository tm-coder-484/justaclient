package dev.justaclient.feature.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ActionSetting;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.JsonSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.Motes;
import dev.justaclient.render.WorldFx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Every biome can have its own look: ambient motes, a daylight tint, a fog colour and a quiet ambience, each with its own
 * switch. Walking from one biome to the next blends between them. It only changes colours, motes and sounds on your
 * screen; it never makes fog thinner or anything easier to see.
 */
public final class BiomeEffectsFeature extends Feature {
	public enum Particles implements ChoiceSetting.Named {
		KEEP("As set"), NONE("None"), FIREFLIES("Fireflies"), DRIFT("Drift"), LINKED("Linked");

		private final String label;

		Particles(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		AmbientParticlesFeature.@Nullable Style style() {
			return switch (this) {
				case FIREFLIES -> AmbientParticlesFeature.Style.FIREFLIES;
				case DRIFT -> AmbientParticlesFeature.Style.DRIFT;
				case LINKED -> AmbientParticlesFeature.Style.LINKED;
				default -> null;
			};
		}
	}

	public enum Ambience implements ChoiceSetting.Named {
		OFF("Off", null, true),
		HUM("Low hum", () -> SoundEvents.AMBIENT_NETHER_WASTES_LOOP.value(), true),
		CRIMSON("Crimson", () -> SoundEvents.AMBIENT_CRIMSON_FOREST_LOOP.value(), true),
		WARPED("Warped", () -> SoundEvents.AMBIENT_WARPED_FOREST_LOOP.value(), true),
		SOUL("Soul wind", () -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(), true),
		BASALT("Basalt", () -> SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), true),
		UNDERWATER("Deep water", () -> SoundEvents.AMBIENT_UNDERWATER_LOOP, true),
		FIREFLY("Fireflies", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(dev.justaclient.Justaclient.id("ambient.fireflies")), false),
		DRY_GRASS("Dry grass", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(dev.justaclient.Justaclient.id("ambient.dry_grass")), false),
		CAVE("Cave", () -> SoundEvents.AMBIENT_CAVE.value(), false);

		private final String label;
		final @Nullable Supplier<SoundEvent> sound;
		/** A loop plays all the time; the others play now and then. */
		final boolean loop;

		Ambience(String label, @Nullable Supplier<SoundEvent> sound, boolean loop) {
			this.label = label;
			this.sound = sound;
			this.loop = loop;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** One biome's look: its own settings, edited on the Biome effects screen and saved in the profile. */
	public static final class Look {
		public final BoolSetting on = new BoolSetting("on", "Use this look here", true);
		public final ChoiceSetting<Particles> particles = new ChoiceSetting<>("particles", "Particles", Particles.KEEP);
		public final ColorSetting particleColor = new ColorSetting("particle_color", "Particle colour", "#F59E0B")
			.visibleWhen(() -> particles.get().style() != null);
		public final NumberSetting amount = new NumberSetting("amount", "Particle amount", 60, 10, 200, 5, NumberSetting.unit(""))
			.visibleWhen(() -> particles.get().style() != null);
		public final BoolSetting tint = new BoolSetting("tint", "Change daylight colour", false);
		public final ColorSetting tintColor = new ColorSetting("tint_color", "Daylight colour", "#FFD9A0").visibleWhen(tint::on);
		public final NumberSetting tintStrength = new NumberSetting("tint_strength", "Tint strength", 0.35, 0.05, 1.0, 0.05, NumberSetting.percent())
			.visibleWhen(tint::on);
		public final BoolSetting fog = new BoolSetting("fog", "Change fog colour", false);
		public final ColorSetting fogColor = new ColorSetting("fog_color", "Fog colour", "#B8C8E8").visibleWhen(fog::on);
		public final NumberSetting fogStrength = new NumberSetting("fog_strength", "Fog colour strength", 0.5, 0.05, 1.0, 0.05, NumberSetting.percent())
			.visibleWhen(fog::on);
		public final ChoiceSetting<Ambience> ambience = new ChoiceSetting<>("ambience", "Ambience", Ambience.OFF);
		public final NumberSetting volume = new NumberSetting("volume", "Ambience volume", 0.4, 0.05, 1.0, 0.05, NumberSetting.percent())
			.visibleWhen(() -> ambience.get() != Ambience.OFF);
		private final List<Setting<?>> all = List.of(on, particles, particleColor, amount, tint, tintColor, tintStrength, fog, fogColor, fogStrength,
			ambience, volume);

		public List<Setting<?>> settings() {
			return all;
		}

		/** True if this look changes anything at all. */
		public boolean changesAnything() {
			return on.on() && (particles.get() != Particles.KEEP || tint.on() || fog.on() || ambience.get() != Ambience.OFF);
		}

		public JsonObject save() {
			JsonObject o = new JsonObject();
			for (Setting<?> s : all) {
				o.add(s.id, s.save());
			}
			return o;
		}

		public static Look load(JsonObject o) {
			Look look = new Look();
			for (Setting<?> s : look.all) {
				JsonElement e = o.get(s.id);
				s.load(e);
			}
			return look;
		}

		public void reset() {
			all.forEach(Setting::reset);
		}
	}

	private final NumberSetting blend = number("blend", "Blend time", 2.0, 0.2, 6.0, 0.2, NumberSetting.decimal(" s"));
	private final BoolSetting ambienceOn = bool("ambience", "Play ambience", true);
	private final ActionSetting edit = add(new ActionSetting("edit", "Edit biomes...",
		() -> Minecraft.getInstance().setScreen(new dev.justaclient.ui.BiomeEffectsScreen(Minecraft.getInstance().screen))));
	/** Biome id ("minecraft:swamp") to its look. Only biomes you changed are here. */
	private final Map<String, Look> looks = new LinkedHashMap<>();
	private final JsonSetting store = add(new JsonSetting("biomes", "Biomes", this::writeLooks, this::readLooks, this::presets));
	private final Motes motes = new Motes();
	private String here = "";
	private @Nullable Look target;
	/** Blended values: r, g, b, strength. */
	private final float[] fogNow = {0.72f, 0.78f, 0.9f, 0f};
	private final float[] tintNow = {1f, 0.85f, 0.63f, 0f};
	private @Nullable Loop loop;
	private int nextOneShot = 100;
	private final RandomSource random = RandomSource.create();

	public BiomeEffectsFeature() {
		super("biome_effects", "Biome effects", "Particles, daylight tint, fog colour and ambience for each biome", Category.WORLD, Icons.LEAF, false);
		presets();
	}

	public static BiomeEffectsFeature get() {
		return Features.get(BiomeEffectsFeature.class);
	}

	public static void init() {
		BiomeEffectsFeature feature = get();
		WorldFx.layer(feature, feature::render);
	}

	// The biome store

	private JsonObject writeLooks() {
		JsonObject o = new JsonObject();
		looks.forEach((id, look) -> o.add(id, look.save()));
		return o;
	}

	private void readLooks(JsonObject o) {
		looks.clear();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (e.getValue().isJsonObject()) {
				looks.put(e.getKey(), Look.load(e.getValue().getAsJsonObject()));
			}
		}
	}

	/** A handful of biomes start with a look, so switching the feature on shows something straight away. */
	private void presets() {
		looks.clear();
		Look swamp = new Look();
		swamp.particles.set(Particles.FIREFLIES);
		swamp.particleColor.set("#C6F36B");
		swamp.fog.set(true);
		swamp.fogColor.set("#6F8F5A");
		swamp.fogStrength.set(0.45);
		swamp.ambience.set(Ambience.FIREFLY);
		looks.put("minecraft:swamp", swamp);
		looks.put("minecraft:mangrove_swamp", Look.load(swamp.save()));
		Look cherry = new Look();
		cherry.particles.set(Particles.DRIFT);
		cherry.particleColor.set("#F9A8D4");
		cherry.amount.set(80.0);
		cherry.tint.set(true);
		cherry.tintColor.set("#FFC4E1");
		cherry.tintStrength.set(0.3);
		looks.put("minecraft:cherry_grove", cherry);
		Look pale = new Look();
		pale.particles.set(Particles.DRIFT);
		pale.particleColor.set("#D4D4D8");
		pale.fog.set(true);
		pale.fogColor.set("#9CA3AF");
		pale.fogStrength.set(0.6);
		looks.put("minecraft:pale_garden", pale);
		Look desert = new Look();
		desert.particles.set(Particles.NONE);
		desert.tint.set(true);
		desert.tintColor.set("#FFD08A");
		desert.fog.set(true);
		desert.fogColor.set("#E8CFA0");
		desert.fogStrength.set(0.4);
		desert.ambience.set(Ambience.DRY_GRASS);
		looks.put("minecraft:desert", desert);
		Look snow = new Look();
		snow.particles.set(Particles.DRIFT);
		snow.particleColor.set("#F8FAFC");
		snow.amount.set(120.0);
		looks.put("minecraft:snowy_plains", snow);
		looks.put("minecraft:ice_spikes", Look.load(snow.save()));
		Look crimson = new Look();
		crimson.particles.set(Particles.DRIFT);
		crimson.particleColor.set("#F87171");
		looks.put("minecraft:crimson_forest", crimson);
		Look warped = new Look();
		warped.particles.set(Particles.FIREFLIES);
		warped.particleColor.set("#2DD4BF");
		looks.put("minecraft:warped_forest", warped);
	}

	/** The look for {@code biome}, made (as a blank look) if it has none yet. */
	public Look look(String biome) {
		return looks.computeIfAbsent(biome, b -> new Look());
	}

	public @Nullable Look existing(String biome) {
		return looks.get(biome);
	}

	/** Forgets a biome's look, so it shows as vanilla again. */
	public void clear(String biome) {
		looks.remove(biome);
	}

	/** Drops blank looks, so the profile only keeps biomes you changed. */
	public void tidy() {
		looks.values().removeIf(l -> !l.changesAnything() && l.on.on());
	}

	public String here() {
		return here;
	}

	public Map<String, Look> looks() {
		return looks;
	}

	// Each tick: where you are, and the blend towards that biome's look

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			here = "";
			target = null;
			stopLoop();
			return;
		}
		here = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
		Look look = looks.get(here);
		target = look != null && look.on.on() ? look : null;
		float k = 1f - (float) Math.exp(-0.05 * 3.0 / blend.get());
		blendTo(fogNow, target != null && target.fog.on() ? target.fogColor.argb() : 0, target != null && target.fog.on() ? target.fogStrength.f() : 0f, k);
		blendTo(tintNow, target != null && target.tint.on() ? target.tintColor.argb() : 0, target != null && target.tint.on() ? target.tintStrength.f() : 0f, k);
		ambience(mc, k);
	}

	/** Moves {@code now} towards a colour and strength; with strength 0 only the strength fades, so the colour never flashes. */
	public static void blendTo(float[] now, int argb, float strength, float k) {
		if (strength > 0f) {
			now[0] += (((argb >> 16) & 0xFF) / 255f - now[0]) * k;
			now[1] += (((argb >> 8) & 0xFF) / 255f - now[1]) * k;
			now[2] += ((argb & 0xFF) / 255f - now[2]) * k;
		}
		now[3] += (strength - now[3]) * k;
		if (now[3] < 0.002f && strength == 0f) {
			now[3] = 0f;
		}
	}

	@Override
	protected void onDisable() {
		motes.clear();
		stopLoop();
		fogNow[3] = 0f;
		tintNow[3] = 0f;
		target = null;
	}

	/** Fog colour to mix in: r, g, b, strength (0 when nothing to do). */
	public float[] fogTint() {
		return isEnabled() ? fogNow : new float[4];
	}

	/** Daylight tint to mix in: r, g, b, strength. */
	public float[] lightTint() {
		return isEnabled() ? tintNow : new float[4];
	}

	/** True while the biome you are in sets its own particles (or none), so Ambient particles steps aside. */
	public boolean replacesParticles() {
		return isEnabled() && target != null && target.particles.get() != Particles.KEEP;
	}

	private void render(WorldFx.Frame f) {
		if (CleanWorldFeature.hidesClientParticles()) {
			motes.clear();
			return;
		}
		Look look = target;
		AmbientParticlesFeature.@Nullable Style style = look == null ? null : look.particles.get().style();
		if (style == null) {
			motes.draw(f, null);
			return;
		}
		Motes.Look base = AmbientParticlesFeature.get().look();
		motes.draw(f, new Motes.Look(style, look.amount.i(), base.radius(), base.size(), look.particleColor.argb(), false));
	}

	// Ambience

	private void ambience(Minecraft mc, float k) {
		Ambience want = target == null || !ambienceOn.on() ? Ambience.OFF : target.ambience.get();
		float volume = target == null ? 0f : target.volume.f();
		if (want.loop && want.sound != null) {
			if (loop == null || loop.kind != want || loop.isStopped()) {
				if (loop != null) {
					loop.fadeOut();
				}
				loop = new Loop(want, want.sound.get());
				mc.getSoundManager().play(loop);
			}
			loop.target = volume;
		} else if (loop != null) {
			loop.fadeOut();
			loop = null;
		}
		if (!want.loop && want.sound != null && --nextOneShot <= 0) {
			nextOneShot = 120 + random.nextInt(260);
			mc.getSoundManager().play(new SimpleSoundInstance(want.sound.get().getLocation(), SoundSource.AMBIENT, volume, 0.9f + random.nextFloat() * 0.2f,
				random, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
		}
	}

	private void stopLoop() {
		if (loop != null) {
			loop.fadeOut();
			loop = null;
		}
	}

	/** A looping sound that fades in, follows its target volume and fades out before stopping. */
	private static final class Loop extends AbstractTickableSoundInstance {
		final Ambience kind;
		float target;
		private float now = 0.0001f;
		private boolean leaving;

		Loop(Ambience kind, SoundEvent sound) {
			super(sound, SoundSource.AMBIENT, RandomSource.create());
			this.kind = kind;
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = now;
		}

		void fadeOut() {
			leaving = true;
		}

		@Override
		public void tick() {
			float goal = leaving ? 0f : target;
			now += (goal - now) * 0.06f;
			volume = Math.max(0.0001f, now);
			if (leaving && now < 0.005f) {
				stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
