package dev.justaclient.render;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ScreenStyleFeature;
import dev.justaclient.render.text.JacText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Draws vanilla widget sprites in the client's style. Hooked into the GUI sprite and texture blits, so it reaches every
 * screen that uses the vanilla widgets, including other mods' screens. Returns false to let vanilla draw.
 */
public final class ScreenSkin {
	private ScreenSkin() {
	}

	private static ScreenStyleFeature feature() {
		return Features.get(ScreenStyleFeature.class);
	}

	private static float radius(float h) {
		ScreenStyleFeature f = feature();
		if (!f.rounded()) {
			return 0f;
		}
		return Math.min(JacConfig.get().style.cornerRadius * 0.7f, h / 2f);
	}

	/** Base fill for controls: the theme surface, made solid enough to read on any background. */
	private static int base(float alpha) {
		int surface = Colors.withAlpha(JacConfig.surface(), 0xFF);
		return Colors.withAlpha(Colors.lerp(surface, 0xFF000000, 0.15f), Math.round(0xD8 * alpha));
	}

	/** Called for every GUI sprite blit. {@code color} carries the widget's fade. */
	public static boolean sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h, int color) {
		if (!"minecraft".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		if (!path.startsWith("widget/")) {
			return false;
		}
		ScreenStyleFeature f = feature();
		if (f == null || !f.styling()) {
			return false;
		}
		float alpha = ((color >>> 24) & 0xFF) / 255f;
		String name = path.substring(7);
		switch (name) {
			case "button", "button_highlighted", "button_disabled" -> {
				if (!f.buttons()) {
					return false;
				}
				button(g, x, y, w, h, alpha, name.endsWith("highlighted"), name.endsWith("disabled"));
				return true;
			}
			case "slider", "slider_highlighted" -> {
				if (!f.sliders()) {
					return false;
				}
				button(g, x, y, w, h, alpha, name.endsWith("highlighted"), false);
				return true;
			}
			case "slider_handle", "slider_handle_highlighted" -> {
				if (!f.sliders()) {
					return false;
				}
				float r = radius(h);
				int accent = JacConfig.accent();
				boolean hot = name.endsWith("highlighted");
				Draw.rect(g, x + 1, y + 2, w - 2, h - 4, Math.min(r, (w - 2) / 2f),
					Colors.fade(hot ? accent : Colors.lerp(accent, 0xFFFFFFFF, 0.25f), alpha * (hot ? 1f : 0.85f)));
				return true;
			}
			case "text_field", "text_field_highlighted" -> {
				if (!f.fields()) {
					return false;
				}
				float r = radius(h);
				Draw.rect(g, x, y, w, h, r, Colors.fade(Colors.withAlpha(0xFF08090C, 0xE0), alpha));
				boolean focused = name.endsWith("highlighted");
				Draw.outline(g, x, y, w, h, r, focused ? 1f : 0.75f,
					Colors.fade(focused ? Colors.withAlpha(JacConfig.accent(), 0xD0) : 0x30FFFFFF, alpha));
				return true;
			}
			case "checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted" -> {
				if (!f.checkboxes()) {
					return false;
				}
				boolean on = name.contains("selected");
				boolean hot = name.endsWith("highlighted");
				float r = Math.min(radius(h), 4f);
				int accent = JacConfig.accent();
				if (on) {
					Draw.rect(g, x, y, w, h, r, Colors.fade(accent, alpha));
					float size = h * 0.62f;
					JacText.icon(g, Icons.CHECK, x + (w - size) / 2f, y + (h - size) / 2f, size, Colors.fade(0xFFFFFFFF, alpha));
				} else {
					Draw.rect(g, x, y, w, h, r, base(alpha));
				}
				Draw.outline(g, x, y, w, h, r, 1f, Colors.fade(hot ? Colors.withAlpha(accent, 0xE0) : 0x38FFFFFF, alpha));
				return true;
			}
			case "scroller_background" -> {
				if (!f.scrollbars()) {
					return false;
				}
				Draw.rect(g, x + 1, y, w - 2, h, f.rounded() ? (w - 2) / 2f : 0f, Colors.fade(0x30000000, alpha));
				return true;
			}
			case "scroller" -> {
				if (!f.scrollbars()) {
					return false;
				}
				Draw.rect(g, x + 1, y + 1, w - 2, h - 2, f.rounded() ? (w - 2) / 2f : 0f,
					Colors.fade(Colors.withAlpha(Colors.lerp(JacConfig.accent(), 0xFFFFFFFF, 0.3f), 0xC0), alpha));
				return true;
			}
			case "tab", "tab_highlighted", "tab_selected", "tab_selected_highlighted" -> {
				if (!f.tabs()) {
					return false;
				}
				boolean selected = name.contains("selected");
				boolean hot = name.endsWith("highlighted");
				float r = radius(h);
				Draw.rect(g, x + 1, y + 2, w - 2, h - 2, r, selected ? base(alpha) : Colors.fade(hot ? 0x40FFFFFF : 0x22FFFFFF, alpha));
				if (selected) {
					Draw.rect(g, x + w * 0.2f, y + h - 2.5f, w * 0.6f, 1.5f, 0.75f, Colors.fade(JacConfig.accent(), alpha));
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private static void button(GuiGraphics g, float x, float y, float w, float h, float alpha, boolean hot, boolean disabled) {
		float r = radius(h);
		int accent = JacConfig.accent();
		if (disabled) {
			Draw.rect(g, x, y, w, h, r, Colors.fade(base(0.55f), alpha));
			Draw.outline(g, x, y, w, h, r, 0.75f, Colors.fade(0x14FFFFFF, alpha));
			return;
		}
		Draw.rect(g, x, y, w, h, r, base(alpha));
		if (hot) {
			Draw.rect(g, x, y, w, h, r, Colors.fade(Colors.withAlpha(accent, 0x40), alpha));
			Draw.outline(g, x, y, w, h, r, 1f, Colors.fade(Colors.withAlpha(accent, 0xE0), alpha));
		} else {
			// A faint top highlight gives the flat surface some depth.
			Draw.verticalGradient(g, x, y, w, h, r, Colors.fade(0x12FFFFFF, alpha), 0x00FFFFFF);
			Draw.outline(g, x, y, w, h, r, 0.75f, Colors.fade(0x26FFFFFF, alpha));
		}
	}

	/** Called for every plain texture blit; replaces the list panel textures and header and footer dividers. */
	public static boolean texture(GuiGraphics g, Identifier id, int x, int y, int w, int h) {
		if (!"minecraft".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		if (!path.startsWith("textures/gui/")) {
			return false;
		}
		ScreenStyleFeature f = feature();
		if (f == null || !f.lists() || !f.styling()) {
			return false;
		}
		switch (path.substring(13)) {
			case "menu_list_background.png", "inworld_menu_list_background.png" -> {
				Draw.rect(g, x, y, w, h, 0, Colors.withAlpha(JacConfig.surface(), 0x70));
				return true;
			}
			case "header_separator.png", "inworld_header_separator.png", "footer_separator.png", "inworld_footer_separator.png" -> {
				float lineY = path.contains("header") ? y + h - 1 : y;
				Draw.horizontalGradient(g, x, lineY, w / 2f, 1, 0, 0x00FFFFFF, Colors.withAlpha(JacConfig.accent(), 0x70));
				Draw.horizontalGradient(g, x + w / 2f, lineY, w / 2f, 1, 0, Colors.withAlpha(JacConfig.accent(), 0x70), 0x00FFFFFF);
				return true;
			}
			case "menu_background.png", "inworld_menu_background.png" -> {
				Draw.rect(g, x, y, w, h, 0, Colors.withAlpha(JacConfig.surface(), 0x50));
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
