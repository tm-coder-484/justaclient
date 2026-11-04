package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Icons;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.jspecify.annotations.Nullable;

/**
 * The Dev Console: the game log, a diagnostics sheet, what each resource pack changes and a history of reloads, and the
 * list of screens the restyle has seen. Everything it shows is read locally.
 */
public final class DevConsoleFeature extends Feature {
	/** Folders in a pack that the audit counts, in the order it shows them. */
	public static final List<String> KINDS = List.of("textures", "models", "blockstates", "sounds", "lang", "font", "shaders", "particles",
		"atlases", "texts");
	private static final int HISTORY_MAX = 20;

	/** One resource reload: when, how long, which packs and what changed since the one before. */
	public record Reload(long at, List<String> packs, List<String> added, List<String> removed, long millis, boolean failed) {
	}

	/** What one pack holds: files per kind, how many replace vanilla files, and how many a higher pack hides. */
	public record PackAudit(String id, Map<String, Integer> kinds, int total, int vanilla, int hidden) {
	}

	private static final List<Reload> HISTORY = new ArrayList<>();
	private static List<PackResources> lastPacks = List.of();
	private static List<String> lastIds = List.of();
	private static @Nullable List<PackAudit> audit;
	private static boolean auditing;

	public DevConsoleFeature() {
		super("dev_console", "Dev Console", "Game log, diagnostics, resource pack audit and the screen list", Category.TOOLS, Icons.TERMINAL, true);
	}

	public static boolean on() {
		DevConsoleFeature f = Features.get(DevConsoleFeature.class);
		return f != null && f.isEnabled();
	}

	/** Pack ids without the "file/" folder prefix. */
	public static String packName(String id) {
		return id.startsWith("file/") ? id.substring(5) : id;
	}

	/** Called by the resource manager when a reload starts. Keeps the history even with the feature off, so it is there when turned on. */
	public static void reloadStarted(List<PackResources> packs, ReloadInstance instance) {
		long start = System.nanoTime();
		long at = System.currentTimeMillis();
		List<String> ids = new ArrayList<>();
		for (PackResources p : packs) {
			ids.add(packName(p.packId()));
		}
		List<String> added = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		if (!lastIds.isEmpty()) {
			Set<String> before = new HashSet<>(lastIds);
			Set<String> now = new HashSet<>(ids);
			ids.stream().filter(id -> !before.contains(id)).forEach(added::add);
			lastIds.stream().filter(id -> !now.contains(id)).forEach(removed::add);
		}
		lastIds = ids;
		lastPacks = List.copyOf(packs);
		audit = null;
		instance.done().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			synchronized (HISTORY) {
				HISTORY.add(0, new Reload(at, ids, added, removed, (System.nanoTime() - start) / 1_000_000L, error != null));
				while (HISTORY.size() > HISTORY_MAX) {
					HISTORY.remove(HISTORY.size() - 1);
				}
			}
		}));
	}

	public static List<Reload> history() {
		synchronized (HISTORY) {
			return new ArrayList<>(HISTORY);
		}
	}

	/** The audit of the packs in the last reload, highest priority first, or null while it is being worked out. */
	public static @Nullable List<PackAudit> audit() {
		if (audit == null && !auditing && !lastPacks.isEmpty()) {
			auditing = true;
			List<PackResources> packs = lastPacks;
			Thread t = new Thread(() -> {
				List<PackAudit> result = runAudit(packs);
				Minecraft.getInstance().execute(() -> {
					if (packs == lastPacks) {
						audit = result;
					}
					auditing = false;
				});
			}, "Justaclient pack audit");
			t.setDaemon(true);
			t.start();
		}
		return audit;
	}

	private static List<PackAudit> runAudit(List<PackResources> packs) {
		// The last pack in the list wins, so walk from the top down and count files an earlier (higher) pack already gave.
		Set<String> seen = new HashSet<>();
		List<PackAudit> out = new ArrayList<>();
		for (int i = packs.size() - 1; i >= 0; i--) {
			PackResources pack = packs.get(i);
			Map<String, Integer> kinds = new LinkedHashMap<>();
			int[] totals = new int[3];
			try {
				for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
					for (String kind : KINDS) {
						pack.listResources(PackType.CLIENT_RESOURCES, ns, kind, (id, supplier) -> {
							kinds.merge(kind, 1, Integer::sum);
							totals[0]++;
							if ("minecraft".equals(id.getNamespace()) && !"vanilla".equals(pack.packId())) {
								totals[1]++;
							}
							if (!seen.add(id.toString())) {
								totals[2]++;
							}
						});
					}
				}
			} catch (RuntimeException e) {
				// A pack closed by a newer reload; show what was counted.
			}
			out.add(new PackAudit(packName(pack.packId()), kinds, totals[0], totals[1], totals[2]));
		}
		return out;
	}
}
