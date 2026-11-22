package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.PostFx;

/** Blends each frame with the last one so fast camera turns smear a little, like a film camera. */
public final class MotionBlurFeature extends Feature {
	private final NumberSetting strength = number("strength", "Strength", 0.5, 0.1, 0.9, 0.05, NumberSetting.percent());

	public MotionBlurFeature() {
		super("motion_blur", "Motion blur", "Smear fast camera movement a little", Category.WORLD, Icons.ZAP, false);
	}

	@Override
	protected void onEnable() {
		PostFx.resetHistory();
	}

	/** How much of the previous frame to keep, scaled so the trail lasts the same time at any frame rate. */
	public float blend(float dt) {
		float perFrameAt60 = strength.f() * 0.85f;
		return (float) Math.pow(perFrameAt60, dt * 60f);
	}
}
