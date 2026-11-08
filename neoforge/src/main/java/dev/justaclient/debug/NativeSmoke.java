package dev.justaclient.debug;

import dev.justaclient.Justaclient;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.Feature;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in development-only smoke world, enabled by -PjacSmokeWorld. Never runs in a normal instance. */
public final class NativeSmoke {
    private static final String WORLD = "Justaclient-Port-Smoke";
    private static boolean opening, prepared;
    private static long readyAt;
    public static boolean ready() {
        return !Boolean.getBoolean("justaclient.smokeWorld") || prepared && System.currentTimeMillis() >= readyAt;
    }

    public static void tick(Minecraft mc) {
        if (!Boolean.getBoolean("justaclient.smokeWorld")) return;
        if (mc.screen instanceof dev.justaclient.ui.CrashHelperScreen || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.screen.onClose();
            return;
        }
        if (!opening && mc.isGameLoadFinished() && mc.getOverlay() == null && (mc.screen instanceof TitleScreen || mc.screen instanceof dev.justaclient.ui.JacTitleScreen)) {
            opening = true;
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat"))) {
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
            } else {
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, null);
                var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(817211L, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
            }
            return;
        }
        if (prepared || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        prepared = true;
        readyAt = System.currentTimeMillis() + 8000;
        Set<String> effects = Set.of("sky", "fog", "light_color", "fullbright", "glint", "hand", "hand_glow", "trails", "jump_circles", "ambient_particles", "cosmetics", "hats", "end_crystal", "hitboxes", "color_grading", "motion_blur", "waypoints", "minimap", "food_info", "crafting_helper", "furnace_info", "potion_guide", "anvil_helper");
        for (Feature feature : Features.all()) if (effects.contains(feature.id)) feature.setEnabled(true);
        Features.get(dev.justaclient.feature.impl.SpatialMenusFeature.class).setEnabled(Boolean.getBoolean("justaclient.smokeSpatial"));
        mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var source = server.createCommandSourceStack();
            for (String command : new String[] {"time set midnight", "gamerule doDaylightCycle false", "tp @p 0 -60 0 -45 0", "give @p diamond_sword", "enchant @p sharpness 3", "give @p diamond_helmet", "give @p golden_apple 16", "summon end_crystal 3 -59 3", "setblock 1 -60 3 chest", "setblock 2 -60 3 crafting_table", "setblock 1 -60 4 furnace"})
                server.getCommands().performPrefixedCommand(source, command);
        });
        Justaclient.LOGGER.info("Native smoke world loaded; enabling rendering checks");
        SelfTest.run();
    }
}
