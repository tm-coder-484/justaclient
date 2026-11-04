package dev.justaclient.mixin;

import dev.justaclient.feature.impl.ServersFeature;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Folders button on the vanilla server list that opens the client's list. */
@Mixin(JoinMultiplayerScreen.class)
abstract class JoinMultiplayerScreenMixin extends Screen {
	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void justaclient$foldersButton(CallbackInfo ci) {
		ServersFeature feature = ServersFeature.get();
		if (feature == null || !feature.isEnabled()) {
			return;
		}
		Screen self = this;
		addRenderableWidget(Button.builder(Component.literal("Folders"), b -> minecraft.gui.setScreen(new dev.justaclient.ui.ServersScreen(self)))
			.bounds(width - 66, 6, 60, 20).build());
	}
}
