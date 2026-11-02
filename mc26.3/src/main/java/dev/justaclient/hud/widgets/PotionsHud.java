package dev.justaclient.hud.widgets;

import com.google.common.collect.Ordering;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class PotionsHud extends HudFeature {
	public enum RingColor implements ChoiceSetting.Named {
		EFFECT("Effect"), ACCENT("Accent");

		private final String label;

		RingColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final float RING = 15f;
	private static final float ROW_H = 18f;

	public final BoolSetting hideVanilla = bool("hide_vanilla", "Hide vanilla effect icons", true);
	private final BoolSetting ring = bool("ring", "Timer ring", true);
	private final ChoiceSetting<RingColor> ringColor = choice("ring_color", "Ring colour", RingColor.EFFECT).visibleWhen(ring::on);
	private final BoolSetting blink = bool("blink", "Pulse when ending", true);
	private final List<Entry> entries = new ArrayList<>();
	/** Longest duration seen per effect, so the ring can show how much is left. Instances do not keep their start time. */
	private final Map<Holder<MobEffect>, Integer> longest = new HashMap<>();
	private final Spring heightSpring = Spring.ui(0f);
	private int lastCount;

	private record Entry(Holder<MobEffect> effect, String name, String time, float left, boolean ending) {
	}

	public PotionsHud() {
		super("potions", "Potion effects", "Active effects with names and timer rings", Icons.FLASK, true, 1f, 0f);
	}

	@Override
	public void layout(boolean preview) {
		entries.clear();
		Player player = Minecraft.getInstance().player;
		if (preview) {
			entries.add(new Entry(MobEffects.SPEED, "Speed II", "1:24", 0.45f, false));
			entries.add(new Entry(MobEffects.FIRE_RESISTANCE, "Fire Resistance", "6:12", 0.8f, false));
		} else if (player != null) {
			float tickrate = player.level().tickRateManager().tickrate();
			List<Holder<MobEffect>> active = new ArrayList<>();
			for (MobEffectInstance effect : Ordering.natural().reverse().sortedCopy(player.getActiveEffects())) {
				if (!effect.showIcon()) {
					continue;
				}
				Holder<MobEffect> holder = effect.getEffect();
				active.add(holder);
				float left = 1f;
				if (!effect.isInfiniteDuration()) {
					int max = Math.max(effect.getDuration(), longest.getOrDefault(holder, 0));
					longest.put(holder, max);
					left = max <= 0 ? 0f : (float) effect.getDuration() / max;
				}
				String name = holder.value().getDisplayName().getString();
				if (effect.getAmplifier() > 0) {
					name += " " + Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)).getString();
				}
				entries.add(new Entry(holder, name, MobEffectUtil.formatDuration(effect, 1f, tickrate).getString(), left, effect.endsWithin(200)));
			}
			longest.keySet().retainAll(active);
		}
		float textW = 0f;
		for (Entry e : entries) {
			textW = Math.max(textW, Math.max(JacText.width(e.name, TEXT, JacText.Weight.SEMIBOLD), JacText.width(e.time, TEXT, JacText.Weight.REGULAR)));
		}
		width = PAD * 2 + RING + 6f + textW;
		// The panel eases taller or shorter as effects come and go, but appears at full size.
		float target = PAD * 2 + entries.size() * ROW_H - 3f;
		heightSpring.setTarget(target);
		if (preview || entries.isEmpty() || lastCount == 0) {
			heightSpring.snap(target);
		}
		lastCount = entries.size();
		height = heightSpring.update();
	}

	@Override
	public boolean hasContent() {
		return !entries.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		if (entries.isEmpty()) {
			return;
		}
		panel(g, width, height);
		float y = PAD;
		long now = System.currentTimeMillis();
		for (Entry e : entries) {
			float alpha = e.ending && blink.on() ? 0.55f + 0.45f * (float) Math.abs(Math.cos(now / 250.0)) : 1f;
			float cx = PAD + RING / 2f;
			float cy = y + RING / 2f;
			if (ring.on()) {
				int color = ringColor.get() == RingColor.ACCENT ? JacConfig.accent() : 0xFF000000 | e.effect.value().getColor();
				Draw.arc(g, cx, cy, RING / 2f, 1.5f, 0f, 1f, Theme.CONTROL);
				Draw.arc(g, cx, cy, RING / 2f, 1.5f, 0f, e.left, Colors.fade(color, alpha));
			}
			float icon = 9f;
			g.pose().pushMatrix();
			g.pose().translate(cx - icon / 2f, cy - icon / 2f);
			g.pose().scale(icon / 18f, icon / 18f);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.effect), 0, 0, 18, 18, Colors.fade(0xFFFFFFFF, alpha));
			g.pose().popMatrix();
			float tx = PAD + RING + 6f;
			float lh = JacText.lineHeight(TEXT);
			text(g, e.name, tx, cy - lh + 0.5f, Colors.fade(Theme.TEXT, alpha), TEXT, JacText.Weight.SEMIBOLD);
			text(g, e.time, tx, cy + 0.5f, Colors.fade(Theme.TEXT_DIM, alpha), TEXT, JacText.Weight.REGULAR);
			y += ROW_H;
		}
	}
}
