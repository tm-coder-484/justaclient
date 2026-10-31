package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.NameProtectFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Name protect: every string the vanilla font draws or measures passes through here first. */
@Mixin(Font.class)
abstract class FontMixin {
	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private String justaclient$protectString(String text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"),
		argsOnly = true)
	private FormattedCharSequence justaclient$protectSequence(FormattedCharSequence text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String justaclient$widthString(String text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText justaclient$widthText(FormattedText text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence justaclient$widthSequence(FormattedCharSequence text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}
}
