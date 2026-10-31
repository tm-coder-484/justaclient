package dev.justaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandFeature;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private float justaclient$swing(float swing) {
		HandFeature hand = Features.get(HandFeature.class);
		return hand.isEnabled() ? hand.swingProgress(swing) : swing;
	}

	@ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float justaclient$equip(float equip) {
		HandFeature hand = Features.get(HandFeature.class);
		return hand.isEnabled() ? hand.equipProgress(equip) : equip;
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void justaclient$moveArm(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, InteractionHand hand,
		float attack, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HandFeature feature = Features.get(HandFeature.class);
		AvatarRenderState avatar = player.avatarRenderState;
		if (feature.isEnabled() && avatar != null) {
			boolean main = hand == InteractionHand.MAIN_HAND;
			feature.moveArm(pose, main ? avatar.mainArm : avatar.mainArm.getOpposite(), main);
		}
	}

	/** Just before the held item is submitted, after vanilla's own arm and use animations. */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void justaclient$transformItem(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HandFeature feature = Features.get(HandFeature.class);
		AvatarRenderState avatar = player.avatarRenderState;
		if (feature.isEnabled() && avatar != null) {
			boolean main = hand == InteractionHand.MAIN_HAND;
			feature.transformItem(pose, main ? avatar.mainArm : avatar.mainArm.getOpposite(), main);
		}
	}
}
