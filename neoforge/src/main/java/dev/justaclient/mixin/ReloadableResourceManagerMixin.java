package dev.justaclient.mixin;

import dev.justaclient.feature.impl.DevConsoleFeature;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records each resource reload for the Dev Console's reload history. */
@Mixin(ReloadableResourceManager.class)
abstract class ReloadableResourceManagerMixin {
	@Inject(method = "createReload", at = @At("RETURN"))
	private void justaclient$recordReload(Executor background, Executor game, CompletableFuture<Unit> waiting, List<PackResources> packs,
		CallbackInfoReturnable<ReloadInstance> cir) {
		DevConsoleFeature.reloadStarted(packs, cir.getReturnValue());
	}
}
