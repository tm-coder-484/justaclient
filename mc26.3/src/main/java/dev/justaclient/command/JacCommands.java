package dev.justaclient.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.justaclient.feature.impl.FriendsFeature;
import dev.justaclient.media.NowPlaying;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side commands (never sent to the server):
 * <ul>
 * <li>{@code /jwp add [name]}, {@code /jwp set <name> <x> <y> <z>}, {@code /jwp remove <name>}, {@code /jwp list},
 * {@code /jwp gps <name>}, {@code /jwp gps off}, {@code /jwp hide|show <name>}</li>
 * <li>{@code /jfriend add|remove <name>}, {@code /jfriend list}</li>
 * <li>{@code /potions} (or {@code /jpotions}): the potion guide</li>
 * <li>{@code /storage} (or {@code /jstorage}): search the containers you opened</li>
 * <li>{@code /landmarks} (or {@code /jlandmarks}): the landmark board</li>
 * <li>{@code /lastdeath} (or {@code /jdeath}): the death recap and earlier deaths</li>
 * <li>{@code /ck}: kits; {@code /ck save [name]}, {@code /ck preview|load|check <name>}, {@code /ck stop}, {@code /ck list}</li>
 * <li>{@code /jmedia}: what the Spotify card sees (player, track, timeline, cover, lyrics), for working out why
 * something is missing</li>
 * </ul>
 */
public final class JacCommands {
	private static final SuggestionProvider<FabricClientCommandSource> KITS = (ctx, builder) -> {
		for (var kit : dev.justaclient.feature.impl.KitsFeature.get().kits()) {
			builder.suggest(kit.name);
		}
		return builder.buildFuture();
	};

	private static int open(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(screen));
		return 1;
	}

	private static final SuggestionProvider<FabricClientCommandSource> WAYPOINTS = (ctx, builder) -> {
		for (Waypoint w : Waypoints.all()) {
			builder.suggest(w.name);
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (ctx, builder) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) {
			mc.getConnection().getOnlinePlayers().forEach(p -> builder.suggest(p.getProfile().name()));
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> FRIENDS = (ctx, builder) -> {
		FriendsFeature.get().list().forEach(builder::suggest);
		return builder.buildFuture();
	};

	private JacCommands() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("jwp")
				.then(literal("add")
					.executes(ctx -> add(ctx, Waypoints.nextName()))
					.then(argument("name", StringArgumentType.greedyString()).executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(literal("set").then(argument("name", StringArgumentType.string())
					.then(argument("x", IntegerArgumentType.integer()).then(argument("y", IntegerArgumentType.integer())
						.then(argument("z", IntegerArgumentType.integer()).executes(JacCommands::set))))))
				.then(literal("remove").then(argument("name", StringArgumentType.greedyString()).suggests(WAYPOINTS).executes(ctx -> {
					String name = unquote(StringArgumentType.getString(ctx, "name"));
					return reply(ctx, Waypoints.remove(name), "Removed " + name, "No waypoint called " + name);
				})))
				.then(literal("list").executes(JacCommands::list))
				.then(literal("gps")
					.then(literal("off").executes(ctx -> {
						Waypoints.track(null);
						return reply(ctx, true, "GPS off", "");
					}))
					.then(argument("name", StringArgumentType.greedyString()).suggests(WAYPOINTS).executes(ctx -> {
						Waypoint w = Waypoints.find(unquote(StringArgumentType.getString(ctx, "name")));
						if (w != null) {
							Waypoints.track(w.name);
						}
						return reply(ctx, w != null, w != null ? "Tracking " + w.name : "", "No waypoint called that");
					})))
				.then(literal("hide").then(argument("name", StringArgumentType.greedyString()).suggests(WAYPOINTS).executes(ctx -> visible(ctx, false))))
				.then(literal("show").then(argument("name", StringArgumentType.greedyString()).suggests(WAYPOINTS).executes(ctx -> visible(ctx, true)))));
			dispatcher.register(literal("jselftest").executes(ctx -> {
				for (String line : dev.justaclient.debug.SelfTest.run()) {
					ctx.getSource().sendFeedback(Component.literal(line).withStyle(line.startsWith("FAIL") ? ChatFormatting.RED : ChatFormatting.GRAY));
				}
				return 1;
			}));
			dispatcher.register(literal("ck")
				.executes(ctx -> open(new dev.justaclient.ui.KitsScreen(null)))
				.then(literal("save").executes(ctx -> open(dev.justaclient.ui.KitsScreen.fromInventory("Kit " + (dev.justaclient.feature.impl.KitsFeature.get()
						.kits().size() + 1))))
					.then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
						String n = dev.justaclient.common.kits.Kit.cleanName(StringArgumentType.getString(ctx, "name"));
						return n == null ? reply(ctx, false, "", "Give the kit a name") : open(dev.justaclient.ui.KitsScreen.fromInventory(n));
					})))
				.then(literal("preview").then(argument("name", StringArgumentType.greedyString()).suggests(KITS).executes(ctx -> {
					var kit = dev.justaclient.common.kits.Kit.find(dev.justaclient.feature.impl.KitsFeature.get().kits(), StringArgumentType.getString(ctx, "name"));
					return kit == null ? reply(ctx, false, "", "No kit called " + StringArgumentType.getString(ctx, "name")) : open(dev.justaclient.ui.KitsScreen
						.preview(kit));
				})))
				.then(literal("load").then(argument("name", StringArgumentType.greedyString()).suggests(KITS).executes(ctx -> {
					var kit = dev.justaclient.common.kits.Kit.find(dev.justaclient.feature.impl.KitsFeature.get().kits(), StringArgumentType.getString(ctx, "name"));
					if (kit == null) {
						return reply(ctx, false, "", "No kit called " + StringArgumentType.getString(ctx, "name"));
					}
					Minecraft.getInstance().execute(() -> dev.justaclient.feature.impl.KitsFeature.get().use(kit));
					return 1;
				})))
				.then(literal("check").then(argument("name", StringArgumentType.greedyString()).suggests(KITS).executes(ctx -> {
					var kit = dev.justaclient.common.kits.Kit.find(dev.justaclient.feature.impl.KitsFeature.get().kits(), StringArgumentType.getString(ctx, "name"));
					if (kit == null) {
						return reply(ctx, false, "", "No kit called " + StringArgumentType.getString(ctx, "name"));
					}
					Minecraft.getInstance().execute(() -> dev.justaclient.feature.impl.KitsFeature.get().check(kit, false));
					return 1;
				})))
				.then(literal("stop").executes(ctx -> {
					dev.justaclient.feature.impl.KitsFeature.get().stopChecking();
					return reply(ctx, true, "Stopped checking the kit", "");
				}))
				.then(literal("list").executes(ctx -> {
					var kits = dev.justaclient.feature.impl.KitsFeature.get().kits();
					return reply(ctx, true, kits.isEmpty() ? "No kits yet: /ck save <name>" : "Kits: " + String.join(", ", kits.stream().map(k -> k.name)
						.toList()), "");
				})));
			for (String name : new String[] {"lastdeath", "jdeath"}) {
				dispatcher.register(literal(name).executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.gui.setScreen(new dev.justaclient.ui.DeathRecapScreen(null)));
					return 1;
				}));
			}
			for (String name : new String[] {"landmarks", "jlandmarks"}) {
				dispatcher.register(literal(name).executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.gui.setScreen(new dev.justaclient.ui.LandmarksScreen(null)));
					return 1;
				}));
			}
			for (String name : new String[] {"chatlog", "jchat"}) {
				dispatcher.register(literal(name).executes(ctx -> {
					dev.justaclient.feature.impl.ChatHistoryFeature.open(null);
					return 1;
				}).then(argument("tab", StringArgumentType.greedyString()).suggests((c, b) -> {
					dev.justaclient.feature.impl.ChatHistoryFeature.get().tabNames().forEach(b::suggest);
					return b.buildFuture();
				}).executes(ctx -> {
					String want = StringArgumentType.getString(ctx, "tab").trim();
					String tab = dev.justaclient.feature.impl.ChatHistoryFeature.get().tabNames().stream().filter(n -> n.equalsIgnoreCase(want))
						.findFirst().orElse(dev.justaclient.feature.impl.ChatHistoryFeature.ALL);
					dev.justaclient.feature.impl.ChatHistoryFeature.open(tab);
					return 1;
				})));
			}
			// /cmd 1 to 8: send command macro N once (the same as its key).
			dispatcher.register(literal("cmd").executes(ctx -> {
				StringBuilder list = new StringBuilder();
				for (int i = 1; i <= dev.justaclient.feature.impl.MacrosFeature.slots(); i++) {
					String c = dev.justaclient.feature.impl.MacrosFeature.command(i);
					if (!c.isEmpty()) {
						list.append(list.isEmpty() ? "" : "  ").append(i).append(": ").append(c);
					}
				}
				return reply(ctx, !list.isEmpty(), list.toString(), "No macros yet: set them in Chat > Command macros");
			}).then(argument("n", IntegerArgumentType.integer(1, dev.justaclient.feature.impl.MacrosFeature.slots())).executes(ctx -> {
				int n = IntegerArgumentType.getInteger(ctx, "n");
				return reply(ctx, dev.justaclient.feature.impl.MacrosFeature.runSlot(n), "Sent macro " + n, "Macro " + n + " is empty: set it in Chat > Command macros");
			})));
			for (String name : new String[] {"storage", "jstorage"}) {
				dispatcher.register(literal(name).executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.gui.setScreen(new dev.justaclient.ui.StorageScreen(null)));
					return 1;
				}));
			}
			for (String name : new String[] {"potions", "jpotions"}) {
				dispatcher.register(literal(name).executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.gui.setScreen(new dev.justaclient.ui.PotionGuideScreen(null)));
					return 1;
				}));
			}
			dispatcher.register(literal("jmedia").executes(ctx -> {
				ctx.getSource().sendFeedback(Component.literal("Justaclient media").withStyle(ChatFormatting.BOLD));
				for (String line : NowPlaying.describe()) {
					ctx.getSource().sendFeedback(Component.literal(line).withStyle(ChatFormatting.GRAY));
				}
				return 1;
			}));
			dispatcher.register(literal("jfriend")
				.then(literal("add").then(argument("name", StringArgumentType.word()).suggests(PLAYERS).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					return reply(ctx, FriendsFeature.get().add(name), "Added " + name + " to friends", name + " is already a friend");
				})))
				.then(literal("remove").then(argument("name", StringArgumentType.word()).suggests(FRIENDS).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					return reply(ctx, FriendsFeature.get().remove(name), "Removed " + name + " from friends", name + " is not a friend");
				})))
				.then(literal("list").executes(ctx -> {
					var list = FriendsFeature.get().list();
					return reply(ctx, true, list.isEmpty() ? "No friends yet. Middle-click a player or use /jfriend add <name>" : "Friends: " + String.join(", ", list), "");
				})));
		});
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, String name) {
		Player player = ctx.getSource().getPlayer();
		Waypoint w = Waypoints.add(unquote(name), player.getBlockX(), player.getBlockY(), player.getBlockZ());
		return reply(ctx, true, "Saved " + w.name + " at " + w.x + " " + w.y + " " + w.z, "");
	}

	private static int set(CommandContext<FabricClientCommandSource> ctx) {
		Waypoint w = Waypoints.add(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "x"),
			IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
		return reply(ctx, true, "Saved " + w.name + " at " + w.x + " " + w.y + " " + w.z, "");
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx) {
		var all = Waypoints.all();
		if (all.isEmpty()) {
			return reply(ctx, true, "No waypoints in this world yet. Press B or use /jwp add <name>", "");
		}
		String dim = Waypoints.dimension();
		for (Waypoint w : all) {
			String where = w.dimension.equals(dim) ? "" : " (" + w.dimension.replace("minecraft:", "") + ")";
			ctx.getSource().sendFeedback(Component.literal("● ").withColor(w.color & 0xFFFFFF)
				.append(Component.literal(w.name + "  ").withStyle(w.visible ? ChatFormatting.WHITE : ChatFormatting.GRAY))
				.append(Component.literal(w.x + " " + w.y + " " + w.z + where).withStyle(ChatFormatting.GRAY)));
		}
		return all.size();
	}

	private static int visible(CommandContext<FabricClientCommandSource> ctx, boolean visible) {
		Waypoint w = Waypoints.find(unquote(StringArgumentType.getString(ctx, "name")));
		if (w != null) {
			w.visible = visible;
			Waypoints.save();
		}
		return reply(ctx, w != null, w != null ? (visible ? "Showing " : "Hiding ") + w.name : "", "No waypoint called that");
	}

	private static int reply(CommandContext<FabricClientCommandSource> ctx, boolean ok, String success, String failure) {
		if (ok) {
			ctx.getSource().sendFeedback(Component.literal(success));
			return 1;
		}
		ctx.getSource().sendError(Component.literal(failure));
		return 0;
	}

	private static String unquote(String name) {
		String n = name.trim();
		return n.length() >= 2 && n.startsWith("\"") && n.endsWith("\"") ? n.substring(1, n.length() - 1) : n;
	}
}
