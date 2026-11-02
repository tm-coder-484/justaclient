package dev.justaclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.TerrainMap;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.Swatches;
import dev.justaclient.ui.widget.TextField;
import dev.justaclient.ui.widget.Widget;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The M key map: loaded terrain around you on the left, this world's waypoints on the right. Drag to pan, scroll to
 * zoom, right-click the map to drop a waypoint, and pick one to rename, move, recolour, track, hide or delete it.
 */
public final class WorldMapScreen extends Screen {
	private static final float W = 560f;
	private static final float H = 330f;
	private static final float PAD = 10f;
	private static final float MAP_X = PAD;
	private static final float MAP_Y = PAD;
	private static final float MAP_W = 360f;
	private static final float MAP_H = H - PAD * 2;
	private static final float SIDE_X = MAP_X + MAP_W + 12f;
	private static final float SIDE_W = W - SIDE_X - PAD - 2f;
	private static final float LIST_TOP = 56f;
	private static final float ROW_H = 18f;
	private static final float EDIT_TOP = 182f;

	/** Texture cells per side. Covers the view at every zoom with room to pan before a rebuild. */
	private static final int SIZE = 384;
	private static final Identifier TEXTURE = Justaclient.id("world_map");
	/** GUI units per block. */
	private static final float[] ZOOMS = {0.25f, 0.5f, 1f, 2f, 4f};
	private static @Nullable DynamicTexture texture;
	private static int lastZoom = 2;

	private final Animation open = new Animation(Easing.CUBIC_OUT, 240, 0f);
	private final Animation locateAnim = new Animation(Easing.CUBIC_OUT, 160, 0f);
	private boolean closing;
	private float alpha;
	private float originX;
	private float originY;
	private float scale = 1f;

	/** Where the view is heading; {@code shown*} glide towards it so zooming and jumping to a waypoint animate. */
	private double viewX;
	private double viewZ;
	private double shownX;
	private double shownZ;
	private float shownPpb = -1f;
	private long lastFrame;
	private int zoom = lastZoom;
	private boolean dragging;
	private double dragLastX;
	private double dragLastY;

	private int builtX;
	private int builtZ;
	private int builtBlocks;
	private long builtAt;
	private @Nullable ClientLevel builtLevel;

	private final Spring listScroll = Spring.ui(0f);
	private float listScrollTarget;

	private @Nullable Waypoint selected;
	private final TextSetting name = new TextSetting("name", "Name", "", 32, "Waypoint name");
	private final TextSetting fx = new TextSetting("x", "X", "", 9);
	private final TextSetting fy = new TextSetting("y", "Y", "", 9);
	private final TextSetting fz = new TextSetting("z", "Z", "", 9);
	private final List<TextField> coordFields = new ArrayList<>();
	private final List<Widget> editor = new ArrayList<>();
	private final List<Widget> chrome = new ArrayList<>();
	private boolean dirty;

	public WorldMapScreen() {
		super(Component.literal("Map"));
	}

	@Override
	protected void init() {
		LocalPlayer player = minecraft.player;
		if (player != null && builtLevel == null) {
			viewX = player.getX();
			viewZ = player.getZ();
		}
		chrome.clear();
		chrome.add(new Button(SIDE_X, 30, SIDE_W, 18, Icons.PLUS, "Add waypoint here", true, this::addHere));
		buildEditor();
	}

	// Waypoints

	private void addHere() {
		LocalPlayer player = minecraft.player;
		if (player != null) {
			add(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		}
	}

	private void add(int x, int y, int z) {
		select(Waypoints.add(Waypoints.nextName(), x, y, z));
		// Scroll the list so the new row shows.
		listScrollTarget = Math.max(0f, Waypoints.all().size() * ROW_H - listHeight());
	}

	private void select(@Nullable Waypoint w) {
		commit();
		selected = w;
		if (w != null) {
			name.set(w.name);
			fx.set(Integer.toString(w.x));
			fy.set(Integer.toString(w.y));
			fz.set(Integer.toString(w.z));
		}
		buildEditor();
	}

	private void buildEditor() {
		editor.clear();
		coordFields.clear();
		Waypoint w = selected;
		if (w == null) {
			return;
		}
		float y = EDIT_TOP + 12f;
		editor.add(new TextField(SIDE_X, y, SIDE_W, name));
		y += 31f;
		float third = (SIDE_W - 8f) / 3f;
		TextSetting[] coords = {fx, fy, fz};
		for (int i = 0; i < 3; i++) {
			TextField field = new TextField(SIDE_X + i * (third + 4f), y, third, coords[i]);
			coordFields.add(field);
			editor.add(field);
		}
		y += 31f;
		List<String> palette = Arrays.stream(Waypoints.PALETTE).mapToObj(Colors::toHex).toList();
		editor.add(new Swatches(SIDE_X, y, SIDE_W, "Colour", palette, () -> Colors.toHex(w.color), hex -> {
			w.color = Colors.parse(hex, w.color);
			dirty = true;
		}));
		y += 33f;
		boolean tracked = Waypoints.isTracked(w);
		// The GPS only points within the waypoint's own dimension, so other dimensions get no Track button.
		float bx = SIDE_X;
		if (w.dimension.equals(Waypoints.dimension())) {
			editor.add(new Button(bx, y, third, 18, Icons.NAVIGATION, tracked ? "Stop" : "Track", tracked, () -> {
				Waypoints.track(tracked ? null : w.name);
				buildEditor();
			}));
			bx += third + 4f;
		}
		editor.add(new Button(bx, y, third, 18, Icons.EYE, w.visible ? "Hide" : "Show", false, () -> {
			w.visible = !w.visible;
			dirty = true;
			buildEditor();
		}));
		bx += third + 4f;
		editor.add(new Button(bx, y, third, 18, Icons.TRASH, "Delete", false, this::deleteSelected));
	}

	private void deleteSelected() {
		Waypoint w = selected;
		if (w != null) {
			Waypoints.remove(w.name);
			selected = null;
			dirty = false;
			buildEditor();
		}
	}

	/** Copies the text fields onto the selected waypoint when they hold something valid. */
	private void applyFields() {
		Waypoint w = selected;
		if (w == null) {
			return;
		}
		String n = name.get().trim();
		if (!n.isEmpty() && !n.equals(w.name) && Waypoints.rename(w, n)) {
			dirty = true;
		}
		Integer x = parse(fx.get());
		Integer y = parse(fy.get());
		Integer z = parse(fz.get());
		if (x != null && x != w.x) {
			w.x = x;
			dirty = true;
		}
		if (y != null && y != w.y) {
			w.y = y;
			dirty = true;
		}
		if (z != null && z != w.z) {
			w.z = z;
			dirty = true;
		}
	}

	private static @Nullable Integer parse(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void commit() {
		applyFields();
		if (dirty) {
			Waypoints.save();
			dirty = false;
		}
	}

	// Terrain

	private float ppb() {
		return shownPpb > 0 ? shownPpb : ZOOMS[zoom];
	}

	/** Eases the shown view towards the target. Dragging moves both at once so the map sticks to the cursor. */
	private void glide() {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 1f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		if (shownPpb < 0 || dragging) {
			shownX = viewX;
			shownZ = viewZ;
			if (shownPpb < 0) {
				shownPpb = ZOOMS[zoom];
			}
			return;
		}
		float k = 1f - (float) Math.exp(-dt * 14f);
		shownX += (viewX - shownX) * k;
		shownZ += (viewZ - shownZ) * k;
		// Zoom eases in log space so each step feels the same speed.
		double logNow = Math.log(shownPpb);
		shownPpb = (float) Math.exp(logNow + (Math.log(ZOOMS[zoom]) - logNow) * k);
	}

	private void centerOnPlayer() {
		if (minecraft.player != null) {
			viewX = minecraft.player.getX();
			viewZ = minecraft.player.getZ();
		}
	}

	private static int blocksFor(int zoom) {
		return Math.max(1, Math.round(1f / ZOOMS[zoom]));
	}

	private void rebuildIfNeeded() {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return;
		}
		int blocks = blocksFor(zoom);
		// Rebuild when the view nears the texture's edge, the zoom or world changes, or every couple of seconds for new chunks.
		double margin = (SIZE * blocks - Math.max(MAP_W, MAP_H) / ZOOMS[zoom]) / 2.0;
		boolean outside = Math.abs(viewX - builtX) > margin || Math.abs(viewZ - builtZ) > margin;
		boolean stale = !dragging && System.currentTimeMillis() - builtAt > 2000;
		if (texture == null || builtLevel != level || builtBlocks != blocks || outside || stale) {
			build(level, player, (int) Math.floor(viewX), (int) Math.floor(viewZ), blocks);
		}
	}

	private void build(ClientLevel level, LocalPlayer player, int cx, int cz, int blocks) {
		if (texture == null) {
			texture = new DynamicTexture(() -> "Justaclient world map", SIZE, SIZE, true);
			minecraft.getTextureManager().register(TEXTURE, texture);
		}
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		TerrainMap.fill(image, SIZE, level, player.getBlockY() + 1, cx, cz, blocks);
		texture.upload();
		builtX = cx;
		builtZ = cz;
		builtBlocks = blocks;
		builtLevel = level;
		builtAt = System.currentTimeMillis();
	}

	private float screenX(double blockX) {
		return MAP_X + MAP_W / 2f + (float) ((blockX - shownX) * ppb());
	}

	private float screenY(double blockZ) {
		return MAP_Y + MAP_H / 2f + (float) ((blockZ - shownZ) * ppb());
	}

	private double blockX(double localX) {
		return shownX + (localX - MAP_X - MAP_W / 2f) / ppb();
	}

	private double blockZ(double localY) {
		return shownZ + (localY - MAP_Y - MAP_H / 2f) / ppb();
	}

	private static boolean onMap(double mx, double my) {
		return mx >= MAP_X && mx < MAP_X + MAP_W && my >= MAP_Y && my < MAP_Y + MAP_H;
	}

	private @Nullable Waypoint waypointAt(double mx, double my) {
		Waypoint best = null;
		double bestDist = 6 * 6;
		for (Waypoint w : Waypoints.here()) {
			double dx = mx - screenX(w.x + 0.5);
			double dy = my - screenY(w.z + 0.5);
			double d = dx * dx + dy * dy;
			if (d < bestDist) {
				best = w;
				bestDist = d;
			}
		}
		return best;
	}

	// Drawing

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float t = open.animateTo(closing ? 0f : 1f).get();
		alpha = t;
		if (closing && open.isDone()) {
			minecraft.setScreen(null);
			return;
		}
		if (minecraft.level == null || minecraft.player == null) {
			minecraft.setScreen(null);
			return;
		}
		applyFields();
		glide();
		rebuildIfNeeded();
		float fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		scale = fit * (0.94f + 0.06f * t);
		originX = (width - W * scale) / 2f;
		originY = (height - H * scale) / 2f + (1f - t) * 6f;
		double mx = (mouseX - originX) / scale;
		double my = (mouseY - originY) / scale;

		g.pose().pushMatrix();
		g.pose().translate(originX, originY);
		g.pose().scale(scale, scale);
		Draw.panel(g, 0, 0, W, H, t);
		drawMap(g, mx, my, t);
		drawSidebar(g, mx, my, t);
		g.pose().popMatrix();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, Colors.fade(0x66000000, alpha));
	}

	private void drawMap(GuiGraphics g, double mx, double my, float t) {
		float radius = Math.min(8f, JacConfig.get().style.cornerRadius);
		Draw.rect(g, MAP_X, MAP_Y, MAP_W, MAP_H, radius, Colors.fade(0xE00B0D12, t));
		g.enableScissor((int) MAP_X, (int) MAP_Y, (int) (MAP_X + MAP_W), (int) (MAP_Y + MAP_H));
		if (texture != null) {
			float cell = ppb() * builtBlocks;
			g.pose().pushMatrix();
			g.pose().translate(screenX(builtX), screenY(builtZ));
			g.pose().scale(cell, cell);
			g.pose().translate(-SIZE / 2f, -SIZE / 2f);
			g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, SIZE, SIZE, SIZE, SIZE, Colors.fade(0xFFFFFFFF, t));
			g.pose().popMatrix();
		}
		// Chunk lines fade in once a chunk is big enough to read.
		float grid = Math.max(0f, Math.min(1f, (ppb() - 1.2f) / 1.5f));
		if (grid > 0.01f) {
			int lineColor = Colors.fade(0x22FFFFFF, t * grid);
			double left = Math.floor(blockX(MAP_X) / 16) * 16;
			for (double bx = left; screenX(bx) < MAP_X + MAP_W; bx += 16) {
				Draw.rect(g, screenX(bx), MAP_Y, 0.5f, MAP_H, 0, lineColor);
			}
			double top = Math.floor(blockZ(MAP_Y) / 16) * 16;
			for (double bz = top; screenY(bz) < MAP_Y + MAP_H; bz += 16) {
				Draw.rect(g, MAP_X, screenY(bz), MAP_W, 0.5f, 0, lineColor);
			}
		}
		drawRoute(g, t);
		Waypoint hovered = !dragging && onMap(mx, my) ? waypointAt(mx, my) : null;
		for (Waypoint w : Waypoints.here()) {
			float x = screenX(w.x + 0.5);
			float y = screenY(w.z + 0.5);
			int color = w.visible ? w.color : Colors.withAlpha(w.color, 0x70);
			if (w == selected) {
				Draw.outline(g, x - 6.5f, y - 6.5f, 13f, 13f, 6.5f, 1.2f, Colors.fade(0xFFFFFFFF, t));
			}
			Draw.circle(g, x, y, 4.2f, Colors.fade(0xFF101218, t));
			Draw.circle(g, x, y, 3.2f, Colors.fade(color, t));
			if (w == hovered || w == selected || ppb() >= 0.5f) {
				float tw = JacText.width(w.name, Theme.SMALL, JacText.Weight.SEMIBOLD);
				Draw.rect(g, x - tw / 2f - 3f, y - 15f, tw + 6f, 9f, 3f, Colors.fade(0xB0000000, t));
				JacText.drawCentered(g, w.name, x, y - 14f, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}
		LocalPlayer player = minecraft.player;
		if (player != null) {
			float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			float px = screenX(player.getX(partial));
			float py = screenY(player.getZ(partial));
			g.pose().pushMatrix();
			g.pose().translate(px, py);
			g.pose().rotate((float) Math.toRadians(player.getYRot(partial) + 180f));
			JacText.icon(g, Icons.NAVIGATION, -6.5f, -6.5f, 13f, Colors.fade(0xA0000000, t));
			JacText.icon(g, Icons.NAVIGATION, -5.5f, -5.5f, 11f, Colors.fade(0xFFFFFFFF, t));
			g.pose().popMatrix();
		}
		g.disableScissor();

		// Hint along the bottom, and the block under the cursor along the top.
		String hint = "Drag or arrows to move  ·  Scroll to zoom  ·  Right-click to add  ·  Space to find you";
		float hw = JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR);
		float hx = MAP_X + (MAP_W - hw) / 2f;
		Draw.rect(g, hx - 6f, MAP_Y + MAP_H - 17f, hw + 12f, 11f, 5.5f, Colors.fade(0x99000000, t));
		JacText.draw(g, hint, hx, MAP_Y + MAP_H - 15.5f, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		if (onMap(mx, my) && !StreamModeFeature.hidesCoordinates()) {
			String at = "X " + (int) Math.floor(blockX(mx)) + "   Z " + (int) Math.floor(blockZ(my));
			float aw = JacText.width(at, Theme.SMALL, JacText.Weight.SEMIBOLD);
			Draw.rect(g, MAP_X + 6f, MAP_Y + 6f, aw + 12f, 11f, 5.5f, Colors.fade(0x99000000, t));
			JacText.draw(g, at, MAP_X + 12f, MAP_Y + 7.5f, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
		}
		String z = Math.round(ZOOMS[zoom] * 100) + "%";
		float zw = JacText.width(z, Theme.SMALL, JacText.Weight.SEMIBOLD);
		Draw.rect(g, MAP_X + MAP_W - zw - 18f, MAP_Y + 6f, zw + 12f, 11f, 5.5f, Colors.fade(0x99000000, t));
		JacText.draw(g, z, MAP_X + MAP_W - zw - 12f, MAP_Y + 7.5f, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
		// Back to you: shown once the view has wandered off.
		LocalPlayer me = minecraft.player;
		boolean away = me != null && Math.hypot(me.getX() - viewX, me.getZ() - viewZ) * ppb() > 24;
		float locate = locateAnim.animateTo(away ? 1f : 0f).get() * t;
		if (locate > 0.01f) {
			float lx = locateX(zw);
			boolean hover = mx >= lx && mx < lx + 13f && my >= MAP_Y + 5f && my < MAP_Y + 18f;
			Draw.rect(g, lx, MAP_Y + 5f, 13f, 13f, 6.5f, Colors.fade(hover ? 0xCC202430 : 0x99000000, locate));
			JacText.icon(g, Icons.LOCATE_FIXED, lx + 3f, MAP_Y + 8f, 7f, Colors.fade(hover ? JacConfig.accent() : Theme.TEXT, locate));
		}
	}

	private float locateX(float zoomLabelW) {
		return MAP_X + MAP_W - zoomLabelW - 18f - 17f;
	}

	/** A dotted line from you to the selected waypoint, with the distance halfway along. */
	private void drawRoute(GuiGraphics g, float t) {
		LocalPlayer player = minecraft.player;
		if (selected == null || player == null || !selected.dimension.equals(Waypoints.dimension())) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float ax = screenX(player.getX(partial));
		float ay = screenY(player.getZ(partial));
		float bx = screenX(selected.x + 0.5);
		float by = screenY(selected.z + 0.5);
		float len = (float) Math.hypot(bx - ax, by - ay);
		if (len < 14f) {
			return;
		}
		// The dots march towards the waypoint.
		float offset = (System.currentTimeMillis() % 1000L) / 1000f * 6f;
		int color = Colors.fade(Colors.withAlpha(selected.color, 0xD0), t);
		for (float d = 8f + offset; d < len - 6f; d += 6f) {
			float f = d / len;
			Draw.circle(g, ax + (bx - ax) * f, ay + (by - ay) * f, 1.8f, Colors.fade(0x90000000, t));
			Draw.circle(g, ax + (bx - ax) * f, ay + (by - ay) * f, 1.2f, color);
		}
		double blocks = Math.hypot(player.getX() - selected.x - 0.5, player.getZ() - selected.z - 0.5);
		String label = blocks >= 1000 ? String.format("%.1f km", blocks / 1000) : String.format("%.0f m", blocks);
		float lw = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD);
		float cx = (ax + bx) / 2f;
		float cy = (ay + by) / 2f;
		Draw.rect(g, cx - lw / 2f - 4f, cy - 5f, lw + 8f, 10f, 5f, Colors.fade(0xB0000000, t));
		JacText.drawCentered(g, label, cx, cy - 3.5f, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
	}

	private float listHeight() {
		return (selected != null ? EDIT_TOP - 6f : H - PAD - 4f) - LIST_TOP;
	}

	private void drawSidebar(GuiGraphics g, double mx, double my, float t) {
		List<Waypoint> all = Waypoints.all();
		JacText.draw(g, "Waypoints", SIDE_X, 13f, Colors.fade(Theme.TEXT, t), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String count = Integer.toString(all.size());
		JacText.draw(g, count, SIDE_X + SIDE_W - JacText.width(count, Theme.LABEL, JacText.Weight.REGULAR), 15f, Colors.fade(Theme.TEXT_DIM, t),
			Theme.LABEL, JacText.Weight.REGULAR);
		for (Widget w : chrome) {
			w.render(g, mx, my, t);
		}

		float listH = listHeight();
		listScrollTarget = Math.max(0f, Math.min(listScrollTarget, all.size() * ROW_H - listH));
		listScroll.setTarget(listScrollTarget);
		float scroll = listScroll.update();
		g.enableScissor((int) SIDE_X, (int) LIST_TOP, (int) (SIDE_X + SIDE_W), (int) (LIST_TOP + listH));
		if (all.isEmpty()) {
			JacText.draw(g, "No waypoints yet.", SIDE_X + 2f, LIST_TOP + 4f, Colors.fade(Theme.TEXT_DIM, t), Theme.LABEL, JacText.Weight.REGULAR);
			JacText.draw(g, "Right-click the map to add one.", SIDE_X + 2f, LIST_TOP + 15f, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
		String dim = Waypoints.dimension();
		LocalPlayer player = minecraft.player;
		float radius = Math.min(5f, JacConfig.get().style.cornerRadius);
		for (int i = 0; i < all.size(); i++) {
			Waypoint w = all.get(i);
			float y = LIST_TOP + i * ROW_H - scroll;
			if (y + ROW_H < LIST_TOP || y > LIST_TOP + listH) {
				continue;
			}
			boolean here = w.dimension.equals(dim);
			boolean hover = mx >= SIDE_X && mx < SIDE_X + SIDE_W && my >= Math.max(y, LIST_TOP) && my < Math.min(y + ROW_H - 2f, LIST_TOP + listH);
			if (w == selected) {
				Draw.rect(g, SIDE_X, y, SIDE_W, ROW_H - 2f, radius, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0x40), t));
			} else if (hover) {
				Draw.rect(g, SIDE_X, y, SIDE_W, ROW_H - 2f, radius, Colors.fade(Theme.CONTROL, t));
			}
			float fade = t * (here ? 1f : 0.5f) * (w.visible ? 1f : 0.6f);
			Draw.circle(g, SIDE_X + 8f, y + 8f, 3.2f, Colors.fade(w.color, fade));
			String right;
			if (!here) {
				right = shortDimension(w.dimension);
			} else if (player != null) {
				right = (int) Math.sqrt(Math.pow(w.x + 0.5 - player.getX(), 2) + Math.pow(w.z + 0.5 - player.getZ(), 2)) + " m";
			} else {
				right = "";
			}
			float rw = JacText.width(right, Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, right, SIDE_X + SIDE_W - rw - 6f, y + 5f, Colors.fade(Theme.TEXT_DIM, fade), Theme.SMALL, JacText.Weight.REGULAR);
			float iconsRight = SIDE_X + SIDE_W - rw - 10f;
			if (Waypoints.isTracked(w)) {
				iconsRight -= 8f;
				JacText.icon(g, Icons.NAVIGATION, iconsRight, y + 4.5f, 7f, Colors.fade(JacConfig.accent(), fade));
			}
			String label = w.death ? w.name + " (death)" : w.name;
			float max = iconsRight - (SIDE_X + 16f) - 4f;
			while (label.length() > 1 && JacText.width(label, Theme.LABEL, JacText.Weight.REGULAR) > max) {
				label = label.substring(0, label.length() - 1);
			}
			JacText.draw(g, label, SIDE_X + 16f, y + 4.5f, Colors.fade(Theme.TEXT, fade), Theme.LABEL, JacText.Weight.REGULAR);
		}
		g.disableScissor();

		if (selected != null) {
			Draw.rect(g, SIDE_X, EDIT_TOP, SIDE_W, 0.75f, 0, Colors.fade(Theme.DIVIDER, t));
			for (Widget w : editor) {
				w.render(g, mx, my, t);
			}
			if (!selected.dimension.equals(dim)) {
				JacText.draw(g, "In " + shortDimension(selected.dimension) + ", track it from there.", SIDE_X, H - PAD - 8f, Colors.fade(Theme.TEXT_DIM, t),
					Theme.SMALL, JacText.Weight.REGULAR);
			}
		}
	}

	private static String shortDimension(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> id.substring(id.indexOf(':') + 1);
		};
	}

	// Input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return true;
		}
		double mx = (event.x() - originX) / scale;
		double my = (event.y() - originY) / scale;
		int button = event.button();
		boolean used = false;
		for (Widget w : List.copyOf(editor)) {
			used |= w.mouseClicked(mx, my, button);
		}
		if (used) {
			return true;
		}
		for (Widget w : chrome) {
			if (w.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		String zoomLabel = Math.round(ZOOMS[zoom] * 100) + "%";
		float lx = locateX(JacText.width(zoomLabel, Theme.SMALL, JacText.Weight.SEMIBOLD));
		if (locateAnim.get() > 0.5f && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mx >= lx && mx < lx + 13f && my >= MAP_Y + 5f && my < MAP_Y + 18f) {
			centerOnPlayer();
			return true;
		}
		if (onMap(mx, my)) {
			Waypoint hit = waypointAt(mx, my);
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				if (hit != null) {
					select(hit);
				}
				// Pick up the map where it is shown, even mid-glide.
				viewX = shownX;
				viewZ = shownZ;
				dragging = true;
				dragLastX = mx;
				dragLastY = my;
				return true;
			}
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit == null) {
				int x = (int) Math.floor(blockX(mx));
				int z = (int) Math.floor(blockZ(my));
				int y = minecraft.player.getBlockY();
				ClientLevel level = minecraft.level;
				if (level != null && level.hasChunk(x >> 4, z >> 4)) {
					y = TerrainMap.surfaceY(level, new BlockPos.MutableBlockPos(), x, z, minecraft.player.getBlockY() + 1) + 1;
				}
				add(x, y, z);
				return true;
			}
			return true;
		}
		float listH = listHeight();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mx >= SIDE_X && mx < SIDE_X + SIDE_W && my >= LIST_TOP && my < LIST_TOP + listH) {
			int index = (int) Math.floor((my - LIST_TOP + listScroll.get()) / ROW_H);
			List<Waypoint> all = Waypoints.all();
			if (index >= 0 && index < all.size()) {
				Waypoint w = all.get(index);
				select(w);
				if (w.dimension.equals(Waypoints.dimension())) {
					viewX = w.x + 0.5;
					viewZ = w.z + 0.5;
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			double mx = (event.x() - originX) / scale;
			double my = (event.y() - originY) / scale;
			viewX -= (mx - dragLastX) / ppb();
			viewZ -= (my - dragLastY) / ppb();
			dragLastX = mx;
			dragLastY = my;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double mx = (x - originX) / scale;
		double my = (y - originY) / scale;
		if (onMap(mx, my)) {
			int next = Math.max(0, Math.min(ZOOMS.length - 1, zoom + (scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0)));
			if (next != zoom) {
				// Keep the block under the cursor still while zooming.
				double bx = blockX(mx);
				double bz = blockZ(my);
				zoom = next;
				lastZoom = next;
				viewX = bx - (mx - MAP_X - MAP_W / 2f) / ZOOMS[next];
				viewZ = bz - (my - MAP_Y - MAP_H / 2f) / ZOOMS[next];
			}
			return true;
		}
		if (mx >= SIDE_X && my >= LIST_TOP && my < LIST_TOP + listHeight()) {
			listScrollTarget -= (float) scrollY * ROW_H * 1.5f;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (Widget w : editor) {
			if (w.capturesKeys()) {
				w.keyPressed(event.key());
				return true;
			}
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE || Justaclient.OPEN_MAP.matches(event)) {
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_DELETE && selected != null) {
			deleteSelected();
			return true;
		}
		if (key == GLFW.GLFW_KEY_SPACE) {
			centerOnPlayer();
			return true;
		}
		// Arrow keys pan a quarter of the view.
		double step = MAP_W / 4f / ZOOMS[zoom];
		switch (key) {
			case GLFW.GLFW_KEY_LEFT -> viewX -= step;
			case GLFW.GLFW_KEY_RIGHT -> viewX += step;
			case GLFW.GLFW_KEY_UP -> viewZ -= step;
			case GLFW.GLFW_KEY_DOWN -> viewZ += step;
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (Widget w : editor) {
			if (w.capturesKeys()) {
				int c = event.codepoint();
				boolean coord = w instanceof TextField f && coordFields.contains(f);
				if (event.isAllowedChatCharacter() && (!coord || Character.isDigit(c) || c == '-')) {
					w.charTyped(c);
				}
				return true;
			}
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			commit();
		}
	}

	@Override
	public void removed() {
		commit();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
