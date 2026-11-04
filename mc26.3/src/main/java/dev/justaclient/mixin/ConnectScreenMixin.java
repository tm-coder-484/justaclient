package dev.justaclient.mixin;

import dev.justaclient.feature.impl.AutoReconnectFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the server a connection is attempted to, so a retry after a kick goes back to that server. */
@Mixin(ConnectScreen.class)
abstract class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void justaclient$attempt(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay,
		@Nullable TransferState transfer, CallbackInfo ci) {
		AutoReconnectFeature.connecting(data);
	}
}
