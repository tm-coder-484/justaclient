package dev.justaclient.feature.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Icons;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * PvP tier badges from the public MC Tiers list next to player names, in name tags and the tab list. It is public
 * ranking data anyone can look up on the website; nothing about the game is changed or revealed.
 */
public final class TiersFeature extends Feature {
	public enum Mode implements ChoiceSetting.Named {
		BEST("Best tier", null), VANILLA("Vanilla", "vanilla"), SWORD("Sword", "sword"), AXE("Axe", "axe"), POT("Pot", "pot"), NETHOP("Neth Pot", "nethop"),
		UHC("UHC", "uhc"), SMP("SMP", "smp"), MACE("Mace", "mace");

		private final String label;
		final @Nullable String slug;

		Mode(String label, @Nullable String slug) {
			this.label = label;
			this.slug = slug;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** One ranking: tier 1 is the best, high is the upper half of a tier (HT) and low the lower (LT). */
	public record Tier(int tier, boolean high, boolean retired, String mode) {
		String label() {
			return (retired ? "R" : "") + (high ? "HT" : "LT") + tier;
		}

		/** Lower is better, for picking the best tier across modes. */
		int rank() {
			return tier * 2 + (high ? 0 : 1) + (retired ? 100 : 0);
		}
	}

	private record Entry(@Nullable Map<String, Tier> tiers, long fetchedAt) {
	}

	private static final long TTL = 30 * 60_000L;
	private static final long RETRY = 5 * 60_000L;

	private final ChoiceSetting<Mode> mode = choice("mode", "Show", Mode.BEST);
	private final BoolSetting nameTags = bool("name_tags", "In name tags", true);
	private final BoolSetting tabList = bool("tab_list", "In the tab list", true);
	private final BoolSetting retired = bool("retired", "Show retired tiers", false);
	private final TextSetting api = add(new TextSetting("api", "API address", "https://mctiers.com/api/v2", 96, "https://mctiers.com/api/v2"));

	private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> queued = new ConcurrentHashMap<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Justaclient tiers");
		t.setDaemon(true);
		return t;
	});
	private volatile long pausedUntil;
	private volatile int failures;
	private @Nullable HttpClient http;

	public TiersFeature() {
		super("tiers", "Tier badges", "PvP tiers from MC Tiers next to player names", Category.UTILITY, Icons.TROPHY, false);
	}

	public static TiersFeature get() {
		return Features.get(TiersFeature.class);
	}

	/** {@code name} with the player's badge in front, or unchanged if they have none, it is still loading, or it is off. */
	public Component decorate(Component name, @Nullable UUID id, boolean nameTag) {
		if (!isEnabled() || id == null || (nameTag ? !nameTags.on() : !tabList.on())) {
			return name;
		}
		Tier tier = tier(id);
		if (tier == null) {
			return name;
		}
		MutableComponent badge = Component.literal(tier.label()).withColor(color(tier));
		return Component.empty().append(badge).append(Component.literal(" | ").withColor(0x808080)).append(name);
	}

	private static int color(Tier t) {
		if (t.retired()) {
			return 0x9CA3AF;
		}
		return switch (t.tier()) {
			case 1 -> t.high() ? 0xFFCF4A : 0xD5B355;
			case 2 -> t.high() ? 0xC4D3E7 : 0xA0A7B2;
			case 3 -> t.high() ? 0xF89F5A : 0xC67B42;
			case 4 -> t.high() ? 0x81749A : 0x655B79;
			default -> t.high() ? 0x8F82A8 : 0x6C7178;
		};
	}

	private @Nullable Tier tier(UUID id) {
		// Offline-mode and NPC players have version 3 or 2 UUIDs; only real accounts can be on the list.
		if (id.version() != 4) {
			return null;
		}
		Entry e = cache.get(id);
		long now = System.currentTimeMillis();
		if (e == null || now - e.fetchedAt() > (e.tiers() == null ? RETRY : TTL)) {
			fetch(id);
		}
		if (e == null || e.tiers() == null) {
			return null;
		}
		Mode m = mode.get();
		if (m.slug != null) {
			Tier t = e.tiers().get(m.slug);
			return t != null && (retired.on() || !t.retired()) ? t : null;
		}
		Tier best = null;
		for (Tier t : e.tiers().values()) {
			if ((retired.on() || !t.retired()) && (best == null || t.rank() < best.rank())) {
				best = t;
			}
		}
		return best;
	}

	private void fetch(UUID id) {
		if (System.currentTimeMillis() < pausedUntil || queued.putIfAbsent(id, Boolean.TRUE) != null) {
			return;
		}
		worker.execute(() -> {
			try {
				cache.put(id, new Entry(load(id), System.currentTimeMillis()));
				failures = 0;
				// Stay gentle with a free public API.
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				// Network trouble: remember the miss for a while and back off after a few in a row.
				cache.put(id, new Entry(null, System.currentTimeMillis()));
				if (++failures >= 3) {
					pausedUntil = System.currentTimeMillis() + RETRY;
					failures = 0;
					Justaclient.LOGGER.info("Tier lookups paused for five minutes: {}", e.toString());
				}
			} finally {
				queued.remove(id);
			}
		});
	}

	/** Fetches one profile. Returns an empty map for players who are not on the list. */
	private Map<String, Tier> load(UUID id) throws Exception {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
		}
		String base = api.get().trim();
		if (base.isEmpty()) {
			base = "https://mctiers.com/api/v2";
		}
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String plain = id.toString().replace("-", "");
		HttpResponse<String> res = request(base + "/profile/" + plain);
		if (res.statusCode() == 404 || res.statusCode() == 400) {
			res = request(base + "/profile/" + id);
		}
		if (res.statusCode() == 404) {
			return Map.of();
		}
		if (res.statusCode() / 100 != 2) {
			throw new IllegalStateException("HTTP " + res.statusCode());
		}
		return parse(res.body());
	}

	private HttpResponse<String> request(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent", "Justaclient (Minecraft mod)")
			.header("Accept", "application/json").GET().build();
		return http.send(req, HttpResponse.BodyHandlers.ofString());
	}

	/** Reads {@code rankings: {mode: {tier, pos, retired}}}; pos 0 is the high half of the tier. Unknown fields are ignored. */
	static Map<String, Tier> parse(String body) {
		Map<String, Tier> out = new ConcurrentHashMap<>();
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonObject()) {
			return out;
		}
		JsonObject obj = root.getAsJsonObject();
		if (obj.has("data") && obj.get("data").isJsonObject()) {
			obj = obj.getAsJsonObject("data");
		}
		JsonElement rankings = obj.get("rankings");
		if (rankings == null || !rankings.isJsonObject()) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : rankings.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject r = e.getValue().getAsJsonObject();
			if (!r.has("tier")) {
				continue;
			}
			int tier = r.get("tier").getAsInt();
			int pos = r.has("pos") ? r.get("pos").getAsInt() : 1;
			boolean ret = r.has("retired") && r.get("retired").getAsBoolean();
			if (tier >= 1 && tier <= 5) {
				out.put(e.getKey(), new Tier(tier, pos == 0, ret, e.getKey()));
			}
		}
		return out;
	}

	@Override
	protected void onDisable() {
		cache.clear();
	}
}
