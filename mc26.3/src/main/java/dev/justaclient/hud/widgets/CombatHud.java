package dev.justaclient.hud.widgets;

import dev.justaclient.feature.impl.CombatTracker;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.render.Icons;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Your combo, hits this session, the reach of your last hit and your shield's condition. */
public final class CombatHud extends RowsHud {
	private final BoolSetting combo = bool("combo", "Combo", true);
	private final BoolSetting best = bool("best", "Best combo", false);
	private final BoolSetting hits = bool("hits", "Hit counter", true);
	private final BoolSetting reach = bool("reach", "Last hit distance", true);
	private final BoolSetting shield = bool("shield", "Shield condition", true);

	public CombatHud() {
		super("combat_info", "Combat info", "Combo, hit counter, hit distance and shield condition", Icons.SWORDS, false, 0.5f, 0.6f);
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		Player player = Minecraft.getInstance().player;
		if (!preview && player == null) {
			return;
		}
		if (combo.on()) {
			rows.add(new Row("Combo", String.valueOf(preview ? 4 : CombatTracker.combo())));
		}
		if (best.on()) {
			rows.add(new Row("Best combo", String.valueOf(preview ? 9 : CombatTracker.bestCombo())));
		}
		if (hits.on()) {
			rows.add(new Row("Hits", String.valueOf(preview ? 57 : CombatTracker.hits())));
		}
		if (reach.on()) {
			float r = preview ? 2.84f : CombatTracker.lastReach();
			rows.add(new Row("Reach", r < 0 ? "-" : String.format(Locale.ROOT, "%.2f", r)));
		}
		if (shield.on()) {
			String value = preview ? "86% · Ready" : shieldState(player);
			if (value != null) {
				rows.add(new Row("Shield", value));
			}
		}
	}

	/** Durability left, and whether an axe has knocked the shield out. Null when you are not holding one. */
	private static String shieldState(Player player) {
		ItemStack stack = player.getOffhandItem().is(Items.SHIELD) ? player.getOffhandItem()
			: player.getMainHandItem().is(Items.SHIELD) ? player.getMainHandItem() : ItemStack.EMPTY;
		if (stack.isEmpty()) {
			return null;
		}
		int left = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / Math.max(1, stack.getMaxDamage()));
		float cooldown = player.getCooldowns().getCooldownPercent(stack, 0f);
		String state = cooldown > 0f ? "Disabled " + Math.round(cooldown * 100) + "%" : player.isBlocking() ? "Blocking" : "Ready";
		return left + "% · " + state;
	}
}
