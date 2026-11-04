package dev.justaclient.chat;

import dev.justaclient.feature.impl.ChatHistoryFeature;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.mixin.ChatScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** What the chat features add to the chat screen: scroll memory, saved message keys and the tab chips. */
public final class ChatScreens {
	private ChatScreens() {
	}

	public static boolean isChatOpen(Minecraft mc) {
		return mc.screen instanceof ChatScreen;
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen chat)) {
				return;
			}
			ChatHook.restoreScroll(client.gui.getChat());
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				var input = ((ChatScreenAccessor) chat).jac$input();
				// Set the text after the key's own character has been typed, so Alt+1 does not leave a "1" behind.
				return !ChatHook.chatKey(input.getValue(), event.key(), event.hasControlDown(), event.hasAltDown(), text -> client.execute(() -> {
					input.setValue(text);
					input.moveCursorToEnd(false);
				}));
			});
			InvCompat.afterDraw(screen, (s, g, mx, my, delta) -> ChatHistoryFeature.get().drawChips(g, s.width, s.height, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !(InvCompat.isLeft(event.button())
				&& ChatHistoryFeature.get().clickChips(event.x(), event.y(), s.width, s.height)));
		});
	}
}
