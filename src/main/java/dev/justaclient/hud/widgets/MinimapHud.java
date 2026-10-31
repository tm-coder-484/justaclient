package dev.justaclient.hud.widgets;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.ExploredMap;
import dev.justaclient.render.Icons;
import dev.justaclient.render.MapEntities;
import dev.justaclient.render.TerrainMap;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * A small top-down map of the terrain around you, coloured like vanilla maps, remembering places you have been (see
 * {@link ExploredMap}). Players and mobs show only while you can see them (see {@link MapEntities}); with the chat open
 * you can point at one to see its name, health and distance. No caves. In dimensions with a roof it maps the floor you
 * are standing on instead.
 */
public final class MinimapHud extends HudFeature {
	private static final int SIZE = 128;
	private static final Identifier TEXTURE = Justaclient.id("minimap");

	public enum Zoom implements ChoiceSetting.Named {
		NEAR("Near", 1), MEDIUM("Medium", 2), FAR("Far", 4);

		private final String label;
		final int blocks;

		Zoom(String label, int blocks) {
			this.label = label;
			this.blocks = blocks;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final NumberSetting size = number("size", "Size", 96, 64, 160, 4, NumberSetting.unit(" px"));
	private final ChoiceSetting<Zoom> zoom = choice("zoom", "Zoom", Zoom.NEAR);
	private final BoolSetting rotate = bool("rotate", "Rotate with view", true);
	private final BoolSetting waypoints = bool("waypoints", "Show waypoints", true);
	private final BoolSetting entities = bool("entities", "Players and mobs in view", true);
	private final BoolSetting coords = bool("coords", "Coordinates under the map", true);
	private @Nullable DynamicTexture texture;
	private int centerX = Integer.MIN_VALUE;
	private int centerZ;
	private int builtZoom;
	private long lastBuild;
	private @Nullable ClientLevel builtLevel;

	public MinimapHud() {
		super("minimap", "Minimap", "Top-down terrain map with waypoints, players and mobs", Icons.GLOBE, false, 1f, 0f);
		background.set(true);
	}

	@Override
	public void layout(boolean preview) {
		width = size.f();
		height = size.f() + (showCoords(preview) ? 12f : 0f);
	}

	private boolean showCoords(boolean preview) {
		return coords.on() && (preview || !StreamModeFeature.hidesCoordinates());
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		int blocks = zoom.get().blocks;
		int px = player.getBlockX();
		int pz = player.getBlockZ();
		// Rebuild after moving a couple of pixels' worth, when the zoom or world changes, or every few seconds for new chunks.
		boolean moved = Math.abs(px - centerX) >= 2 * blocks || Math.abs(pz - centerZ) >= 2 * blocks;
		if (moved || blocks != builtZoom || mc.level != builtLevel || System.currentTimeMillis() - lastBuild > 3000) {
			build(mc.level, player, px, pz, blocks);
		}
	}

	private void build(ClientLevel level, LocalPlayer player, int cx, int cz, int blocks) {
		if (texture == null) {
			texture = new DynamicTexture(() -> "Justaclient minimap", SIZE, SIZE, true);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
		}
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		TerrainMap.fill(image, SIZE, level, player.getBlockY() + 1, cx, cz, blocks);
		texture.upload();
		centerX = cx;
		centerZ = cz;
		builtZoom = blocks;
		builtLevel = level;
		lastBuild = System.currentTimeMillis();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		float s = size.f();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		panel(g, s, s);
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (preview || player == null || texture == null) {
			Draw.verticalGradient(g, 3, 3, s - 6, s - 6, Math.max(0f, radius - 2f), 0xFF4D7A3A, 0xFF35602A);
			drawPlayer(g, s / 2f, s / 2f, 0f);
			drawCoords(g, preview, s);
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double px = player.getX(partial);
		double pz = player.getZ(partial);
		int blocks = builtZoom;
		float scale = (s - 6f) / (SIZE * 0.68f);
		float yaw = player.getYRot(partial);
		float angle = rotate.on() ? (float) Math.toRadians(180f - yaw) : 0f;
		g.enableScissor(3, 3, (int) (s - 3), (int) (s - 3));
		g.pose().pushMatrix();
		g.pose().translate(s / 2f, s / 2f);
		g.pose().scale(scale, scale);
		g.pose().rotate(angle);
		// The texture is centred on the block the map was built at; shift by how far the player has moved since.
		g.pose().translate((float) (-(px - centerX - 0.5) / blocks) - SIZE / 2f, (float) (-(pz - centerZ - 0.5) / blocks) - SIZE / 2f);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
		g.pose().popMatrix();
		if (waypoints.on()) {
			for (Waypoint w : Waypoints.here()) {
				if (!w.visible) {
					continue;
				}
				float dx = (float) ((w.x + 0.5 - px) / blocks * scale);
				float dz = (float) ((w.z + 0.5 - pz) / blocks * scale);
				float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
				float rx = dx * c - dz * sn;
				float rz = dx * sn + dz * c;
				float limit = s / 2f - 7f;
				float far = Math.max(Math.abs(rx), Math.abs(rz));
				if (far > limit) {
					rx *= limit / far;
					rz *= limit / far;
				}
				Draw.circle(g, s / 2f + rx, s / 2f + rz, 3f, 0xFF101218);
				Draw.circle(g, s / 2f + rx, s / 2f + rz, 2.2f, w.color);
			}
		}
		LivingEntity hovered = null;
		float hoverX = 0f, hoverY = 0f;
		if (entities.on()) {
			Vector2f mouse = mouseLocal(g);
			float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
			float limit = s / 2f - 4f;
			float best = 4.5f * 4.5f;
			for (LivingEntity e : MapEntities.visible()) {
				float dx = (float) ((e.getX(partial) - px) / blocks * scale);
				float dz = (float) ((e.getZ(partial) - pz) / blocks * scale);
				float rx = dx * c - dz * sn;
				float rz = dx * sn + dz * c;
				if (Math.abs(rx) > limit || Math.abs(rz) > limit) {
					continue;
				}
				float x = s / 2f + rx;
				float y = s / 2f + rz;
				if (mouse != null) {
					float d = (mouse.x - x) * (mouse.x - x) + (mouse.y - y) * (mouse.y - y);
					if (d < best) {
						best = d;
						hovered = e;
						hoverX = x;
						hoverY = y;
					}
				}
				MapEntities.draw(g, e, x, y, 2f, 1f, false);
			}
			if (hovered != null) {
				MapEntities.draw(g, hovered, hoverX, hoverY, 2f, 1f, true);
			}
		}
		g.disableScissor();
		drawPlayer(g, s / 2f, s / 2f, rotate.on() ? 0f : (float) Math.toRadians(yaw + 180f));
		drawCoords(g, false, s);
		if (hovered != null) {
			MapEntities.tooltip(g, hovered, hoverX, hoverY - 2f, 1f);
		}
	}

	/** The mouse in this widget's coordinates while the chat frees the cursor, else null. */
	private static @Nullable Vector2f mouseLocal(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof ChatScreen)) {
			return null;
		}
		float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
		float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
		return new Matrix3x2f(g.pose()).invert().transformPosition(mx, my, new Vector2f());
	}

	private static void drawPlayer(GuiGraphics g, float x, float y, float angle) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().rotate(angle);
		JacText.icon(g, Icons.NAVIGATION, -5.5f, -5.5f, 11f, 0xA0000000);
		JacText.icon(g, Icons.NAVIGATION, -4.5f, -4.5f, 9f, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private void drawCoords(GuiGraphics g, boolean preview, float s) {
		if (!showCoords(preview)) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		String text = preview || player == null ? "128, 64, -342" : player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
		float w = JacText.width(text, TEXT, JacText.Weight.REGULAR);
		text(g, text, (s - w) / 2f, s + 3f, Colors.withAlpha(Theme.TEXT, 0xE0), TEXT, JacText.Weight.REGULAR);
	}
}
