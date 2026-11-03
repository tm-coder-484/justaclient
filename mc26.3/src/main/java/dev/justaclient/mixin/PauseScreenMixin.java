package dev.justaclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.feature.impl.ServersFeature;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a Servers button under Back to Game, to switch servers without leaving to the title screen first. */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 0, shift = At.Shift.AFTER), require = 0)
	private void justaclient$serversButton(CallbackInfo ci, @Local GridLayout.RowHelper rows) {
		ServersFeature feature = ServersFeature.get();
		if (feature == null || !feature.pauseButton()) {
			return;
		}
		Button button = Button.builder(Component.literal("Servers"), b -> minecraft.gui.setScreen(new dev.justaclient.ui.ServersScreen(this))).width(204)
			.build();
		button.active = minecraft.allowsMultiplayer();
		rows.addChild(button, 2);
	}
}
