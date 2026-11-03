package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Click sounds for vanilla and modded buttons, and for the client's own menus: off, quieter, or another sound. */
public final class GuiSoundsFeature extends Feature {
	public enum Pack implements ChoiceSetting.Named {
		OFF("Off", null, 1f), SUBTLE("Subtle", () -> SoundEvents.UI_BUTTON_CLICK.value(), 1.35f),
		SOFT("Soft", () -> SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6f), CRISP("Crisp", () -> SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f),
		GLASS("Glass", () -> SoundEvents.AMETHYST_BLOCK_HIT, 1.4f), POP("Pop", () -> SoundEvents.ITEM_PICKUP, 1.7f);

		private final String label;
		private final Supplier<SoundEvent> sound;
		private final float pitch;

		Pack(String label, Supplier<SoundEvent> sound, float pitch) {
			this.label = label;
			this.sound = sound;
			this.pitch = pitch;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Pack> pack = choice("pack", "Sound", Pack.SUBTLE);
	private final NumberSetting volume = number("volume", "Volume", 0.5, 0.05, 1, 0.05, NumberSetting.percent());
	private final BoolSetting ours = bool("client_menus", "Also in the client's menus", true);

	public GuiSoundsFeature() {
		super("gui_sounds", "GUI sounds", "Quieter or different button clicks everywhere, or none", Category.SCREENS, Icons.MUSIC, false);
	}

	/** Plays the button click in place of vanilla's; returns true if vanilla's should not play. */
	public static boolean click(SoundManager sounds) {
		GuiSoundsFeature f = Features.get(GuiSoundsFeature.class);
		if (f == null || !f.isEnabled()) {
			return false;
		}
		f.play(sounds);
		return true;
	}

	/** A click from the client's own menus; silent unless this feature is on and set to cover them. */
	public static void menuClick() {
		GuiSoundsFeature f = Features.get(GuiSoundsFeature.class);
		if (f != null && f.isEnabled() && f.ours.on()) {
			f.play(Minecraft.getInstance().getSoundManager());
		}
	}

	private void play(SoundManager sounds) {
		Pack p = pack.get();
		if (p.sound == null) {
			return;
		}
		sounds.play(SimpleSoundInstance.forUI(p.sound.get(), p.pitch, volume.f()));
	}
}
