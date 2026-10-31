package dev.justaclient.hud;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A draggable HUD widget. Each frame the renderer calls {@link #layout} to size the widget, then {@link #draw} with the
 * pose already moved to the widget's top-left and scaled.
 * <p>
 * The position is stored relative to the free space on screen (0 = flush left/top, 1 = flush right/bottom), so a widget
 * parked against an edge stays against it at every window size and GUI scale.
 */
public abstract class HudFeature extends Feature {
	public static final float PAD = 5f;
	/** Gap kept between widgets and the screen edge. */
	public static final float MARGIN = 4f;
	public static final float TEXT = 6.5f;
	public static final float ROW = 10f;

	public final BoolSetting background = new BoolSetting("background", "Background panel", true);
	public final NumberSetting scale = new NumberSetting("scale", "Scale", 1.0, 0.5, 2.0, 0.05, v -> Math.round(v * 100) + "%");
	public float relX;
	public float relY;
	private final float defaultX;
	private final float defaultY;
	protected float width;
	protected float height;

	protected HudFeature(String id, String name, String description, Icons icon, boolean enabledByDefault, float x, float y) {
		super(id, name, description, Category.HUD, icon, enabledByDefault);
		this.relX = this.defaultX = x;
		this.relY = this.defaultY = y;
	}

	/** Measures the widget into {@link #width} and {@link #height}. {@code preview} asks for sample content. */
	public abstract void layout(boolean preview);

	/** Draws at 0, 0 in widget space. */
	public abstract void draw(GuiGraphics g, boolean preview);

	/** False when there is nothing to show, e.g. no active effects. The editor always shows a preview instead. */
	public boolean hasContent() {
		return true;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public float scaledWidth() {
		return width * scale.f();
	}

	public float scaledHeight() {
		return height * scale.f();
	}

	public float screenX(float screenWidth) {
		return MARGIN + relX * Math.max(0f, screenWidth - 2 * MARGIN - scaledWidth());
	}

	public float screenY(float screenHeight) {
		return MARGIN + relY * Math.max(0f, screenHeight - 2 * MARGIN - scaledHeight());
	}

	/** Places the widget's top-left at x, y on a screen of the given size. */
	public void moveTo(float x, float y, float screenWidth, float screenHeight) {
		float freeX = screenWidth - 2 * MARGIN - scaledWidth();
		float freeY = screenHeight - 2 * MARGIN - scaledHeight();
		relX = freeX <= 0 ? 0f : Math.max(0f, Math.min(1f, (x - MARGIN) / freeX));
		relY = freeY <= 0 ? 0f : Math.max(0f, Math.min(1f, (y - MARGIN) / freeY));
	}

	public void resetPosition() {
		relX = defaultX;
		relY = defaultY;
		scale.reset();
	}

	@Override
	public void resetToDefaults() {
		super.resetToDefaults();
		resetPosition();
	}

	@Override
	public List<Setting<?>> settings() {
		List<Setting<?>> all = new ArrayList<>(super.settings());
		all.add(background);
		all.add(scale);
		return all;
	}

	/** Shows the widget itself, with sample content, over a dark backdrop. */
	@Override
	public void drawPreview(GuiGraphics g, float x, float y, float w, float h) {
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		Draw.verticalGradient(g, x, y, w, h, radius, 0xFF3A4A66, 0xFF1C2333);
		layout(true);
		float fit = Math.min(1f, Math.min((w - 6f) / width, (h - 6f) / height));
		g.pose().pushMatrix();
		g.pose().translate(x + (w - width * fit) / 2f, y + (h - height * fit) / 2f);
		g.pose().scale(fit, fit);
		draw(g, true);
		g.pose().popMatrix();
	}

	// Drawing helpers shared by the widgets.

	protected void panel(GuiGraphics g, float w, float h) {
		if (background.on()) {
			Draw.hudPanel(g, 0, 0, w, h);
		}
	}

	/** Text that stays readable without a panel behind it by adding a soft shadow. */
	protected void text(GuiGraphics g, String s, float x, float y, int color, float size, JacText.Weight weight) {
		if (!background.on()) {
			JacText.draw(g, s, x + 0.5f, y + 0.5f, Colors.fade(0xC0000000, Colors.alpha(color) / 255f), size, weight);
		}
		JacText.draw(g, s, x, y, color, size, weight);
	}

	protected void textRight(GuiGraphics g, String s, float right, float y, int color, float size, JacText.Weight weight) {
		text(g, s, right - JacText.width(s, size, weight), y, color, size, weight);
	}
}
