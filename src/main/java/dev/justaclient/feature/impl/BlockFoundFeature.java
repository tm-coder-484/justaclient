package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.LifetimeStats;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * A toast when you find a rare block. "Found" means your crosshair landed on it or you mined it, so it only ever tells
 * you about blocks already on your screen; nothing is scanned through walls.
 */
public final class BlockFoundFeature extends Feature {
	public enum When implements ChoiceSetting.Named {
		MINED("When mined"), LOOKED("When looked at"), BOTH("Both");

		private final String label;

		When(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private enum Kind {
		DIAMOND, DEBRIS, EMERALD, GOLD, SPAWNER, OTHER
	}

	private final ChoiceSetting<When> when = choice("when", "Toast", When.MINED);
	private final BoolSetting diamond = bool("diamond", "Diamonds", true);
	private final BoolSetting debris = bool("debris", "Ancient debris", true);
	private final BoolSetting emerald = bool("emerald", "Emerald", true);
	private final BoolSetting gold = bool("gold", "Gold", false);
	private final BoolSetting spawner = bool("spawner", "Spawners", true);
	private final BoolSetting other = bool("other", "Lapis and redstone", false);

	private final Set<BlockPos> seen = new HashSet<>();
	private final Map<Kind, Integer> mined = new HashMap<>();
	private @Nullable ClientLevel level;
	private @Nullable BlockPos breaking;
	private @Nullable BlockState breakingState;

	public BlockFoundFeature() {
		super("block_found", "Block found toasts", "A toast when you mine or spot a rare block", Category.UTILITY, Icons.GEM, false);
	}

	private static @Nullable Kind kind(BlockState state) {
		Block b = state.getBlock();
		if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
			return Kind.DIAMOND;
		}
		if (b == Blocks.ANCIENT_DEBRIS) {
			return Kind.DEBRIS;
		}
		if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) {
			return Kind.EMERALD;
		}
		if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE) {
			return Kind.GOLD;
		}
		if (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER) {
			return Kind.SPAWNER;
		}
		if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) {
			return Kind.OTHER;
		}
		return null;
	}

	private boolean wanted(Kind kind) {
		return switch (kind) {
			case DIAMOND -> diamond.on();
			case DEBRIS -> debris.on();
			case EMERALD -> emerald.on();
			case GOLD -> gold.on();
			case SPAWNER -> spawner.on();
			case OTHER -> other.on();
		};
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			level = mc.level;
			seen.clear();
			mined.clear();
		}
		if (level == null || when.get() == When.MINED) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = level.getBlockState(block.getBlockPos());
			Kind kind = kind(state);
			if (kind != null && wanted(kind) && seen.add(block.getBlockPos().immutable())) {
				Notifications.push(Icons.GEM, "Found", state.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		if (seen.size() > 4096) {
			seen.clear();
		}
	}

	/** Called as the player starts to destroy a block, before it is gone. */
	public static void beforeBreak(BlockPos pos) {
		BlockFoundFeature f = Features.get(BlockFoundFeature.class);
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		f.breaking = pos.immutable();
		f.breakingState = level.getBlockState(pos);
	}

	/** Called once the block broke. Counts it for lifetime stats even with the toasts off. */
	public static void afterBreak(BlockPos pos) {
		BlockFoundFeature f = Features.get(BlockFoundFeature.class);
		if (f.breakingState == null || !pos.equals(f.breaking)) {
			return;
		}
		BlockState state = f.breakingState;
		f.breakingState = null;
		Kind kind = kind(state);
		if (kind == Kind.DIAMOND || kind == Kind.DEBRIS) {
			LifetimeStats.onRareMined(kind == Kind.DIAMOND);
		}
		if (!f.isEnabled() || kind == null || !f.wanted(kind) || f.when.get() == When.LOOKED) {
			return;
		}
		int n = f.mined.merge(kind, 1, Integer::sum);
		String name = state.getBlock().getName().getString();
		Notifications.push(Icons.GEM, name, n == 1 ? "first this session" : ordinal(n) + " this session");
	}

	private static String ordinal(int n) {
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return n + suffix;
	}
}
