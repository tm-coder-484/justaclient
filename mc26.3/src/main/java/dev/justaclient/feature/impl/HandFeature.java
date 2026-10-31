package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.justaclient.anim.Easing;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person hand placement, swing feel and an inspect animation. Only your own view of your own hands changes; the
 * swing speed is the animation only, attack timing is untouched.
 */
public final class HandFeature extends Feature {
	public enum Swing implements ChoiceSetting.Named {
		VANILLA("Vanilla"), SMOOTH("Smooth"), SNAPPY("Snappy");

		private final String label;

		Swing(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final DoubleFunction<String> OFFSET = v -> String.format("%+.2f", v);

	private final NumberSetting x = number("x", "Sideways", 0, -0.6, 0.6, 0.02, OFFSET);
	private final NumberSetting y = number("y", "Up / down", 0, -0.6, 0.6, 0.02, OFFSET);
	private final NumberSetting z = number("z", "Forward / back", 0, -0.6, 0.6, 0.02, OFFSET);
	private final NumberSetting yaw = number("yaw", "Turn", 0, -45, 45, 1, NumberSetting.unit("°"));
	private final NumberSetting pitch = number("pitch", "Tilt", 0, -45, 45, 1, NumberSetting.unit("°"));
	private final NumberSetting roll = number("roll", "Roll", 0, -45, 45, 1, NumberSetting.unit("°"));
	private final NumberSetting scale = number("scale", "Item size", 1, 0.4, 1.6, 0.05, NumberSetting.percent());
	private final BoolSetting offhand = bool("offhand", "Mirror onto off hand", true);
	private final ChoiceSetting<Swing> swing = choice("swing", "Swing style", Swing.SMOOTH);
	private final NumberSetting swingSpeed = number("swing_speed", "Swing animation speed", 1, 0.5, 2, 0.05, NumberSetting.decimal("x"));
	private final BoolSetting noEquipDip = bool("no_equip_dip", "No dip when switching items", false);
	private final KeySetting inspectKey = key("inspect_key", "Inspect key", InputConstants.KEY_I);
	private long inspectStart = -1;
	private boolean inspectWasDown;

	public HandFeature() {
		super("hand", "Hand", "Hand position and size, swing style, inspect animation", Category.FIRST_PERSON, Icons.HAND, true);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.gui.screen() == null && inspectKey.isDown();
		if (down && !inspectWasDown && mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
			inspectStart = System.nanoTime();
		}
		inspectWasDown = down;
	}

	/** Moves the whole arm. Runs as each hand's pose is pushed. */
	public void moveArm(PoseStack pose, HumanoidArm arm, boolean mainHand) {
		if (!mainHand && !offhand.on()) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(x.f() * side, y.f(), -z.f());
	}

	/** Turns and sizes the item itself, around its own grip, and plays the inspect animation. */
	public void transformItem(PoseStack pose, HumanoidArm arm, boolean mainHand) {
		if (!mainHand && !offhand.on()) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		if (yaw.get() != 0 || pitch.get() != 0 || roll.get() != 0) {
			pose.rotateDegrees(Axis.YP, yaw.f() * side);
			pose.rotateDegrees(Axis.XP, pitch.f());
			pose.rotateDegrees(Axis.ZP, roll.f() * side);
		}
		float s = scale.f();
		if (s != 1f) {
			pose.scale(s, s, s);
		}
		if (mainHand) {
			inspect(pose, side);
		}
	}

	private void inspect(PoseStack pose, float side) {
		if (inspectStart < 0) {
			return;
		}
		float t = (System.nanoTime() - inspectStart) / 1e9f / 2.2f;
		if (t >= 1f) {
			inspectStart = -1;
			return;
		}
		// Lift and turn the item to show its face, roll it over to show the other side, then settle back.
		float lift = Easing.CUBIC_OUT.apply(Math.min(1f, t * 4f)) * (1f - Easing.CUBIC_IN_OUT.apply(Math.max(0f, (t - 0.8f) / 0.2f)));
		float spin = Easing.CUBIC_IN_OUT.apply(Math.max(0f, Math.min(1f, (t - 0.25f) / 0.5f)));
		pose.translate(-0.12f * side * lift, 0.08f * lift, 0.05f * lift);
		pose.rotateDegrees(Axis.YP, side * (-35f * lift + 180f * spin * lift));
		pose.rotateDegrees(Axis.ZP, side * 20f * lift);
	}

	/** Reshapes the swing progress (0..1) for the chosen style. */
	public float swingProgress(float progress) {
		if (progress <= 0f) {
			return progress;
		}
		return switch (swing.get()) {
			case VANILLA -> progress;
			case SMOOTH -> Easing.CUBIC_IN_OUT.apply(progress);
			case SNAPPY -> Easing.CUBIC_OUT.apply(progress);
		};
	}

	public float equipProgress(float progress) {
		return noEquipDip.on() ? 0f : progress;
	}

	/** Swing duration in ticks for your own animation. */
	public int swingDuration(int ticks) {
		return Math.max(1, Math.round(ticks / swingSpeed.f()));
	}
}
