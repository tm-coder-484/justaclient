package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.ScreenshotIndex;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.ScreenshotsFeature;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.SmoothTexture;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import dev.justaclient.waypoint.Waypoints;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The screenshot manager: a grid of your screenshots with search, favourites and sizes; a viewer with where and when each
 * was taken and its tags; and a compare slider for two shots.
 */
public final class ScreenshotsScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final int THUMB_W = 256;
	private static final int FULL_W = 1920;
	private static final int MAX_THUMBS = 400;
	private static final ExecutorService DECODER = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Justaclient screenshot loader");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private static int nextId;

	private enum Mode {
		GRID, VIEW, COMPARE
	}

	private enum Filter {
		ALL("All"), FAVOURITES("Favourites"), WORLD("This world");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	/** A decoded picture waiting for the render thread to upload it. */
	private record Decoded(Pic pic, boolean full, int w, int h, int[] argb) {
	}

	private static final class Pic {
		final Path path;
		final String name;
		final long modified;
		@Nullable Identifier thumb;
		int thumbW;
		int thumbH;
		boolean thumbLoading;
		boolean failed;
		@Nullable Identifier full;
		int fullW;
		int fullH;
		boolean fullLoading;

		Pic(Path path, long modified) {
			this.path = path;
			this.name = path.getFileName().toString();
			this.modified = modified;
		}
	}

	private final List<Pic> all = new ArrayList<>();
	private List<Pic> shown = List.of();
	private final ConcurrentLinkedQueue<Decoded> ready = new ConcurrentLinkedQueue<>();
	private final List<Identifier> textures = new ArrayList<>();
	private int thumbs;
	private Mode mode = Mode.GRID;
	private Filter filter = Filter.ALL;
	private boolean newestFirst = true;
	private static int size = 1;
	private final InputBox search = new InputBox("", 64, "Search tags, worlds, biomes", Icons.SEARCH);
	private final InputBox tagBox = new InputBox("", 24, "Add a tag", Icons.PLUS);
	private final Scroller scroll = new Scroller();
	private @Nullable Pic current;
	private @Nullable Pic compareA;
	private @Nullable Pic compareB;
	private float slider = 0.5f;
	private boolean draggingSlider;
	private final List<Button> viewButtons = new ArrayList<>();
	private final @Nullable String worldKey;
	private String status = "";

	private float px;
	private float py;
	private float pw;
	private float ph;
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private float tileW;
	private float tileH;
	private int columns = 1;

	public ScreenshotsScreen(@Nullable Screen parent) {
		this(parent, Waypoints.worldKey());
	}

	private ScreenshotsScreen(@Nullable Screen parent, @Nullable String worldKey) {
		super("Screenshots", parent);
		this.worldKey = worldKey;
	}

	/** Opens straight into one world's screenshots, for the World Archive. */
	public static ScreenshotsScreen forWorld(@Nullable Screen parent, String worldKey) {
		ScreenshotsScreen s = new ScreenshotsScreen(parent, worldKey);
		s.filter = Filter.WORLD;
		return s;
	}

	private ScreenshotIndex index() {
		return ScreenshotsFeature.get().index();
	}

	@Override
	protected void setup() {
		if (all.isEmpty()) {
			scan();
		}
	}

	private void scan() {
		all.clear();
		Path dir = ScreenshotsFeature.folder();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.filter(Files::isRegularFile).toList()) {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
						try {
							all.add(new Pic(p, Files.getLastModifiedTime(p).toMillis()));
						} catch (IOException e) {
							// Gone in between.
						}
					}
				}
			} catch (IOException e) {
				status = "Could not read the screenshots folder";
			}
		}
		Set<String> names = new HashSet<>();
		for (Pic p : all) {
			names.add(p.name);
		}
		int before = index().shots.size();
		index().prune(names);
		if (index().shots.size() != before) {
			ScreenshotsFeature.get().save();
		}
		applyFilter();
	}

	private void applyFilter() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		List<Pic> list = new ArrayList<>();
		for (Pic p : all) {
			ScreenshotIndex.Shot s = index().peek(p.name);
			boolean pass = switch (filter) {
				case ALL -> true;
				case FAVOURITES -> s != null && s.favourite;
				case WORLD -> s != null && worldKey != null && worldKey.equals(s.worldKey);
			};
			if (pass && (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q) || (s != null && s.searchText().contains(q)))) {
				list.add(p);
			}
		}
		list.sort(newestFirst ? Comparator.comparingLong((Pic p) -> p.modified).reversed() : Comparator.comparingLong(p -> p.modified));
		shown = list;
	}

	// Loading

	private void requestThumb(Pic p) {
		if (p.thumb != null || p.thumbLoading || p.failed || thumbs >= MAX_THUMBS) {
			return;
		}
		p.thumbLoading = true;
		DECODER.submit(() -> decode(p, false));
	}

	private void requestFull(Pic p) {
		if (p.full != null || p.fullLoading || p.failed) {
			return;
		}
		p.fullLoading = true;
		DECODER.submit(() -> decode(p, true));
	}

	private void decode(Pic p, boolean full) {
		try {
			BufferedImage img = ImageIO.read(p.path.toFile());
			if (img == null) {
				throw new IOException("not an image");
			}
			int maxW = full ? FULL_W : THUMB_W;
			int w = Math.min(maxW, img.getWidth());
			int h = Math.max(1, Math.round(img.getHeight() * (w / (float) img.getWidth())));
			BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(img, 0, 0, w, h, null);
			g.dispose();
			ready.add(new Decoded(p, full, w, h, scaled.getRGB(0, 0, w, h, null, 0, w)));
		} catch (IOException | RuntimeException | OutOfMemoryError e) {
			p.failed = true;
			p.thumbLoading = false;
			p.fullLoading = false;
		}
	}

	/** Uploads a few decoded pictures per frame so opening the gallery never stalls. */
	private void upload() {
		for (int i = 0; i < 4; i++) {
			Decoded d = ready.poll();
			if (d == null) {
				return;
			}
			SmoothTexture tex = new SmoothTexture(() -> "Screenshot " + d.pic.name, d.w, d.h);
			NativeImage pixels = tex.getPixels();
			if (pixels == null) {
				continue;
			}
			for (int y = 0; y < d.h; y++) {
				for (int x = 0; x < d.w; x++) {
					pixels.setPixel(x, y, d.argb[y * d.w + x]);
				}
			}
			tex.upload();
			Identifier id = Justaclient.id("screenshot/" + (nextId++));
			minecraft.getTextureManager().register(id, tex);
			textures.add(id);
			if (d.full) {
				d.pic.full = id;
				d.pic.fullW = d.w;
				d.pic.fullH = d.h;
				d.pic.fullLoading = false;
				releaseOtherFulls();
			} else {
				d.pic.thumb = id;
				d.pic.thumbW = d.w;
				d.pic.thumbH = d.h;
				d.pic.thumbLoading = false;
				thumbs++;
			}
		}
	}

	/**
	 * Frees the full-size pictures that are not on screen. Each is up to 1920 px wide, so stepping through a few hundred
	 * screenshots would otherwise keep gigabytes of them until the gallery closes.
	 */
	private void releaseOtherFulls() {
		for (Pic p : all) {
			if (p.full != null && p != current && p != compareA && p != compareB) {
				minecraft.getTextureManager().release(p.full);
				textures.remove(p.full);
				p.full = null;
			}
		}
	}

	private void release() {
		for (Identifier id : textures) {
			minecraft.getTextureManager().release(id);
		}
		textures.clear();
		for (Pic p : all) {
			p.thumb = null;
			p.full = null;
			p.thumbLoading = false;
			p.fullLoading = false;
		}
		ready.clear();
		thumbs = 0;
	}

	@Override
	public void onClose() {
		release();
		super.onClose();
	}

	/** Also frees everything when another screen replaces this one (death, disconnect), not only when it is closed. */
	@Override
	public void removed() {
		release();
		super.removed();
	}

	// Drawing helpers

	/** Draws {@code id} ({@code tw} by {@code th}) to fill the box, cropping the overflow, or to fit inside it. */
	private static void image(GuiGraphicsExtractor g, Identifier id, int tw, int th, float x, float y, float w, float h, boolean cover, float a) {
		int color = ((Math.round(a * 255) & 0xFF) << 24) | 0xFFFFFF;
		float scale = cover ? Math.max(w / tw, h / th) : Math.min(w / tw, h / th);
		if (cover) {
			float srcW = w / scale;
			float srcH = h / scale;
			g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(x), Math.round(y), (tw - srcW) / 2f, (th - srcH) / 2f, Math.round(w), Math.round(h),
				Math.round(srcW), Math.round(srcH), tw, th, color);
		} else {
			float dw = tw * scale;
			float dh = th * scale;
			g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(x + (w - dw) / 2f), Math.round(y + (h - dh) / 2f), 0f, 0f, Math.round(dw), Math.round(dh),
				tw, th, tw, th, color);
		}
	}

	private void layout() {
		pw = Math.min(680f, width - 24f);
		ph = Math.min(400f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		gridX = px + PAD;
		gridY = py + 58;
		gridW = pw - PAD * 2;
		gridH = py + ph - 34 - gridY;
		float[] sizes = {96f, 128f, 176f};
		float target = sizes[size];
		columns = Math.max(1, (int) ((gridW + 6) / (target + 6)));
		tileW = (gridW - 6 * (columns - 1)) / columns;
		tileH = tileW * 9f / 16f;
		search.place(px + pw - PAD - 170 - 20, py + 10, 170);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float t) {
		upload();
		layout();
		float a = t;
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		switch (mode) {
			case GRID -> drawGrid(g, mx, my, a);
			case VIEW -> drawView(g, mx, my, a);
			case COMPARE -> drawCompare(g, mx, my, a);
		}
	}

	private void header(GuiGraphicsExtractor g, int mx, int my, float a, String title, String sub) {
		int accent = JacConfig.accent();
		JacText.icon(g, Icons.CAMERA, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, title, px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, sub, px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		boolean closeHot = mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
		JacText.icon(g, mode == Mode.GRID ? Icons.X : Icons.ARROW_LEFT, px + pw - PAD - 11, py + 14, 8f,
			Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
	}

	private void drawGrid(GuiGraphicsExtractor g, int mx, int my, float a) {
		long favs = index().shots.values().stream().filter(s -> s.favourite).count();
		header(g, mx, my, a, "Screenshots", all.size() + (all.size() == 1 ? " screenshot" : " screenshots") + (favs > 0 ? " · " + favs + (favs == 1 ? " favourite" : " favourites") : "")
			+ (compareA != null ? " · pick a second one to compare" : ""));
		search.draw(g, mx, my, a);
		int accent = JacConfig.accent();
		float cy = py + 38;
		float cx = gridX;
		for (Filter f : Filter.values()) {
			if (f == Filter.WORLD && worldKey == null) {
				continue;
			}
			float w = JacText.width(f.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			boolean on = f == filter;
			boolean hot = mx >= cx && mx < cx + w && my >= cy && my < cy + 13;
			Draw.rect(g, cx, cy, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, f.label, cx + 6, cy + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}
		// Sort and size, on the right.
		String sort = newestFirst ? "Newest first" : "Oldest first";
		float sw = JacText.width(sort, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
		float rx = gridX + gridW - 3 * 16 - 4;
		for (int i = 0; i < 3; i++) {
			float bx = rx + i * 16;
			boolean on = i == size;
			Draw.rect(g, bx, cy, 14, 13, 4f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : Theme.CONTROL, a));
			float s = 3f + i * 1.5f;
			Draw.rect(g, bx + 7 - s / 2f, cy + 6.5f - s / 2f, s, s, 1f, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a));
		}
		Draw.rect(g, rx - 6 - sw, cy, sw, 13, 6.5f, Colors.fade(Theme.CONTROL, a));
		JacText.draw(g, sort, rx - 6 - sw + 6, cy + 3, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);

		int rows = (shown.size() + columns - 1) / columns;
		scroll.setMax(rows * (tileH + 6) - 6 - gridH);
		float s = scroll.get();
		g.enableScissor((int) gridX, (int) gridY, (int) (gridX + gridW), (int) (gridY + gridH));
		for (int i = 0; i < shown.size(); i++) {
			float tx = gridX + (i % columns) * (tileW + 6);
			float ty = gridY + (i / columns) * (tileH + 6) - s;
			if (ty + tileH < gridY) {
				continue;
			}
			if (ty > gridY + gridH) {
				break;
			}
			Pic p = shown.get(i);
			requestThumb(p);
			boolean hot = my >= gridY && my < gridY + gridH && mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH;
			Draw.rect(g, tx, ty, tileW, tileH, 4f, Colors.fade(0x30000000, a));
			if (p.thumb != null) {
				image(g, p.thumb, p.thumbW, p.thumbH, tx, ty, tileW, tileH, true, a);
			} else {
				JacText.icon(g, p.failed ? Icons.TRIANGLE_ALERT : Icons.IMAGE, tx + tileW / 2f - 5, ty + tileH / 2f - 5, 10f,
					Colors.fade(Theme.TEXT_DIM, a * 0.6f));
			}
			ScreenshotIndex.Shot shot = index().peek(p.name);
			boolean fav = shot != null && shot.favourite;
			if (hot || fav) {
				boolean starHot = hot && mx >= tx + tileW - 16 && my < ty + 16;
				Draw.rect(g, tx + tileW - 15, ty + 3, 12, 12, 6f, Colors.fade(0x80000000, a));
				JacText.icon(g, Icons.STAR, tx + tileW - 12.5f, ty + 5.5f, 7f, Colors.fade(fav ? 0xFFFBBF24 : starHot ? Theme.TEXT : Theme.TEXT_DIM, a));
			}
			if (hot) {
				Draw.verticalGradient(g, tx, ty + tileH - 18, tileW, 18, 0, 0x00000000, Colors.fade(0xB0000000, a));
				String label = shot != null && shot.world != null ? shot.world + (shot.biome != null ? " · " + shot.biome : "") : date(p.modified);
				JacText.draw(g, fit(label, Theme.SMALL, JacText.Weight.SEMIBOLD, tileW - 8), tx + 4, ty + tileH - 10, Colors.fade(0xFFFFFFFF, a),
					Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
			if (p == compareA) {
				Draw.outline(g, tx, ty, tileW, tileH, 4f, 1.5f, Colors.fade(accent, a));
			} else if (hot) {
				Draw.outline(g, tx, ty, tileW, tileH, 4f, 0.75f, Colors.fade(0x60FFFFFF, a));
			}
		}
		g.disableScissor();
		if (shown.isEmpty()) {
			String msg = all.isEmpty() ? "No screenshots yet. Press " + minecraft.options.keyScreenshot.getTranslatedKeyMessage().getString()
				+ " in game to take one." : "Nothing matches";
			JacText.drawCentered(g, msg, gridX + gridW / 2f, gridY + 30, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		if (scroll.max() > 0) {
			float barH = Math.max(16f, gridH * gridH / (gridH + scroll.max()));
			float barY = gridY + (gridH - barH) * (s / scroll.max());
			Draw.rect(g, gridX + gridW + 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		boolean folderHot = mx >= px + PAD && mx < px + PAD + 96 && my >= fy && my < fy + 16;
		Draw.rect(g, px + PAD, fy, 96, 16, 5f, Colors.fade(folderHot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
		JacText.icon(g, Icons.FOLDER_OPEN, px + PAD + 8, fy + 4.5f, 7f, Colors.fade(Theme.TEXT, a));
		JacText.draw(g, "Open folder", px + PAD + 20, fy + 4.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		String hint = status.isEmpty() ? "Click to view · Shift-click two to compare · Star to favourite" : status;
		JacText.draw(g, fit(hint, Theme.SMALL, JacText.Weight.REGULAR, pw - PAD * 2 - 110), px + PAD + 106, fy + 5, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);
	}

	private static String date(long millis) {
		return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(new Date(millis));
	}

	private float viewW() {
		return pw - PAD * 3 - 150;
	}

	private void drawView(GuiGraphicsExtractor g, int mx, int my, float a) {
		Pic p = current;
		if (p == null) {
			mode = Mode.GRID;
			return;
		}
		int index = shown.indexOf(p);
		header(g, mx, my, a, fit(p.name, Theme.TITLE, JacText.Weight.SEMIBOLD, pw - 80), (index + 1) + " of " + shown.size() + " · " + date(p.modified));
		requestFull(p);
		float vx = px + PAD;
		float vy = py + 38;
		float vw = viewW();
		float vh = py + ph - PAD - vy;
		Draw.rect(g, vx, vy, vw, vh, 6f, Colors.fade(0x40000000, a));
		Identifier img = p.full != null ? p.full : p.thumb;
		if (img != null) {
			image(g, img, p.full != null ? p.fullW : p.thumbW, p.full != null ? p.fullH : p.thumbH, vx, vy, vw, vh, false, a);
		}
		// Arrows.
		for (int side = 0; side < 2; side++) {
			float ax = side == 0 ? vx + 4 : vx + vw - 22;
			boolean can = side == 0 ? index > 0 : index < shown.size() - 1;
			boolean hot = can && mx >= ax && mx < ax + 18 && my >= vy + vh / 2f - 12 && my < vy + vh / 2f + 12;
			if (can) {
				Draw.rect(g, ax, vy + vh / 2f - 12, 18, 24, 6f, Colors.fade(hot ? 0xA0000000 : 0x60000000, a));
				JacText.icon(g, side == 0 ? Icons.CHEVRON_LEFT : Icons.CHEVRON_RIGHT, ax + 5, vy + vh / 2f - 4, 8f, Colors.fade(Theme.TEXT, a));
			}
		}
		// Side panel.
		float sx = vx + vw + PAD;
		float sy = vy;
		float sw = 150;
		ScreenshotIndex.Shot shot = index().peek(p.name);
		boolean hideCoords = StreamModeFeature.hidesCoordinates();
		// Facts: world and biome get a full row, the short ones share rows two to a line.
		List<String[]> wide = new ArrayList<>();
		List<String[]> pairs = new ArrayList<>();
		if (shot != null && shot.world != null) {
			wide.add(new String[] {"World", shot.world});
		}
		if (shot != null && shot.biome != null) {
			wide.add(new String[] {"Biome", shot.biome});
		}
		if (shot != null && shot.dimension != null) {
			pairs.add(new String[] {"Dimension", shot.dimension});
		}
		if (shot != null && shot.weather != null) {
			pairs.add(new String[] {"Weather", shot.weather + (shot.time != null ? " · " + shot.time : "")});
		}
		if (shot != null && shot.x != null && !hideCoords) {
			pairs.add(new String[] {"Position", shot.x + ", " + shot.y + ", " + shot.z});
		}
		if (p.full != null) {
			pairs.add(new String[] {"Size", p.fullW == FULL_W ? "large" : p.fullW + " x " + p.fullH});
		}
		if (shot == null || shot.world == null) {
			for (String line : wrap("Taken before auto-tagging, or outside a world.", Theme.SMALL, JacText.Weight.REGULAR, sw)) {
				JacText.draw(g, line, sx, sy, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
				sy += 9;
			}
			sy += 6;
		}
		for (String[] f : wide) {
			fact(g, f, sx, sy, sw, a);
			sy += 20;
		}
		float half = (sw - 6) / 2f;
		for (int i = 0; i < pairs.size(); i++) {
			fact(g, pairs.get(i), sx + (i % 2) * (half + 6), sy, half, a);
			if (i % 2 == 1 || i == pairs.size() - 1) {
				sy += 20;
			}
		}
		sy += 2;
		JacText.draw(g, "Tags", sx, sy, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		sy += 10;
		float cx = sx;
		if (shot != null) {
			for (String tag : shot.tags) {
				float w = JacText.width(tag, Theme.SMALL, JacText.Weight.REGULAR) + 18;
				if (cx + w > sx + sw) {
					cx = sx;
					sy += 15;
				}
				boolean hot = mx >= cx && mx < cx + w && my >= sy && my < sy + 12;
				Draw.rect(g, cx, sy, w, 12, 6f, Colors.fade(hot ? Colors.withAlpha(0xFFF87171, 0x40) : Theme.CONTROL, a));
				JacText.draw(g, tag, cx + 5, sy + 2.5f, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
				JacText.icon(g, Icons.X, cx + w - 10, sy + 3.5f, 5f, Colors.fade(hot ? Theme.TEXT : Theme.TEXT_DIM, a));
				cx += w + 4;
			}
		}
		sy += shot != null && !shot.tags.isEmpty() ? 16 : 0;
		tagBox.place(sx, sy, sw);
		tagBox.draw(g, mx, my, a);
		sy += 24;
		viewButtons.clear();
		boolean fav = shot != null && shot.favourite;
		float bw = (sw - 4) / 2f;
		viewButtons.add(new Button(sx, sy, bw, 16, Icons.STAR, "Favourite", fav, () -> toggleFavourite(p)));
		viewButtons.add(new Button(sx + bw + 4, sy, bw, 16, Icons.LAYERS, "Compare", false, () -> {
			compareA = p;
			mode = Mode.GRID;
			status = "Pick a second screenshot to compare";
		}));
		viewButtons.add(new Button(sx, sy + 20, bw, 16, Icons.EXTERNAL_LINK, "Open", false, () -> openPath(p.path)));
		viewButtons.add(new Button(sx + bw + 4, sy + 20, bw, 16, Icons.TRASH, "Delete", false, () -> delete(p)));
		for (Button b : viewButtons) {
			b.render(g, mx, my, a);
		}
	}

	private static void fact(GuiGraphicsExtractor g, String[] f, float x, float y, float w, float a) {
		JacText.draw(g, f[0], x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.draw(g, fit(f[1], Theme.LABEL, JacText.Weight.SEMIBOLD, w), x, y + 8, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
	}

	private void drawCompare(GuiGraphicsExtractor g, int mx, int my, float a) {
		Pic left = compareA;
		Pic right = compareB;
		if (left == null || right == null) {
			mode = Mode.GRID;
			return;
		}
		header(g, mx, my, a, "Compare", "Drag to slide between them · Esc to go back");
		requestFull(left);
		requestFull(right);
		float vx = px + PAD;
		float vy = py + 38;
		float vw = pw - PAD * 2;
		float vh = py + ph - PAD - vy;
		Draw.rect(g, vx, vy, vw, vh, 6f, Colors.fade(0x40000000, a));
		// Both pictures share the frame of the first, so the slider lines them up.
		int tw = left.full != null ? left.fullW : left.thumbW;
		int th = left.full != null ? left.fullH : left.thumbH;
		if (tw == 0) {
			return;
		}
		float scale = Math.min(vw / tw, vh / th);
		float dw = tw * scale;
		float dh = th * scale;
		float dx = vx + (vw - dw) / 2f;
		float dy = vy + (vh - dh) / 2f;
		Identifier li = left.full != null ? left.full : left.thumb;
		Identifier ri = right.full != null ? right.full : right.thumb;
		float cut = dx + dw * slider;
		if (li != null) {
			g.enableScissor((int) dx, (int) dy, (int) Math.ceil(cut), (int) (dy + dh));
			image(g, li, tw, th, dx, dy, dw, dh, true, a);
			g.disableScissor();
		}
		if (ri != null) {
			g.enableScissor((int) cut, (int) dy, (int) (dx + dw), (int) (dy + dh));
			image(g, ri, right.full != null ? right.fullW : right.thumbW, right.full != null ? right.fullH : right.thumbH, dx, dy, dw, dh, true, a);
			g.disableScissor();
		}
		int accent = JacConfig.accent();
		Draw.rect(g, cut - 0.75f, dy, 1.5f, dh, 0, Colors.fade(0xFFFFFFFF, a));
		Draw.circle(g, cut, dy + dh / 2f, 7f, Colors.fade(accent, a));
		JacText.icon(g, Icons.ARROW_LEFT_RIGHT, cut - 3.5f, dy + dh / 2f - 3.5f, 7f, 0xFFFFFFFF);
		tag(g, left.name, dx + 4, dy + 4, a);
		float rw = JacText.width(right.name, Theme.SMALL, JacText.Weight.SEMIBOLD) + 10;
		tag(g, right.name, dx + dw - rw - 4, dy + 4, a);
	}

	private static void tag(GuiGraphicsExtractor g, String text, float x, float y, float a) {
		float w = JacText.width(text, Theme.SMALL, JacText.Weight.SEMIBOLD) + 10;
		Draw.rect(g, x, y, w, 12, 6f, Colors.fade(0xA0000000, a));
		JacText.draw(g, text, x + 5, y + 2.5f, Colors.fade(0xFFFFFFFF, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
	}

	// Actions

	private void toggleFavourite(Pic p) {
		ScreenshotIndex.Shot s = index().get(p.name);
		s.favourite = !s.favourite;
		ScreenshotsFeature.get().save();
		if (filter == Filter.FAVOURITES) {
			applyFilter();
		}
	}

	private void delete(Pic p) {
		open(new JacConfirmScreen(this, Icons.TRASH, "Delete screenshot", "Delete " + p.name + " from your screenshots folder? This cannot be undone.",
			"Delete", () -> {
				try {
					Files.deleteIfExists(p.path);
					all.remove(p);
					index().shots.remove(p.name);
					ScreenshotsFeature.get().save();
					int i = shown.indexOf(p);
					applyFilter();
					current = shown.isEmpty() ? null : shown.get(Math.max(0, Math.min(shown.size() - 1, i)));
					mode = current == null ? Mode.GRID : Mode.VIEW;
				} catch (IOException e) {
					status = "Could not delete " + p.name;
				}
				open(this);
			}));
	}

	private void step(int by) {
		if (current == null) {
			return;
		}
		int i = shown.indexOf(current) + by;
		if (i >= 0 && i < shown.size()) {
			current = shown.get(i);
			tagBox.set("");
		}
	}

	private void addTag() {
		if (current == null) {
			return;
		}
		if (ScreenshotIndex.addTag(index().get(current.name), tagBox.get())) {
			ScreenshotsFeature.get().save();
		}
		tagBox.set("");
	}

	// Input

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	private void back() {
		if (mode == Mode.COMPARE) {
			mode = current != null ? Mode.VIEW : Mode.GRID;
		} else if (mode == Mode.VIEW) {
			mode = Mode.GRID;
			tagBox.focused = false;
		} else {
			onClose();
		}
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			if (button == 1 && mode != Mode.GRID) {
				back();
				return true;
			}
			return false;
		}
		if (closeHit(mx, my)) {
			back();
			return true;
		}
		return switch (mode) {
			case GRID -> clickGrid(mx, my);
			case VIEW -> clickView(mx, my);
			case COMPARE -> {
				draggingSlider = true;
				dragSlider(mx);
				yield true;
			}
		};
	}

	private boolean clickGrid(double mx, double my) {
		if (search.click(mx, my)) {
			return true;
		}
		float cy = py + 38;
		float cx = gridX;
		for (Filter f : Filter.values()) {
			if (f == Filter.WORLD && worldKey == null) {
				continue;
			}
			float w = JacText.width(f.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			if (mx >= cx && mx < cx + w && my >= cy && my < cy + 13) {
				filter = f;
				applyFilter();
				scroll.scrollTo(0);
				return true;
			}
			cx += w + 4;
		}
		float rx = gridX + gridW - 3 * 16 - 4;
		if (my >= cy && my < cy + 13) {
			for (int i = 0; i < 3; i++) {
				if (mx >= rx + i * 16 && mx < rx + i * 16 + 14) {
					size = i;
					return true;
				}
			}
			String sort = newestFirst ? "Newest first" : "Oldest first";
			float sw = JacText.width(sort, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			if (mx >= rx - 6 - sw && mx < rx - 6) {
				newestFirst = !newestFirst;
				applyFilter();
				return true;
			}
		}
		float fy = py + ph - 26;
		if (mx >= px + PAD && mx < px + PAD + 96 && my >= fy && my < fy + 16) {
			try {
				Files.createDirectories(ScreenshotsFeature.folder());
			} catch (IOException ignored) {
				// The opener says so.
			}
			openPath(ScreenshotsFeature.folder());
			return true;
		}
		if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridY + gridH) {
			return false;
		}
		float s = scroll.get();
		int col = (int) ((mx - gridX) / (tileW + 6));
		int row = (int) ((my - gridY + s) / (tileH + 6));
		int i = row * columns + col;
		if (col >= columns || i < 0 || i >= shown.size()) {
			return true;
		}
		Pic p = shown.get(i);
		float tx = gridX + col * (tileW + 6);
		float ty = gridY + row * (tileH + 6) - s;
		if (mx >= tx + tileW - 16 && my < ty + 16) {
			toggleFavourite(p);
			return true;
		}
		boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
			|| InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		if (shift || compareA != null) {
			if (compareA == null) {
				compareA = p;
				status = "Now pick a second screenshot";
			} else if (compareA != p) {
				compareB = p;
				slider = 0.5f;
				mode = Mode.COMPARE;
				status = "";
			} else {
				compareA = null;
				status = "";
			}
			return true;
		}
		current = p;
		tagBox.set("");
		mode = Mode.VIEW;
		return true;
	}

	private boolean clickView(double mx, double my) {
		if (tagBox.click(mx, my)) {
			return true;
		}
		for (Button b : List.copyOf(viewButtons)) {
			if (b.mouseClicked(mx, my, 0)) {
				return true;
			}
		}
		float vx = px + PAD;
		float vy = py + 38;
		float vw = viewW();
		float vh = py + ph - PAD - vy;
		if (my >= vy + vh / 2f - 12 && my < vy + vh / 2f + 12) {
			if (mx >= vx + 4 && mx < vx + 22) {
				step(-1);
				return true;
			}
			if (mx >= vx + vw - 22 && mx < vx + vw - 4) {
				step(1);
				return true;
			}
		}
		// Tag chips: a click removes the tag.
		ScreenshotIndex.Shot shot = current == null ? null : index().peek(current.name);
		if (shot != null && !shot.tags.isEmpty()) {
			float sx = vx + vw + PAD;
			float chipsTop = tagBox.y - 16;
			float cx = sx;
			float cy = chipsTop;
			// Recompute the rows from the top line of chips.
			int rowsUsed = 0;
			float probe = sx;
			for (String tag : shot.tags) {
				float w = JacText.width(tag, Theme.SMALL, JacText.Weight.REGULAR) + 18;
				if (probe + w > sx + 150) {
					probe = sx;
					rowsUsed++;
				}
				probe += w + 4;
			}
			cy = chipsTop - rowsUsed * 15;
			for (String tag : List.copyOf(shot.tags)) {
				float w = JacText.width(tag, Theme.SMALL, JacText.Weight.REGULAR) + 18;
				if (cx + w > sx + 150) {
					cx = sx;
					cy += 15;
				}
				if (mx >= cx && mx < cx + w && my >= cy && my < cy + 12) {
					shot.tags.remove(tag);
					ScreenshotsFeature.get().save();
					return true;
				}
				cx += w + 4;
			}
		}
		return true;
	}

	private void dragSlider(double mx) {
		float vx = px + PAD;
		float vw = pw - PAD * 2;
		Pic left = compareA;
		if (left == null) {
			return;
		}
		int tw = left.full != null ? left.fullW : left.thumbW;
		int th = left.full != null ? left.fullH : left.thumbH;
		if (tw == 0) {
			return;
		}
		float vh = py + ph - PAD - (py + 38);
		float dw = tw * Math.min(vw / tw, vh / th);
		float dx = vx + (vw - dw) / 2f;
		slider = (float) Math.max(0, Math.min(1, (mx - dx) / dw));
	}

	@Override
	protected void drag(double mx, double my, int button) {
		if (draggingSlider) {
			dragSlider(mx);
		}
	}

	@Override
	protected void release(double mx, double my, int button) {
		draggingSlider = false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mode == Mode.GRID) {
			scroll.scroll(amount, tileH + 6);
		} else if (mode == Mode.VIEW) {
			step(amount > 0 ? -1 : 1);
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (tagBox.focused) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				addTag();
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				tagBox.focused = false;
				return true;
			}
			if (tagBox.key(key, ctrl)) {
				return true;
			}
		}
		if (search.focused && mode == Mode.GRID) {
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			String before = search.get();
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				applyFilter();
				scroll.scrollTo(0);
			}
			if (used) {
				return true;
			}
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (compareA != null && mode == Mode.GRID) {
				compareA = null;
				status = "";
				return true;
			}
			if (mode != Mode.GRID) {
				back();
				return true;
			}
			return false;
		}
		if (mode == Mode.VIEW) {
			if (key == InputConstants.KEY_LEFT) {
				step(-1);
				return true;
			}
			if (key == InputConstants.KEY_RIGHT) {
				step(1);
				return true;
			}
			if (key == InputConstants.KEY_F && current != null && !ctrl) {
				toggleFavourite(current);
				return true;
			}
		}
		if (ctrl && key == InputConstants.KEY_F && mode == Mode.GRID) {
			search.focused = true;
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (tagBox.focused) {
			tagBox.typed(text);
			return true;
		}
		if (mode != Mode.GRID) {
			return false;
		}
		if (!search.focused) {
			if (text.isBlank()) {
				return false;
			}
			search.focused = true;
		}
		search.typed(text);
		applyFilter();
		scroll.scrollTo(0);
		return true;
	}
}
