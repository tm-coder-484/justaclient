package dev.justaclient.feature.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.storage.StorageIndex;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.mixin.DeathScreenAccessor;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.Theme;
import dev.justaclient.waypoint.Locate;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.world.WorldStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.justaclient.platform.events.ScreenEvents;
import dev.justaclient.platform.events.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * When you die, a card beside the death screen says how and where, what hit you in the last seconds, your health over
 * that time, your armour, food and level, how long you had been alive, and has a button to point the GPS back there.
 * {@code /lastdeath} shows it again with earlier deaths in this world. Only what your own game saw is used.
 */
public final class DeathRecapFeature extends Feature {
	private static final Gson GSON = new Gson();
	private static final int SAMPLES = 40;
	private static final long WINDOW = 10_000L;
	private static final float CARD_W = 180f;

	private final BoolSetting panel = bool("panel", "Card on the death screen", true);
	private final BoolSetting history = bool("history", "Keep earlier deaths (/lastdeath)", true);

	/** One hit taken: when, what kind of damage, who dealt it and with what, and how much health it took. */
	public static final class Hit {
		public long at;
		public String cause = "";
		public String attacker = "";
		public String weapon = "";
		public float amount;
	}

	/** Everything the card shows about one death. */
	public static final class Recap {
		public long time;
		public String message = "";
		public int x;
		public int y;
		public int z;
		public String dimension = "";
		public float[] health = new float[0];
		public float maxHealth = 20f;
		public List<Hit> hits = new ArrayList<>();
		public int armor;
		public int food;
		public int level;
		public long alive;
	}

	private final ArrayDeque<float[]> samples = new ArrayDeque<>();
	private final ArrayDeque<Hit> hits = new ArrayDeque<>();
	private @Nullable Recap last;
	private boolean wasDead = true;
	private long aliveSince;
	private float lastHealth = -1f;
	private int ticks;
	private @Nullable String world;
	private static final float[] GPS = new float[4];

	public DeathRecapFeature() {
		super("death_recap", "Death recap", "How and where you died, what hit you, and a way back", Category.UTILITY, Icons.SKULL, true);
	}

	public static DeathRecapFeature get() {
		return Features.get(DeathRecapFeature.class);
	}

	public static void init() {
		DeathRecapFeature feature = get();
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof DeathScreen death) || !feature.isEnabled() || !feature.panel.on()) {
				return;
			}
			// The death screen can open before the next tick notices the death, so the recap may be made here.
			if ((feature.last == null || System.currentTimeMillis() - feature.last.time > 15_000L) && client.player != null && !feature.wasDead) {
				feature.last = feature.build(client.player, System.currentTimeMillis());
				feature.wasDead = true;
				feature.saveHistory(feature.last, false);
			}
			Recap r = feature.last;
			if (r == null || System.currentTimeMillis() - r.time > 15_000L) {
				return;
			}
			String message = ((DeathScreenAccessor) death).jac$causeOfDeath().getString();
			if (!message.isBlank() && !message.equals(r.message)) {
				r.message = message;
				feature.saveHistory(r, true);
			}
			// The card sits left of the buttons, scaled down to fit a small window.
			float[] place = new float[3];
			InvCompat.afterDraw(screen, (s, g, mx, my, delta) -> {
				float avail = (s.width - 200) / 2f - 14f;
				float scale = Math.min(1f, Math.min(avail / CARD_W, (s.height - 16f) / cardHeight(r, CARD_W)));
				if (scale < 0.55f) {
					return;
				}
				place[0] = 8f;
				place[1] = Math.max(8f, (s.height - cardHeight(r, CARD_W) * scale) / 2f);
				place[2] = scale;
				g.pose().pushPose();
				g.pose().translate(place[0], place[1], 0);
				g.pose().scale(scale, scale, 1);
				drawCard(g, r, 0f, 0f, CARD_W, (int) ((mx - place[0]) / scale), (int) ((my - place[1]) / scale), 1f);
				g.pose().popPose();
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !(InvCompat.isLeft(event.button()) && place[2] > 0
				&& clickCard(r, (event.x() - place[0]) / place[2], (event.y() - place[1]) / place[2])));
		});
	}

	/** Called for every damage event the server sends; only hits on you are kept. */
	public static void onHurt(Entity entity, String type, int causeId, int directId) {
		Minecraft mc = Minecraft.getInstance();
		DeathRecapFeature feature = get();
		if (entity != mc.player || mc.level == null || !feature.isEnabled()) {
			return;
		}
		Hit hit = new Hit();
		hit.at = System.currentTimeMillis();
		hit.cause = damageName(type);
		Entity cause = causeId >= 0 ? mc.level.getEntity(causeId) : null;
		Entity direct = directId >= 0 ? mc.level.getEntity(directId) : null;
		if (cause != null) {
			hit.attacker = cause.getName().getString();
			if (cause instanceof LivingEntity living && !living.getMainHandItem().isEmpty()) {
				hit.weapon = living.getMainHandItem().getHoverName().getString();
			}
		}
		if (direct != null && direct != cause) {
			// A projectile: name what flew, such as an arrow or a fireball.
			hit.weapon = direct.getName().getString();
		}
		feature.hits.addLast(hit);
		while (feature.hits.size() > 12) {
			feature.hits.removeFirst();
		}
	}

	/** "minecraft:mob_attack" as "Mob attack". */
	public static String damageName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		path = path.replace('_', ' ').trim();
		return path.isEmpty() ? "Damage" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		String key = Waypoints.worldKey();
		if (key != null && !key.equals(world)) {
			world = key;
			samples.clear();
			hits.clear();
			last = null;
			aliveSince = now;
			wasDead = player.isDeadOrDying();
		}
		float health = player.getHealth() + player.getAbsorptionAmount();
		if (lastHealth >= 0 && health < lastHealth) {
			// The health packet comes after the damage event: give the drop to the newest hit still without one.
			Hit newest = hits.peekLast();
			if (newest != null && newest.amount == 0 && now - newest.at < 1500) {
				newest.amount = lastHealth - health;
			}
		}
		lastHealth = health;
		if (++ticks % 5 == 0) {
			samples.addLast(new float[] {now, health});
			while (samples.size() > SAMPLES) {
				samples.removeFirst();
			}
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			last = build(player, now);
			saveHistory(last, false);
		} else if (!dead && wasDead) {
			aliveSince = now;
			samples.clear();
			hits.clear();
		}
		wasDead = dead;
	}

	private Recap build(LocalPlayer player, long now) {
		// The killing blow's health drop may not have been seen by a tick yet.
		float health = player.getHealth() + player.getAbsorptionAmount();
		Hit newest = hits.peekLast();
		if (newest != null && newest.amount == 0 && now - newest.at < 1500 && lastHealth > health) {
			newest.amount = lastHealth - health;
			lastHealth = health;
		}
		Recap r = new Recap();
		r.time = now;
		r.x = player.getBlockX();
		r.y = player.getBlockY();
		r.z = player.getBlockZ();
		r.dimension = Waypoints.dimension();
		r.maxHealth = player.getMaxHealth() + player.getAbsorptionAmount();
		List<Float> hp = new ArrayList<>();
		for (float[] s : samples) {
			if (now - (long) s[0] <= WINDOW) {
				hp.add(s[1]);
			}
		}
		hp.add(0f);
		r.health = new float[hp.size()];
		for (int i = 0; i < hp.size(); i++) {
			r.health[i] = hp.get(i);
			r.maxHealth = Math.max(r.maxHealth, hp.get(i));
		}
		for (Hit h : hits) {
			if (now - h.at <= WINDOW) {
				r.hits.add(h);
			}
		}
		r.armor = player.getArmorValue();
		r.food = player.getFoodData().getFoodLevel();
		r.level = player.experienceLevel;
		r.alive = aliveSince > 0 ? now - aliveSince : 0;
		Hit lastHit = r.hits.isEmpty() ? null : r.hits.get(r.hits.size() - 1);
		r.message = lastHit == null ? "You died" : lastHit.cause + (lastHit.attacker.isEmpty() ? "" : " · " + lastHit.attacker);
		return r;
	}

	public @Nullable Recap last() {
		return last;
	}

	/** Deaths in this world, newest first. */
	public List<Recap> history() {
		JsonElement json = WorldStore.read("deaths");
		if (json == null) {
			return last == null ? List.of() : List.of(last);
		}
		try {
			List<Recap> list = GSON.fromJson(json, new TypeToken<List<Recap>>() {
			}.getType());
			return list == null ? List.of() : list.stream().filter(r -> r != null && r.health != null && r.hits != null).toList();
		} catch (RuntimeException e) {
			return last == null ? List.of() : List.of(last);
		}
	}

	private void saveHistory(Recap r, boolean replaceNewest) {
		if (!history.on()) {
			return;
		}
		List<Recap> list = new ArrayList<>(history());
		if (replaceNewest && !list.isEmpty() && list.get(0).time == r.time) {
			list.set(0, r);
		} else if (list.isEmpty() || list.get(0).time != r.time) {
			list.add(0, r);
		}
		while (list.size() > 20) {
			list.remove(list.size() - 1);
		}
		WorldStore.write("deaths", GSON.toJsonTree(list));
	}

	// The card, shared by the death screen and /lastdeath.

	public static float cardHeight(Recap r, float w) {
		int lines = JacScreen.wrap(r.message, Theme.LABEL, JacText.Weight.SEMIBOLD, w - 20).size();
		return 34 + lines * 10 + 26 + 44 + Math.min(3, Math.max(1, r.hits.size())) * 11 + 22 + 26;
	}

	/** Draws the card; returns its height. The GPS button's place is remembered for {@link #clickCard}. */
	public static float drawCard(GuiGraphics g, Recap r, float x, float y, float w, int mx, int my, float a) {
		int red = 0xFFF87171;
		float h = cardHeight(r, w);
		Draw.panel(g, x, y, w, h, a);
		JacText.icon(g, Icons.SKULL, x + 10, y + 10, 9f, Colors.fade(red, a));
		JacText.draw(g, "Death recap", x + 23, y + 10, Colors.fade(Theme.TEXT, a), Theme.LABEL + 1, JacText.Weight.SEMIBOLD);
		String when = StorageIndex.ago(r.time, System.currentTimeMillis());
		JacText.draw(g, when, x + w - 10 - JacText.width(when, Theme.SMALL, JacText.Weight.REGULAR), y + 11, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		float ly = y + 26;
		for (String line : JacScreen.wrap(r.message, Theme.LABEL, JacText.Weight.SEMIBOLD, w - 20)) {
			JacText.draw(g, line, x + 10, ly, Colors.fade(red, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			ly += 10;
		}
		ly += 4;
		String where = StreamModeFeature.hidesCoordinates() ? dimensionName(r.dimension) : r.x + " " + r.y + " " + r.z + " · " + dimensionName(r.dimension);
		row(g, Icons.MAP_PIN, where, x + 10, ly, w - 20, a);
		row(g, Icons.CLOCK, "Alive for " + duration(r.alive), x + 10, ly + 11, w - 20, a);
		ly += 26;

		// Health over the last seconds, one bar per quarter second, with the hits marked.
		JacText.draw(g, "Health, last 10 s", x + 10, ly, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		float gx = x + 10;
		float gy = ly + 10;
		float gw = w - 20;
		float gh = 26;
		Draw.rect(g, gx, gy, gw, gh, 3f, Colors.fade(Theme.CONTROL, a));
		int n = Math.max(1, r.health.length);
		float bw = gw / SAMPLES;
		float start = gx + gw - n * bw;
		for (int i = 0; i < r.health.length; i++) {
			float f = Math.max(0f, Math.min(1f, r.health[i] / Math.max(1f, r.maxHealth)));
			int c = f > 0.5f ? 0xFF4ADE80 : f > 0.25f ? 0xFFFBBF24 : red;
			float bh = Math.max(1f, (gh - 4) * f);
			Draw.rect(g, start + i * bw + 0.3f, gy + gh - 2 - bh, Math.max(0.8f, bw - 0.6f), bh, 0.5f, Colors.fade(c, a));
		}
		for (Hit hit : r.hits) {
			float t = 1f - (r.time - hit.at) / (float) WINDOW;
			if (t >= 0f && t <= 1f) {
				Draw.circle(g, gx + gw * t, gy + 3, 1.6f, Colors.fade(0xFFFFFFFF, a));
			}
		}
		ly = gy + gh + 8;

		JacText.draw(g, "What hit you", x + 10, ly, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		ly += 10;
		if (r.hits.isEmpty()) {
			JacText.draw(g, "No hits in the last 10 s", x + 10, ly, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			ly += 11;
		}
		List<Hit> recent = r.hits.subList(Math.max(0, r.hits.size() - 3), r.hits.size());
		for (int i = recent.size() - 1; i >= 0; i--) {
			Hit hit = recent.get(i);
			String amount = hit.amount > 0 ? String.format(Locale.ROOT, "-%.1f", hit.amount) : "";
			String who = hit.attacker.isEmpty() ? hit.cause : hit.attacker + (hit.weapon.isEmpty() ? "" : " (" + hit.weapon + ")");
			String ago = String.format(Locale.ROOT, "%.1f s", (r.time - hit.at) / 1000f);
			float aw = JacText.width(ago, Theme.SMALL, JacText.Weight.REGULAR);
			float amw = JacText.width(amount, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, amount, x + 10, ly, Colors.fade(red, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, JacScreen.fit(who, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 30 - amw - aw), x + 14 + amw, ly,
				Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			JacText.draw(g, ago, x + w - 10 - aw, ly, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			ly += 11;
		}
		ly += 3;
		String stats = "Armour " + r.armor + " · Food " + r.food + " · Level " + r.level;
		JacText.draw(g, JacScreen.fit(stats, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 20), x + 10, ly, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL + 0.5f, JacText.Weight.REGULAR);

		// Point the GPS back here.
		float by = y + h - 26;
		boolean tracked = Locate.isTracked(target(r));
		String label = tracked ? "GPS points here" : "Point GPS here";
		boolean hot = mx >= x + 10 && mx < x + w - 10 && my >= by && my < by + 18;
		int accent = JacConfig.accent();
		Draw.rect(g, x + 10, by, w - 20, 18, 5f, Colors.fade(tracked ? Colors.withAlpha(accent, 0x40) : hot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f)
			: accent, a));
		float lw = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 12;
		JacText.icon(g, tracked ? Icons.CHECK : Icons.NAVIGATION, x + (w - lw) / 2f, by + 5.5f, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, label, x + (w - lw) / 2f + 12, by + 5.5f, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		GPS[0] = x + 10;
		GPS[1] = by;
		GPS[2] = w - 20;
		GPS[3] = 18;
		return h;
	}

	/** Handles a click on the card's GPS button; true if it was hit. */
	public static boolean clickCard(Recap r, double mx, double my) {
		if (mx < GPS[0] || mx >= GPS[0] + GPS[2] || my < GPS[1] || my >= GPS[1] + GPS[3]) {
			return false;
		}
		GuiSoundsFeature.menuClick();
		Locate.Target t = target(r);
		if (Locate.isTracked(t)) {
			Locate.clear();
		} else {
			Locate.track(t);
			Notifications.push(Icons.NAVIGATION, "GPS set", "Where you died");
		}
		return true;
	}

	private static Locate.Target target(Recap r) {
		return new Locate.Target("Where you died", Icons.SKULL, r.x, r.y, r.z, r.dimension, 0xFFF87171);
	}

	private static void row(GuiGraphics g, Icons icon, String text, float x, float y, float w, float a) {
		JacText.icon(g, icon, x, y + 0.5f, 6.5f, Colors.fade(Theme.TEXT_DIM, a));
		JacText.draw(g, JacScreen.fit(text, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 10), x + 10, y, Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f,
			JacText.Weight.REGULAR);
	}

	private static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> damageName(id);
		};
	}

	/** "1:02:03", "12:34" or "45 s". */
	public static String duration(long ms) {
		long s = ms / 1000;
		if (s < 60) {
			return s + " s";
		}
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
