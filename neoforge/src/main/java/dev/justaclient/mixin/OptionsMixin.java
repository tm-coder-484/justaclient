package dev.justaclient.mixin;

import dev.justaclient.feature.impl.ModsPanelFeature;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the key binds of switched-off mods when options are saved (see {@link dev.justaclient.common.mods.KeybindKeeper}). */
@Mixin(Options.class)
abstract class OptionsMixin {
	@Shadow
	@Final
	private File optionsFile;

	@Shadow
	@Final
	public KeyMapping[] keyMappings;

	@Unique
	private Map<String, String> justaclient$previous = Map.of();

	@Inject(method = "save", at = @At("HEAD"))
	private void justaclient$remember(CallbackInfo ci) {
		justaclient$previous = ModsPanelFeature.keepKeybinds() ? dev.justaclient.common.mods.KeybindKeeper.readKeybinds(optionsFile.toPath())
			: Map.of();
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void justaclient$restore(CallbackInfo ci) {
		if (justaclient$previous.isEmpty()) {
			return;
		}
		Set<String> names = new HashSet<>();
		for (KeyMapping key : keyMappings) {
			names.add(key.getName());
		}
		dev.justaclient.common.mods.KeybindKeeper.restoreMissing(optionsFile.toPath(), justaclient$previous, names);
		justaclient$previous = Map.of();
	}
}
