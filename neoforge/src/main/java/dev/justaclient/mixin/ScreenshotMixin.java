package dev.justaclient.mixin;

import dev.justaclient.feature.impl.ScreenshotsFeature;
import java.io.File;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes where a screenshot was taken as the game names its file. */
@Mixin(Screenshot.class)
abstract class ScreenshotMixin {
	@Inject(method = "getFile", at = @At("RETURN"))
	private static void justaclient$taken(File folder, CallbackInfoReturnable<File> cir) {
		ScreenshotsFeature.taken(cir.getReturnValue());
	}
}
