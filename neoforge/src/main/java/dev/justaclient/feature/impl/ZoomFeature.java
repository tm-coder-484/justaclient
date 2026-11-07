package dev.justaclient.feature.impl;

import dev.justaclient.anim.Spring;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Hold a key to zoom, like a spyglass without the item. Scroll while zooming to change how far. */
public final class ZoomFeature extends Feature {
	private final KeySetting key = key("key", "Zoom key", GLFW.GLFW_KEY_C);
	private final NumberSetting level = number("level", "Zoom", 4, 1.5, 10, 0.5, NumberSetting.decimal("x"));
	private final BoolSetting smooth = bool("smooth", "Smooth zoom", true);
	private final BoolSetting scroll = bool("scroll", "Scroll to adjust", true);
	private final BoolSetting sensitivity = bool("sensitivity", "Slower mouse while zoomed", true);
	private final BoolSetting cinematic = bool("cinematic", "Cinematic camera while zoomed", false);
	private final Spring factor = new Spring(160f, 24f, 1f);
	private double scrollLevel = -1;
	private boolean zooming;
	private boolean forcedSmoothCamera;

	public ZoomFeature() {
		super("zoom", "Zoom", "Hold a key to zoom, scroll to adjust", Category.CAMERA, Icons.ZOOM_IN, true);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean wasZooming = zooming;
		zooming = mc.screen == null && mc.player != null && key.isDown();
		if (zooming && !wasZooming) {
			scrollLevel = level.get();
			if (cinematic.on() && !mc.options.smoothCamera) {
				mc.options.smoothCamera = true;
				forcedSmoothCamera = true;
			}
		} else if (!zooming && wasZooming && forcedSmoothCamera) {
			mc.options.smoothCamera = false;
			forcedSmoothCamera = false;
		}
	}

	@Override
	protected void onDisable() {
		zooming = false;
		if (forcedSmoothCamera) {
			Minecraft.getInstance().options.smoothCamera = false;
			forcedSmoothCamera = false;
		}
	}

	/** Current FOV divisor, 1 when not zoomed. Called every frame by the game renderer. */
	public float factor() {
		float target = isEnabled() && zooming ? (float) (scrollLevel > 0 ? scrollLevel : level.get()) : 1f;
		factor.setTarget(target);
		if (!smooth.on()) {
			factor.snap(target);
		}
		return Math.max(1f, factor.update());
	}

	/** Returns true if the scroll was used to change the zoom. */
	public boolean onScroll(double amount) {
		if (!isEnabled() || !zooming || !scroll.on() || amount == 0) {
			return false;
		}
		double current = scrollLevel > 0 ? scrollLevel : level.get();
		scrollLevel = Math.max(1.5, Math.min(30.0, current * (amount > 0 ? 1.25 : 0.8)));
		return true;
	}

	/**
	 * Scales the sensitivity option so turning speed drops in step with the zoom. Vanilla cubes {@code s * 0.6 + 0.2}, so
	 * the cube root of the zoom divides that term.
	 */
	public double adjustSensitivity(double s) {
		float f = factor.get();
		if (!isEnabled() || !sensitivity.on() || f <= 1.001f) {
			return s;
		}
		double term = (s * 0.6 + 0.2) / Math.cbrt(f);
		return (term - 0.2) / 0.6;
	}
}
