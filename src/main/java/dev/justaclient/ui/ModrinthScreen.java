package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.modrinth.Modrinth;
import dev.justaclient.common.modrinth.ModrinthApi;
import dev.justaclient.common.mods.ManagedMod;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.common.mods.ModStatus;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.ModrinthFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The Modrinth browser (opened from the Mods panel): search mods, shaders, resource packs and data packs made for this
 * game version, see what is already installed, and install into the right folder. Mods ask for a restart through the
 * Mods panel's restart path; shaders warn when Iris is missing; data packs ask which world they are for.
 */
public final class ModrinthScreen extends JacScreen {
	private static final float ROW_H = 30f;
	private static final float PAD = 12f;
	private static final int OK = 0xFF34D399;
	private static final int WARN = 0xFFFBBF24;
	private static final int BAD = 0xFFF87171;
	private static final int PAGE = 20;
	/** Modrinth files loaders and platforms as categories; they say nothing about the project here, so they are hidden. */
	private static final Set<String> LOADERS = Set.of("fabric", "quilt", "forge", "neoforge", "minecraft", "iris", "optifine", "canvas", "vanilla",
		"datapack", "bukkit", "spigot", "paper", "purpur", "folia", "sponge", "velocity", "bungeecord", "waterfall", "liteloader", "modloader", "rift",
		"babric", "bta-babric", "java-agent", "legacy-fabric", "nilloader", "ornithe", "risugami");
	/** Mods installed from here this session, for the restart prompt (kept while the game runs). */
	private static final Set<String> INSTALLED_THIS_SESSION = new HashSet<>();

	private Modrinth.Kind kind = Modrinth.Kind.MOD;
	private Modrinth.Sort sort = Modrinth.Sort.RELEVANCE;
	private final InputBox search = new InputBox("", 64, "Search Modrinth", Icons.SEARCH);
	private final Scroller scroll = new Scroller();
	private final Scroller detailScroll = new Scroller();
	private final List<Modrinth.Hit> hits = new ArrayList<>();
	private int total;
	private boolean loading;
	private @Nullable String error;
	private int requestId;
	private long searchAt;
	private Modrinth.@Nullable Hit selected;
	private final Map<String, List<Modrinth.Version>> versions = new HashMap<>();
	private final Map<String, String> versionErrors = new HashMap<>();
	/** What is installed, by Modrinth project id, for the current kind's folder: the file and its version id. */
	private final Map<String, Installed> installed = new HashMap<>();
	private final Map<String, Job> jobs = new LinkedHashMap<>();
	private @Nullable Path world;
	private List<Path> worlds = List.of();
	private final List<Button> buttons = new ArrayList<>();
	private final List<Runnable> chipActions = new ArrayList<>();
	private final List<float[]> chipBoxes = new ArrayList<>();

	private record Installed(Path file, String versionId) {
	}

	/** An install in progress or finished, shown on the project. */
	private static final class Job {
		volatile String label;
		volatile double progress = -1;
		volatile int color = Theme.TEXT_DIM;
		volatile boolean done;
		volatile boolean failed;
		@Nullable Path file;

		Job(String label) {
			this.label = label;
		}
	}

	// Layout.
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float detailX;
	private float tabsY;

	public ModrinthScreen(@Nullable Screen parent) {
		super("Modrinth", parent);
	}

	private static ModrinthFeature feature() {
		return ModrinthFeature.get();
	}

	@Override
	protected void setup() {
		worlds = listWorlds();
		if (world == null) {
			world = currentWorld();
			if (world == null && !worlds.isEmpty()) {
				world = worlds.getFirst();
			}
		}
		if (feature().isEnabled() && hits.isEmpty() && !loading) {
			runSearch(false);
			scanInstalled();
		}
	}

	// Data

	private void runSearch(boolean more) {
		if (!feature().isEnabled()) {
			return;
		}
		int id = ++requestId;
		int offset = more ? hits.size() : 0;
		if (!more) {
			hits.clear();
			total = 0;
			scroll.scrollTo(0);
			scroll.snap();
		}
		loading = true;
		error = null;
		feature().api().search(search.get(), kind, ModrinthFeature.gameVersion(), "fabric", sort, offset, PAGE)
			.whenComplete((page, e) -> minecraft.execute(() -> {
				if (id != requestId) {
					return;
				}
				loading = false;
				if (e != null) {
					error = message(e);
					return;
				}
				hits.addAll(page.hits());
				total = page.total();
				if (!hits.isEmpty() && (selected == null || !more && hits.stream().noneMatch(hit -> hit.id().equals(selected.id())))) {
					select(hits.getFirst());
				}
			}));
	}

	private static String message(Throwable e) {
		Throwable t = e;
		while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t.getMessage() == null)) {
			t = t.getCause();
		}
		if (t instanceof java.net.ConnectException || t instanceof java.net.http.HttpConnectTimeoutException) {
			return "Could not reach Modrinth. Check your connection.";
		}
		String m = t.getMessage();
		return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
	}

	private void select(Modrinth.Hit hit) {
		if (selected == null || !selected.id().equals(hit.id())) {
			detailScroll.scrollTo(0);
			detailScroll.snap();
		}
		selected = hit;
		String key = kind + ":" + hit.id();
		if (!versions.containsKey(key) && !versionErrors.containsKey(key)) {
			versions.put(key, null);
			feature().api().versions(hit.id(), kind, ModrinthFeature.gameVersion(), "fabric").whenComplete((list, e) -> minecraft.execute(() -> {
				if (e != null) {
					versions.remove(key);
					versionErrors.put(key, message(e));
				} else {
					versions.put(key, list);
				}
			}));
		}
	}

	private @Nullable List<Modrinth.Version> versionsOf(Modrinth.Hit hit) {
		return versions.get(kind + ":" + hit.id());
	}

	private Modrinth.@Nullable Version best(List<Modrinth.Version> list) {
		if (feature().betas.on()) {
			return Modrinth.best(list);
		}
		for (Modrinth.Version v : list) {
			if ("release".equals(v.type()) && v.primaryFile() != null) {
				return v;
			}
		}
		return null;
	}

	/** The folder this kind installs into (for data packs, the chosen world's). */
	private @Nullable Path folder(Modrinth.Kind k) {
		Path game = FabricLoader.getInstance().getGameDir();
		return switch (k) {
			case MOD -> ModManager.modsFolder();
			case SHADER -> game.resolve("shaderpacks");
			case RESOURCE_PACK -> minecraft.getResourcePackDirectory();
			case DATA_PACK -> world == null ? null : world.resolve("datapacks");
		};
	}

	/** Hashes what is in the kind's folder and asks Modrinth which projects those files are. */
	private void scanInstalled() {
		Modrinth.Kind k = kind;
		Path dir = folder(k);
		installed.clear();
		if (dir == null || !Files.isDirectory(dir)) {
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			Map<String, Path> byHash = new HashMap<>();
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
					if (Files.isRegularFile(p) && (name.endsWith(".jar") || name.endsWith(".zip")) && Files.size(p) < ModrinthApi.MAX_BYTES) {
						byHash.put(Modrinth.sha1(Files.readAllBytes(p)), p);
					}
				}
			} catch (IOException e) {
				Justaclient.LOGGER.debug("Could not list {}", dir, e);
			}
			return byHash;
		}).thenCompose(byHash -> feature().api().identify(new ArrayList<>(byHash.keySet())).thenApply(found -> {
			Map<String, Installed> out = new HashMap<>();
			found.forEach((hash, v) -> out.put(v.projectId(), new Installed(byHash.get(hash), v.id())));
			return out;
		})).whenComplete((out, e) -> minecraft.execute(() -> {
			if (e == null && k == kind) {
				installed.putAll(out);
			}
		}));
	}

	private boolean modLoaded(Modrinth.Hit hit) {
		return kind == Modrinth.Kind.MOD && (FabricLoader.getInstance().isModLoaded(hit.slug())
			|| FabricLoader.getInstance().isModLoaded(hit.slug().replace('-', '_')));
	}

	private static List<Path> listWorlds() {
		Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");
		if (!Files.isDirectory(saves)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(saves)) {
			return s.filter(p -> Files.isRegularFile(p.resolve("level.dat"))).sorted(Comparator.comparing((Path p) -> {
				try {
					return Files.getLastModifiedTime(p.resolve("level.dat")).toMillis();
				} catch (IOException e) {
					return 0L;
				}
			}).reversed()).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private @Nullable Path currentWorld() {
		var server = minecraft.getSingleplayerServer();
		if (server == null) {
			return null;
		}
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	// Installing

	private void install(Modrinth.Hit hit, Modrinth.Version version) {
		install(hit, version, kind, hit.id());
	}

	/** Downloads {@code version} of {@code hit} into {@code k}'s folder, tracked on the page as {@code jobKey}. */
	private void install(Modrinth.Hit hit, Modrinth.Version version, Modrinth.Kind k, String jobKey) {
		Path dir = folder(k);
		Modrinth.FileRef file = version.primaryFile();
		if (dir == null || file == null) {
			return;
		}
		String name = Modrinth.safeFileName(file.filename(), k);
		Job job = new Job("Downloading");
		jobs.put(jobKey, job);
		if (name == null) {
			fail(job, "Modrinth's file is not a " + (k == Modrinth.Kind.MOD ? ".jar" : ".zip") + ", so it was not installed");
			return;
		}
		if (Files.exists(dir.resolve(name))) {
			job.done = true;
			job.label = "Already in the folder: " + name;
			job.color = OK;
			job.file = dir.resolve(name);
			return;
		}
		Installed before = installed.get(hit.id());
		feature().api().download(file, dir, name, p -> job.progress = p).whenComplete((done, e) -> minecraft.execute(() -> {
			if (e != null) {
				fail(job, message(e));
				return;
			}
			job.file = done.path();
			job.progress = -1;
			job.done = true;
			job.color = OK;
			if (k == kind) {
				installed.put(hit.id(), new Installed(done.path(), version.id()));
			}
			switch (k) {
				case MOD -> {
					INSTALLED_THIS_SESSION.add(name);
					job.label = "Installed and checked. Restart to load it";
					if (before != null && !before.file().equals(done.path())) {
						job.label += turnOffOld(before.file());
					}
				}
				case SHADER -> job.label = "Installed. Pick it in Video Settings > Shader Packs";
				case RESOURCE_PACK -> job.label = "Installed. Turn it on below or in Resource Packs";
				case DATA_PACK -> job.label = world != null && world.equals(currentWorld())
					? "Installed in this world. Run /reload (or rejoin) to load it"
					: "Installed. It loads next time you open " + world.getFileName();
			}
			if (k == Modrinth.Kind.MOD && feature().dependencies.on()) {
				installDependencies(version);
			}
		}));
	}

	/** After an update, switches the old jar off the same way the Mods panel does (it is renamed when the game closes). */
	private static String turnOffOld(Path old) {
		for (ManagedMod m : ModManager.scan()) {
			if (old.equals(m.file()) && (m.status() == ModStatus.ENABLED || m.status() == ModStatus.ENABLE_PENDING)) {
				ModManager.toggle(m);
				return "; the old version is turned off";
			}
		}
		return "";
	}

	private void fail(Job job, String why) {
		job.failed = true;
		job.done = true;
		job.progress = -1;
		job.label = why;
		job.color = BAD;
	}

	/** Installs the mods {@code version} needs that are not here yet. */
	private void installDependencies(Modrinth.Version version) {
		for (Modrinth.Dependency d : version.dependencies()) {
			if (!d.required() || d.projectId() == null || installed.containsKey(d.projectId()) || jobs.containsKey(d.projectId())) {
				continue;
			}
			String dep = d.projectId();
			feature().api().project(dep).thenCombine(feature().api().versions(dep, Modrinth.Kind.MOD, ModrinthFeature.gameVersion(), "fabric"),
				(project, list) -> new Object[] {project, list}).whenComplete((pair, e) -> minecraft.execute(() -> {
					if (e != null) {
						return;
					}
					Modrinth.Project project = (Modrinth.Project) pair[0];
					@SuppressWarnings("unchecked")
					List<Modrinth.Version> list = (List<Modrinth.Version>) pair[1];
					if (FabricLoader.getInstance().isModLoaded(project.slug()) || FabricLoader.getInstance().isModLoaded(project.slug().replace('-', '_'))) {
						return;
					}
					Modrinth.Version v = best(list);
					if (v != null && !jobs.containsKey(dep)) {
						install(new Modrinth.Hit(project.id(), project.slug(), project.title(), "", "", 0, 0, List.of(), "mod"), v, Modrinth.Kind.MOD, dep);
					}
				}));
		}
	}

	private void turnOnPack(Path file) {
		PackRepository repo = minecraft.getResourcePackRepository();
		repo.reload();
		String id = "file/" + file.getFileName();
		if (!repo.getAvailableIds().contains(id)) {
			return;
		}
		List<String> selected = new ArrayList<>(repo.getSelectedIds());
		if (!selected.contains(id)) {
			selected.add(id);
			repo.setSelected(selected);
			minecraft.options.updateResourcePacks(repo);
		}
	}

	private void installIris() {
		feature().api().versions("iris", Modrinth.Kind.MOD, ModrinthFeature.gameVersion(), "fabric").whenComplete((list, e) -> minecraft.execute(() -> {
			Modrinth.Version v = e == null ? best(list) : null;
			if (v == null) {
				Job job = new Job("");
				jobs.put("iris", job);
				fail(job, e != null ? message(e) : "No Iris for this game version yet");
				return;
			}
			install(new Modrinth.Hit(v.projectId(), "iris", "Iris", "", "", 0, 0, List.of(), "mod"), v, Modrinth.Kind.MOD, "iris");
		}));
	}

	// Drawing

	private void layout() {
		pw = Math.min(600f, width - 24f);
		ph = Math.min(360f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		listX = px + PAD;
		listW = Math.max(160f, pw * 0.45f);
		tabsY = py + 40;
		listY = tabsY + 20;
		listH = py + ph - 34 - listY;
		detailX = listX + listW + PAD;
		search.place(px + pw - PAD - 160 - 20, py + 10, 160);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		float off = (1f - t) * 8f;
		Draw.panel(g, px, py + off, pw, ph, a);
		int accent = JacConfig.accent();
		buttons.clear();
		chipActions.clear();
		chipBoxes.clear();
		JacText.icon(g, Icons.DOWNLOAD, px + PAD, py + PAD + 1 + off, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Browse Modrinth", px + PAD + 15, py + PAD + off, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "For Minecraft " + ModrinthFeature.gameVersion() + " with Fabric", px + PAD + 15, py + PAD + 12 + off,
			Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		boolean closeHot = mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (!feature().isEnabled()) {
			drawOff(g, mx, my, a);
			return;
		}
		search.draw(g, mx, my, a);
		// Kind tabs and the sort chip.
		float cx = listX;
		for (Modrinth.Kind k : Modrinth.Kind.values()) {
			cx += chip(g, mx, my, a, cx, tabsY, k.label, k == kind, () -> {
				if (kind != k) {
					kind = k;
					selected = null;
					runSearch(false);
					scanInstalled();
				}
			}) + 4;
		}
		String sortLabel = "Sort: " + sort.label;
		float sw = JacText.width(sortLabel, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
		chip(g, mx, my, a, px + pw - PAD - sw, tabsY, sortLabel, false, () -> {
			sort = Modrinth.Sort.values()[(sort.ordinal() + 1) % Modrinth.Sort.values().length];
			runSearch(false);
		});
		drawList(g, mx, my, a, accent);
		drawDetail(g, mx, my, a, accent);
		drawFooter(g, mx, my, a);
		if (!search.get().isEmpty() && searchAt > 0 && System.currentTimeMillis() - searchAt > 350) {
			searchAt = 0;
			runSearch(false);
		}
	}

	private float chip(GuiGraphics g, int mx, int my, float a, float x, float y, String label, boolean on, Runnable action) {
		float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + 13;
		int accent = JacConfig.accent();
		Draw.rect(g, x, y, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
		JacText.draw(g, label, x + 6, y + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		chipBoxes.add(new float[] {x, y, w, 13});
		chipActions.add(action);
		return w;
	}

	private void drawOff(GuiGraphics g, int mx, int my, float a) {
		float cw = Math.min(320, pw - 40);
		float cx = px + (pw - cw) / 2f;
		float cy = py + ph / 2f - 50;
		Draw.rect(g, cx, cy, cw, 96, 8f, Colors.fade(0x18000000, a));
		JacText.icon(g, Icons.GLOBE, cx + 14, cy + 15, 10f, Colors.fade(JacConfig.accent(), a));
		JacText.draw(g, "Browsing Modrinth is off", cx + 30, cy + 14, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		float y = cy + 30;
		for (String line : wrap("It goes online to api.modrinth.com to search, and downloads from cdn.modrinth.com when you install. "
			+ "Nothing is sent about you beyond the search.", Theme.SMALL, JacText.Weight.REGULAR, cw - 28)) {
			JacText.draw(g, line, cx + 14, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			y += 9;
		}
		Button on = new Button(cx + 14, cy + 70, 110, 16, Icons.POWER, "Turn it on", true, () -> {
			feature().setEnabled(true);
			dev.justaclient.profile.Profiles.save();
			setup();
		});
		buttons.add(on);
		on.render(g, mx, my, a);
	}

	private void drawList(GuiGraphics g, int mx, int my, float a, int accent) {
		scroll.setMax(hits.size() * ROW_H + (hits.size() < total ? ROW_H : 0) - listH);
		float s = scroll.get();
		Draw.rect(g, listX, listY, listW, listH, 6f, Colors.fade(0x18000000, a));
		g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + listH));
		int first = Math.max(0, (int) (s / ROW_H));
		for (int i = first; i < hits.size(); i++) {
			float y = listY + i * ROW_H - s;
			if (y > listY + listH) {
				break;
			}
			Modrinth.Hit h = hits.get(i);
			boolean sel = selected != null && selected.id().equals(h.id());
			boolean hot = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H && my >= listY && my < listY + listH;
			if (sel || hot) {
				Draw.rect(g, listX + 2, y + 1, listW - 4, ROW_H - 2, 5f, Colors.fade(sel ? Colors.withAlpha(accent, 0x30) : 0x10FFFFFF, a));
			}
			avatar(g, h, listX + 6, y + 5, 20, a);
			float textW = listW - 36 - 8;
			String badge = badge(h);
			float bw = badge == null ? 0 : JacText.width(badge, Theme.SMALL, JacText.Weight.SEMIBOLD) + 10;
			JacText.draw(g, fit(h.title(), Theme.LABEL, JacText.Weight.SEMIBOLD, textW - bw), listX + 32, y + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			JacText.draw(g, fit("by " + h.author() + "  ·  " + Modrinth.shortCount(h.downloads()) + " downloads", Theme.SMALL, JacText.Weight.REGULAR,
				textW), listX + 32, y + 16, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			if (badge != null) {
				int c = badge.equals("Installed") || badge.equals("Loaded") ? OK : WARN;
				float bx = listX + listW - 8 - bw;
				Draw.rect(g, bx, y + 5, bw, 11, 5.5f, Colors.fade(Colors.withAlpha(c, 0x30), a));
				JacText.draw(g, badge, bx + 5, y + 7, Colors.fade(c, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}
		if (hits.size() < total && !hits.isEmpty()) {
			float y = listY + hits.size() * ROW_H - s;
			JacText.drawCentered(g, loading ? "Loading more..." : "Scroll for more", listX + listW / 2f, y + 10, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.REGULAR);
			if (!loading && y < listY + listH) {
				runSearch(true);
			}
		}
		g.disableScissor();
		if (hits.isEmpty()) {
			String text = loading ? "Searching Modrinth..." : error != null ? error : "Nothing found for this version";
			for (String line : wrap(text, Theme.LABEL, JacText.Weight.REGULAR, listW - 20)) {
				JacText.drawCentered(g, line, listX + listW / 2f, listY + 20, Colors.fade(error != null ? BAD : Theme.TEXT_DIM, a), Theme.LABEL,
					JacText.Weight.REGULAR);
			}
			if (error != null && !loading) {
				Button retry = new Button(listX + listW / 2f - 45, listY + 40, 90, 16, Icons.REFRESH_CW, "Try again", false, () -> runSearch(false));
				buttons.add(retry);
				retry.render(g, mx, my, a);
			}
		}
		if (scroll.max() > 0) {
			float barH = Math.max(16f, listH * listH / (listH + scroll.max()));
			float barY = listY + (listH - barH) * (s / scroll.max());
			Draw.rect(g, listX + listW - 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
	}

	private @Nullable String badge(Modrinth.Hit h) {
		Installed in = installed.get(h.id());
		if (in != null) {
			List<Modrinth.Version> list = versionsOf(h);
			Modrinth.Version b = list == null ? null : best(list);
			return b != null && !b.id().equals(in.versionId()) ? "Update" : "Installed";
		}
		Job job = jobs.get(h.id());
		if (job != null && job.done && !job.failed) {
			return "Installed";
		}
		return modLoaded(h) ? "Loaded" : null;
	}

	/** A rounded square with the project's first letter, coloured from its name (Modrinth's icons are WebP, which the game cannot read). */
	private static void avatar(GuiGraphics g, Modrinth.Hit h, float x, float y, float size, float a) {
		int hash = h.slug().hashCode();
		float hue = (hash & 0xFFFF) / 65535f;
		int color = java.awt.Color.HSBtoRGB(hue, 0.45f, 0.62f) | 0xFF000000;
		Draw.rect(g, x, y, size, size, size * 0.28f, Colors.fade(color, a));
		String letter = h.title().isEmpty() ? "?" : h.title().substring(0, 1).toUpperCase(Locale.ROOT);
		JacText.drawCentered(g, letter, x + size / 2f, y + size / 2f - 4.5f, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
	}

	private void drawDetail(GuiGraphics g, int mx, int my, float a, int accent) {
		Modrinth.Hit h = selected;
		float x = detailX;
		float w = px + pw - PAD - x;
		float top = listY;
		float hgt = listH;
		if (h == null) {
			JacText.drawCentered(g, loading ? "" : "Pick something on the left", x + w / 2f, top + hgt / 2f - 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL,
				JacText.Weight.REGULAR);
			return;
		}
		g.enableScissor((int) x, (int) top, (int) (x + w), (int) (top + hgt));
		float y = top - detailScroll.get();
		avatar(g, h, x, y, 30, a);
		JacText.draw(g, fit(h.title(), Theme.TITLE, JacText.Weight.SEMIBOLD, w - 38), x + 38, y + 3, Colors.fade(Theme.TEXT, a), Theme.TITLE,
			JacText.Weight.SEMIBOLD);
		JacText.draw(g, fit("by " + h.author() + "  ·  " + Modrinth.shortCount(h.downloads()) + " downloads  ·  " + Modrinth.shortCount(h.follows())
			+ " followers", Theme.SMALL, JacText.Weight.REGULAR, w - 38), x + 38, y + 17, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		y += 38;
		for (String line : wrap(h.description(), Theme.LABEL, JacText.Weight.REGULAR, w - 4)) {
			JacText.draw(g, line, x, y, Colors.fade(Theme.TEXT, a * 0.9f), Theme.LABEL, JacText.Weight.REGULAR);
			y += 10;
		}
		y += 4;
		// Warnings that come before installing.
		if (kind == Modrinth.Kind.SHADER && !FabricLoader.getInstance().isModLoaded("iris")) {
			y = warning(g, x, y, w, a, "Shaders need Iris, which is not installed. Install it too, then restart.");
			Job iris = jobs.get("iris");
			if (iris == null) {
				Button b = new Button(x, y, 96, 16, Icons.DOWNLOAD, "Install Iris", false, this::installIris);
				buttons.add(b);
				b.render(g, mx, my, a);
				y += 22;
			} else {
				y = jobLine(g, iris, x, y, w, a);
			}
		}
		if (kind == Modrinth.Kind.DATA_PACK) {
			JacText.draw(g, "Install into which world?", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			y += 11;
			if (worlds.isEmpty()) {
				y = warning(g, x, y, w, a, "No singleplayer worlds yet. Data packs go into a world's folder.");
			} else {
				float cx = x;
				Path here = currentWorld();
				for (Path p : worlds.subList(0, Math.min(8, worlds.size()))) {
					String label = p.getFileName() + (p.equals(here) ? " (open now)" : "");
					float cw = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
					if (cx + cw > x + w) {
						cx = x;
						y += 16;
					}
					chip(g, mx, my, a, cx, y, label, p.equals(world), () -> world = p);
					cx += cw + 4;
				}
				y += 20;
			}
		}
		// Version and install.
		List<Modrinth.Version> list = versionsOf(h);
		String verr = versionErrors.get(kind + ":" + h.id());
		Job job = jobs.get(h.id());
		if (verr != null) {
			y = warning(g, x, y, w, a, verr);
		} else if (list == null) {
			JacText.draw(g, "Finding versions...", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			y += 14;
		} else {
			Modrinth.Version v = best(list);
			if (v == null) {
				y = warning(g, x, y, w, a, list.isEmpty() ? "No version for " + ModrinthFeature.gameVersion() + " yet"
					: "Only beta or alpha versions; allow them in the Modrinth browser's settings");
			} else {
				Modrinth.FileRef f = v.primaryFile();
				JacText.draw(g, fit(v.number() + "  ·  " + v.type() + "  ·  " + v.date() + (f != null ? "  ·  " + size(f.size()) : ""), Theme.SMALL,
					JacText.Weight.REGULAR, w), x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
				y += 12;
				Installed in = installed.get(h.id());
				boolean current = in != null && in.versionId().equals(v.id());
				boolean busy = job != null && !job.done;
				String label = current ? "Installed" : in != null ? "Update" : busy ? "Installing" : "Install";
				// A mod already running from outside the mods folder would load twice after a restart, which Fabric refuses.
				boolean outside = in == null && modLoaded(h);
				if (outside) {
					label = "Already loaded";
				}
				boolean canInstall = !current && !busy && !outside && !(job != null && job.done && !job.failed)
					&& (kind != Modrinth.Kind.DATA_PACK || world != null);
				float bw = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 32;
				Button b = new Button(x, y, bw, 18, current || outside ? Icons.CHECK : Icons.DOWNLOAD, label, !current && !outside, () -> install(h, v))
					.active(canInstall);
				buttons.add(b);
				b.render(g, mx, my, a);
				float siteW = JacText.width("Modrinth page", Theme.LABEL, JacText.Weight.SEMIBOLD) + 32;
				float sx = x + bw + 6;
				if (sx + siteW > x + w) {
					sx = x;
					y += 22;
				}
				Button site = new Button(sx, y, siteW, 18, Icons.EXTERNAL_LINK, "Modrinth page", false, () -> openUri(h.url()));
				buttons.add(site);
				site.render(g, mx, my, a);
				y += 24;
				long needs = v.dependencies().stream().filter(Modrinth.Dependency::required).count();
				if (kind == Modrinth.Kind.MOD && needs > 0) {
					String text = needs == 1 ? "Needs 1 other mod" : "Needs " + needs + " other mods";
					text += feature().dependencies.on() ? ", installed with it if missing" : " (installing them is off)";
					JacText.draw(g, fit(text, Theme.SMALL, JacText.Weight.REGULAR, w), x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
						JacText.Weight.REGULAR);
					y += 12;
				}
				if (outside) {
					for (String line : wrap("Already running in this game, from outside the mods folder (your launcher or another folder)",
						Theme.SMALL, JacText.Weight.REGULAR, w - 4)) {
						JacText.draw(g, line, x, y, Colors.fade(OK, a), Theme.SMALL, JacText.Weight.REGULAR);
						y += 9;
					}
					y += 4;
				}
			}
		}
		if (job != null) {
			y = jobLine(g, job, x, y, w, a);
			if (kind == Modrinth.Kind.RESOURCE_PACK && job.done && !job.failed && job.file != null) {
				Path file = job.file;
				Button b = new Button(x, y, 90, 16, Icons.POWER, "Turn it on", true, () -> turnOnPack(file));
				buttons.add(b);
				b.render(g, mx, my, a);
				y += 22;
			}
		}
		// Categories.
		float cx = x;
		int shown = 0;
		for (String c : h.categories()) {
			if (LOADERS.contains(c) || shown++ >= 6) {
				continue;
			}
			float cw = JacText.width(c, Theme.SMALL, JacText.Weight.REGULAR) + 10;
			if (cx + cw > x + w) {
				cx = x;
				y += 14;
			}
			Draw.rect(g, cx, y, cw, 11, 5.5f, Colors.fade(Theme.CONTROL, a));
			JacText.draw(g, c, cx + 5, y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			cx += cw + 4;
		}
		y += 20;
		// Jobs for other projects (dependencies) on this page.
		for (Map.Entry<String, Job> e : jobs.entrySet()) {
			if (!e.getKey().equals(h.id()) && !e.getKey().equals("iris") && !e.getValue().done) {
				y = jobLine(g, e.getValue(), x, y, w, a);
			}
		}
		g.disableScissor();
		float contentH = y + detailScroll.get() - top;
		detailScroll.setMax(contentH - hgt);
	}

	private static String size(long bytes) {
		return bytes >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0) : Math.max(1, bytes / 1024) + " KB";
	}

	private float warning(GuiGraphics g, float x, float y, float w, float a, String text) {
		JacText.icon(g, Icons.TRIANGLE_ALERT, x + 4, y + 1, 7f, Colors.fade(WARN, a));
		for (String line : wrap(text, Theme.SMALL, JacText.Weight.REGULAR, w - 16)) {
			JacText.draw(g, line, x + 13, y, Colors.fade(WARN, a), Theme.SMALL, JacText.Weight.REGULAR);
			y += 9;
		}
		return y + 6;
	}

	private float jobLine(GuiGraphics g, Job job, float x, float y, float w, float a) {
		if (job.progress >= 0 && !job.done) {
			Draw.rect(g, x, y, w - 4, 4, 2f, Colors.fade(Theme.CONTROL, a));
			Draw.rect(g, x, y, (float) ((w - 4) * job.progress), 4, 2f, Colors.fade(JacConfig.accent(), a));
			y += 8;
			JacText.draw(g, "Downloading " + Math.round(job.progress * 100) + "%", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			return y + 12;
		}
		for (String line : wrap(job.label, Theme.SMALL, JacText.Weight.REGULAR, w - 4)) {
			JacText.draw(g, line, x, y, Colors.fade(job.color, a), Theme.SMALL, JacText.Weight.REGULAR);
			y += 9;
		}
		return y + 6;
	}

	private void drawFooter(GuiGraphics g, int mx, int my, float a) {
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		Button back = new Button(px + PAD, fy, 96, 16, Icons.PACKAGE, "Mods panel", false, () -> open(parent instanceof ModsScreen ? parent
			: new ModsScreen(parent)));
		buttons.add(back);
		back.render(g, mx, my, a);
		Path dir = folder(kind);
		if (dir != null) {
			Button f = new Button(px + PAD + 102, fy, 104, 16, Icons.FOLDER_OPEN, "Open folder", false, () -> {
				try {
					Files.createDirectories(dir);
				} catch (IOException e) {
					// Opening shows the error well enough.
				}
				openPath(dir);
			});
			buttons.add(f);
			f.render(g, mx, my, a);
		}
		if (!INSTALLED_THIS_SESSION.isEmpty()) {
			String label = ModsScreen.restartLabel();
			float bw = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
			Button restart = new Button(px + pw - PAD - bw, fy, bw, 16, Icons.REFRESH_CW, label, true, ModsScreen::restartGame);
			buttons.add(restart);
			restart.render(g, mx, my, a);
			int n = INSTALLED_THIS_SESSION.size();
			String note = n == 1 ? "1 new mod loads after a restart" : n + " new mods load after a restart";
			JacText.draw(g, note, px + pw - PAD - bw - 8 - JacText.width(note, Theme.SMALL, JacText.Weight.REGULAR), fy + 4.5f, Colors.fade(WARN, a),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	// Input

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26) {
			onClose();
			return true;
		}
		if (feature().isEnabled() && search.click(mx, my)) {
			return true;
		}
		for (Button b : List.copyOf(buttons)) {
			if (b.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		for (int i = 0; i < chipBoxes.size(); i++) {
			float[] r = chipBoxes.get(i);
			if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
				chipActions.get(i).run();
				return true;
			}
		}
		if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			int i = (int) ((my - listY + scroll.get()) / ROW_H);
			if (i >= 0 && i < hits.size()) {
				select(hits.get(i));
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx >= detailX) {
			detailScroll.scroll(amount, 20f);
		} else {
			scroll.scroll(amount, ROW_H * 1.5f);
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (!feature().isEnabled()) {
			return false;
		}
		if (search.focused) {
			String before = search.get();
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				searchAt = 0;
				runSearch(false);
				return true;
			}
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				searchAt = System.currentTimeMillis();
				if (search.get().isEmpty()) {
					runSearch(false);
				}
			}
			if (used) {
				return true;
			}
		}
		if (ctrl && key == InputConstants.KEY_F) {
			search.focused = true;
			return true;
		}
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
			int i = selected == null ? -1 : hits.indexOf(selected);
			int next = Math.max(0, Math.min(hits.size() - 1, i + (key == InputConstants.KEY_DOWN ? 1 : -1)));
			if (next >= 0 && next < hits.size()) {
				select(hits.get(next));
				scroll.reveal(next * ROW_H, (next + 1) * ROW_H, listH);
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (!feature().isEnabled()) {
			return false;
		}
		if (!search.focused) {
			if (text.isBlank()) {
				return false;
			}
			search.focused = true;
		}
		search.typed(text);
		searchAt = System.currentTimeMillis();
		return true;
	}
}
