package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.justaclient.render.HandFx;
import dev.justaclient.render.PostFx;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Unique
	private boolean justaclient$worldDrawn;

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void justaclient$worldDone(CallbackInfo ci) {
		justaclient$worldDrawn = true;
	}

	/** The world, hand and entity outlines are done and the GUI has not started: grade the frame now. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void justaclient$postFx(CallbackInfo ci) {
		if (justaclient$worldDrawn) {
			justaclient$worldDrawn = false;
			PostFx.apply();
		}
	}

	/** Sends the hand's render pass to the hand glow target instead of the main one. */
	@WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE",
		target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
	private RenderPass justaclient$captureHand(CommandEncoder encoder, Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear,
		GpuTextureView depth, OptionalDouble clearDepth, Operation<RenderPass> original) {
		if (HandFx.begin()) {
			return original.call(encoder, label, HandFx.color(), clear, HandFx.depth(), clearDepth);
		}
		return original.call(encoder, label, color, clear, depth, clearDepth);
	}

	@Inject(method = "renderItemInHand", at = @At("TAIL"))
	private void justaclient$compositeHand(CameraRenderState camera, PlayerRenderState player, GpuTextureView depth, CallbackInfo ci) {
		HandFx.end();
	}
}
