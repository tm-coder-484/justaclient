package dev.justaclient.mixin;

import dev.justaclient.chat.ChatHook;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every line the chat box shows passes through {@link ChatHook}, and its scroll position is kept when the chat closes. */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component justaclient$decorate(Component line) {
		return ChatHook.incoming(line);
	}

	@Inject(method = "resetChatScroll", at = @At("HEAD"))
	private void justaclient$beforeScrollReset(CallbackInfo ci) {
		ChatHook.beforeScrollReset((ChatComponent) (Object) this);
	}
}
