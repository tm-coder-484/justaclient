package dev.justaclient.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.justaclient.feature.impl.FriendsFeature;
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
 * </ul>
 */
public final class JacCommands {
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
