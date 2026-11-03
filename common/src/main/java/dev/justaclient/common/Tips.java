package dev.justaclient.common;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Short tips for the title and loading screens: half about the client, half about the game. */
public final class Tips {
	private static final List<String> ALL = List.of(
		"Press Right Shift anywhere to open the client menu, even on the title screen.",
		"Every feature can have its own toggle key: open it in the menu and set Toggle key.",
		"Hold C to zoom, and scroll while zooming to go further.",
		"Press M for the map. Right-click it to drop a waypoint.",
		"Press B to save a waypoint where you stand.",
		"Edit HUD layout lets you drag widgets; hold Shift to place them freely.",
		"Profiles keep whole setups. Copy one to the clipboard to share it with a friend.",
		"Reset all settings saves a backup first, in config/justaclient/backups.",
		"The Performance page shows what each feature costs per frame.",
		"Middle-click a player to add them as a friend.",
		"The Timer widget has start and reset keys for speedruns and farms.",
		"Weapon tooltips compare damage and speed with what you are holding.",
		"Sneaking stops you falling off the edge of a block.",
		"A shield blocks most attacks from the front, but an axe can knock it out for a few seconds.",
		"Beds explode in the Nether and the End. Respawn anchors work in the Nether.",
		"Gold armour keeps piglins calm.",
		"Mending repairs gear with the experience you pick up while holding or wearing it.",
		"Blaze powder fuels brewing stands; each one lasts 20 brews.",
		"Water and lava make cobblestone when they meet, or obsidian if the lava is still.",
		"Eating golden apples gives absorption hearts and regeneration.",
		"F3 + B shows hitboxes; the client can restyle them under World.",
		"Totems of undying only work in your main hand or off hand.",
		"Villagers restock trades when they can reach their workstation.",
		"Copper ages in the rain; wax it with honeycomb to keep its colour.");

	private Tips() {
	}

	public static String random() {
		return ALL.get(ThreadLocalRandom.current().nextInt(ALL.size()));
	}

	public static List<String> all() {
		return ALL;
	}
}
