package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** A ring that spreads out on the ground when you jump, and optionally when you land. */
public final class JumpCirclesFeature extends Feature {
	private final NumberSetting radius = number("radius", "Size", 1.4, 0.5, 3.0, 0.1, NumberSetting.decimal(" blocks"));
	private final NumberSetting duration = number("duration", "Duration", 0.7, 0.3, 1.5, 0.05, NumberSetting.decimal(" s"));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting landing = bool("landing", "Also on landing", false);
	private boolean wasOnGround = true;

	public JumpCirclesFeature() {
		super("jump_circles", "Jump circles", "A ring spreads on the ground when you jump", Category.WORLD, Icons.CIRCLE_DOT, false);
	}

	@Override
	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		boolean onGround = player.onGround();
		Vec3 feet = player.position().add(0, 0.02, 0);
		if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.1 && !player.isPassenger()) {
			spawn(feet, 1f);
		} else if (!wasOnGround && onGround && landing.on()) {
			spawn(feet, 0.7f);
		}
		wasOnGround = onGround;
	}

	private void spawn(Vec3 at, float scale) {
		int c = color.argb();
		WorldFx.ring(at, 0.15f, radius.f() * scale, 0.16f, c, duration.f());
		WorldFx.ring(at.add(0, 0.01, 0), 0.05f, radius.f() * scale * 0.6f, 0.08f, Colors.lerp(c, 0xFFFFFFFF, 0.5f), duration.f() * 0.8f);
	}
}
