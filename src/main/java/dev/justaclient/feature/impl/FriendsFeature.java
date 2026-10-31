package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Icons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * People you play with. Their name tags and tab list entries get your friend colour, and stream mode can hide their
 * names too. Middle-click a player to add or remove them, or use {@code /jfriend}.
 */
public final class FriendsFeature extends Feature {
	private final TextSetting names = add(new TextSetting("names", "Friends", "", 512, "Steve, Alex"));
	private final ColorSetting color = color("color", "Name colour", "#22C55E");
	private final BoolSetting middleClick = bool("middle_click", "Middle-click players to add", true);
	private final BoolSetting nameTags = bool("name_tags", "Colour name tags", true);
	private final BoolSetting tabList = bool("tab_list", "Colour tab list", true);
	private boolean middleWasDown;

	public FriendsFeature() {
		super("friends", "Friends", "Highlight friends' names; middle-click a player to add them", Category.UTILITY, Icons.USERS, true);
	}

	public static FriendsFeature get() {
		return Features.get(FriendsFeature.class);
	}

	public List<String> list() {
		List<String> out = new ArrayList<>();
		for (String s : names.get().split(",")) {
			String n = s.trim();
			if (!n.isEmpty()) {
				out.add(n);
			}
		}
		return out;
	}

	public boolean isFriend(String name) {
		if (!isEnabled() || name == null) {
			return false;
		}
		for (String n : list()) {
			if (n.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Adds or removes a friend. Returns true if they are a friend afterwards. */
	public boolean toggleFriend(String name) {
		List<String> list = list();
		boolean removed = list.removeIf(n -> n.equalsIgnoreCase(name));
		if (!removed) {
			list.add(name);
		}
		names.set(String.join(", ", list));
		Profiles.save();
		return !removed;
	}

	public boolean add(String name) {
		return !isFriend(name) && toggleFriend(name);
	}

	public boolean remove(String name) {
		return list().stream().anyMatch(n -> n.equalsIgnoreCase(name)) && !toggleFriend(name);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.screen == null && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		if (down && !middleWasDown && middleClick.on() && mc.crosshairPickEntity instanceof Player player && player != mc.player) {
			String name = player.getGameProfile().name();
			boolean now = toggleFriend(name);
			Notifications.push(Icons.USERS, name, now ? "added to friends" : "removed from friends");
		}
		middleWasDown = down;
	}

	/** Recolours a displayed player name if they are a friend. */
	public Component tint(Component name, String player, boolean nameTag) {
		if (!isFriend(player) || !(nameTag ? nameTags.on() : tabList.on())) {
			return name;
		}
		// A flat copy, so team colours on the original's parts do not win over the friend colour.
		return Component.literal(name.getString()).withColor(color.argb() & 0xFFFFFF);
	}
}
