package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Plays a sound only you hear when one of your hits lands. Uses sounds already in the game, so nothing is downloaded. */
public final class HitSoundsFeature extends Feature {
	public enum Sound implements ChoiceSetting.Named {
		CLICK("Click", () -> SoundEvents.UI_BUTTON_CLICK.value()),
		BELL("Bell", () -> SoundEvents.NOTE_BLOCK_BELL.value()),
		CHIME("Chime", () -> SoundEvents.AMETHYST_BLOCK_CHIME),
		PLING("Pling", () -> SoundEvents.NOTE_BLOCK_PLING.value()),
		ORB("Orb", () -> SoundEvents.EXPERIENCE_ORB_PICKUP);

		private final String label;
		final Supplier<SoundEvent> event;

		Sound(String label, Supplier<SoundEvent> event) {
			this.label = label;
			this.event = event;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Sound> sound = choice("sound", "Sound", Sound.CLICK);
	private final NumberSetting volume = number("volume", "Volume", 0.5, 0.05, 1.0, 0.05, NumberSetting.percent());
	private final NumberSetting pitch = number("pitch", "Pitch", 1.4, 0.5, 2.0, 0.05, NumberSetting.decimal("x"));

	public HitSoundsFeature() {
		super("hit_sounds", "Hit sounds", "A sound only you hear when a hit lands", Category.COMBAT, Icons.BELL, false);
	}

	public void onHit() {
		if (isEnabled()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get().event.get(), pitch.f(), volume.f()));
		}
	}
}
