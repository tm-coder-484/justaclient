package dev.justaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandFeature;
import dev.justaclient.render.HandFx;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	/** The hand is submitted and drawn inside this method, so its draws can be sent to the hand glow target as a whole. */
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void justaclient$captureHand(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		HandFx.begin();
	}

	@Inject(method = "renderHandsWithItems", at = @At("TAIL"))
	private void justaclient$compositeHand(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		HandFx.end();
	}

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private float justaclient$swing(float swing) {
		HandFeature hand = Features.get(HandFeature.class);
		return hand.isEnabled() ? hand.swingProgress(swing) : swing;
	}

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float justaclient$equip(float equip) {
		HandFeature hand = Features.get(HandFeature.class);
		return hand.isEnabled() ? hand.equipProgress(equip) : equip;
	}

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void justaclient$moveArm(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
		float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HandFeature feature = Features.get(HandFeature.class);
		if (feature.isEnabled()) {
			boolean main = hand == InteractionHand.MAIN_HAND;
			HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
			feature.moveArm(pose, arm, main);
		}
	}

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void justaclient$transformItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector,
		int light, CallbackInfo ci) {
		HandFeature feature = Features.get(HandFeature.class);
		if (feature.isEnabled() && !stack.isEmpty()) {
			HumanoidArm arm = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
			feature.transformItem(pose, arm, arm == entity.getMainArm());
		}
	}
}
