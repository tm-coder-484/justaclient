package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An extra effect when anyone pops a totem of undying, plus an optional toast that counts pops. Vanilla already shows
 * the pop with particles and a sound, so this only restyles it.
 */
public final class TotemPopFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		BURST("Burst"), RISE("Rising light"), OFF("Toast only");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Effect", Style.BURST);
	private final ColorSetting color = color("color", "Colour", "#F59E0B");
	private final BoolSetting toast = bool("toast", "Toast with pop count", true);
	private final BoolSetting self = bool("self", "Include your own pops", true);
	private final Map<UUID, Integer> pops = new HashMap<>();
	private final RandomSource random = RandomSource.create();

	public TotemPopFeature() {
		super("totem_pop", "Totem pops", "An effect and a counter when a totem pops", Category.COMBAT, Icons.HEART, true);
	}

	public void onPop(Entity entity) {
		if (!isEnabled()) {
			return;
		}
		boolean mine = entity == Minecraft.getInstance().player;
		if (mine && !self.on()) {
			return;
		}
		int count = pops.merge(entity.getUUID(), 1, Integer::sum);
		AABB box = entity.getBoundingBox();
		Vec3 feet = new Vec3(box.getCenter().x, box.minY + 0.05, box.getCenter().z);
		int c = color.argb();
		// Your own pop happens around the camera; keep it to the ring so it does not fill the screen.
		switch (style.get()) {
			case BURST -> {
				WorldFx.ring(feet, 0.3f, 1.8f, 0.2f, c, 0.7f);
				if (!mine) {
					for (int i = 0; i < 30; i++) {
						double a = random.nextDouble() * Math.PI * 2;
						double v = 1.5 + random.nextDouble() * 2;
						WorldFx.particle(feet.x, feet.y + box.getYsize() * 0.5, feet.z, Math.cos(a) * v, 1 + random.nextDouble() * 3, Math.sin(a) * v,
							0.15f, i % 2 == 0 ? c : Colors.lerp(c, 0xFFFFFFFF, 0.6f), 0.9f, 5f, 1.5f);
					}
				}
			}
			case RISE -> {
				WorldFx.ring(feet, 0.2f, 1.2f, 0.15f, c, 1.0f);
				if (!mine) {
					for (int i = 0; i < 24; i++) {
						double a = random.nextDouble() * Math.PI * 2;
						WorldFx.particle(feet.x + Math.cos(a) * 0.45, feet.y + random.nextDouble() * 0.4, feet.z + Math.sin(a) * 0.45, 0, 1.6 + random.nextDouble(), 0,
							0.18f, c, 1.3f, -0.3f, 0.5f);
					}
				}
			}
			case OFF -> {
			}
		}
		if (toast.on()) {
			String who = mine ? "You" : entity.getName().getString();
			Notifications.push(Icons.HEART, who, "popped a totem" + (count > 1 ? " (" + count + ")" : ""));
		}
	}

	@Override
	public void tick() {
		// Counts reset when the world changes.
		if (Minecraft.getInstance().level == null) {
			pops.clear();
		}
	}
}
