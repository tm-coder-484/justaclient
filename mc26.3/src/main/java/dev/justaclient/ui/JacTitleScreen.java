package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.realmsclient.RealmsMainScreen;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.PerformanceModeFeature;
import dev.justaclient.feature.impl.TitleScreenFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.ImageBackground;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Justaclient's title screen: a wordmark, one glass menu, the signed-in account and what is loaded. It does what the
 * vanilla screen does (worlds, servers, Realms, options, language, accessibility, credits) in the client's own style.
 */
public final class JacTitleScreen extends Screen {
	private static final float ROW_H = 20f;
	private static final float MENU_W = 156f;
	private static final float SMALL_LINKS_GAP = 8f;

	private record Row(Icons icon, String label, @Nullable String badge, boolean enabled, Runnable action) {
	}

	/** The player's own background picture. Kept between title screens, freed on joining a world. */
	private static final ImageBackground PICTURE = new ImageBackground("title");

	private final List<Row> rows = new ArrayList<>();
	private final Animation intro = new Animation(Easing.CUBIC_OUT, 650, 0f);
	private final Spring marker = Spring.ui(0f);
	private final Animation markerAlpha = new Animation(Easing.CUBIC_OUT, 140, 0f);
	private final List<Animation> hover = new ArrayList<>();
	private @Nullable Supplier<PlayerSkin> skin;
	private int keyboardRow = -1;
	private boolean markerPlaced;
	private float menuX;
	private float menuY;
	/** On short windows the account card moves to the bottom right, above the status line. */
	private boolean accountRight;

	public JacTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	/** Frees the background picture's textures; it is decoded again when the title screen next shows it. */
	public static void freeBackground() {
		PICTURE.unload();
	}

	@Override
	protected void init() {
		rows.clear();
		rows.add(new Row(Icons.USER, "Singleplayer", null, true, () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
		boolean multiplayer = minecraft.allowsMultiplayer();
		rows.add(new Row(Icons.USERS, "Multiplayer", null, multiplayer, () -> minecraft.gui.setScreen(
			minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
		rows.add(new Row(Icons.GLOBE, "Realms", null, multiplayer && minecraft.allowsRealms(), () -> minecraft.gui.setScreen(new RealmsMainScreen(this))));
		rows.add(null);
		rows.add(new Row(Icons.SLIDERS, "Options", null, true, () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));
		rows.add(new Row(Icons.LAYOUT_DASHBOARD, "Client settings", Justaclient.OPEN_MENU.getTranslatedKeyMessage().getString(), true,
			() -> minecraft.gui.setScreen(new ClickGuiScreen(this))));
		rows.add(new Row(Icons.LOG_OUT, "Quit game", null, true, minecraft::stop));
		while (hover.size() < rows.size()) {
			hover.add(new Animation(Easing.CUBIC_OUT, 120, 0f));
		}
		skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
		menuX = Math.max(16f, width * 0.075f);
		// Sit a third of the way down, but move up on short windows so the menu and the links below it still fit.
		menuY = Math.max(56f, Math.min(height * 0.34f, height - menuHeight() - 70f));
		accountRight = menuY + menuHeight() + 22f > height - 46f;
	}

	private float menuHeight() {
		float h = 6f;
		for (Row row : rows) {
			h += row == null ? 9f : ROW_H;
		}
		return h + 2f;
	}

	private float rowY(int index) {
		float y = menuY + 4f;
		for (int i = 0; i < index; i++) {
			y += rows.get(i) == null ? 9f : ROW_H;
		}
		return y;
	}

	private int rowAt(double mx, double my) {
		if (mx < menuX + 4 || mx >= menuX + MENU_W - 4) {
			return -1;
		}
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			float y = rowY(i);
			if (row != null && my >= y && my < y + ROW_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		TitleScreenFeature feature = Features.get(TitleScreenFeature.class);
		TitleScreenFeature.Background bg = feature.background();
		if (bg == TitleScreenFeature.Background.IMAGE) {
			PICTURE.use(feature.imagePath(), PerformanceModeFeature.imageSize(), feature.imageBlur());
			if (PICTURE.ready()) {
				PICTURE.drawCover(g, width, height, 0xFFFFFFFF);
				Draw.rect(g, 0, 0, width, height, 0, Colors.withAlpha(0x000000, (int) (feature.dim() * 255)));
				Draw.horizontalGradient(g, 0, 0, width * 0.6f, height, 0, 0x70000000, 0x00000000);
				return;
			}
			drawGradient(g);
			String note = PICTURE.loading() ? "Loading your picture" : PICTURE.error() != null ? "Could not read the picture: " + PICTURE.error()
				: "Put a PNG, JPG or GIF in config/justaclient/backgrounds";
			JacText.draw(g, note, width - JacText.width(note, Theme.SMALL, JacText.Weight.REGULAR) - 8, 6, Theme.TEXT_DIM, Theme.SMALL, JacText.Weight.REGULAR);
			return;
		}
		if (bg == TitleScreenFeature.Background.GRADIENT) {
			drawGradient(g);
			return;
		}
		extractPanorama(g, partialTick);
		if (bg == TitleScreenFeature.Background.BLURRED) {
			Draw.glass(g, 0, 0, width, height, 0, 0x30101218, 1f);
		}
		// Darken the left side so the menu reads over bright panoramas.
		Draw.horizontalGradient(g, 0, 0, width * 0.6f, height, 0, 0x90000000, 0x00000000);
	}

	/** Slow soft blobs of the accent colours over a dark surface. */
	private void drawGradient(GuiGraphicsExtractor g) {
		Draw.verticalGradient(g, 0, 0, width, height, 0, 0xFF0E1016, 0xFF07080C);
		float t = (System.currentTimeMillis() % 120_000L) / 1000f;
		float size = Math.max(width, height);
		blob(g, width * (0.30f + 0.10f * (float) Math.sin(t * 0.11f)), height * (0.35f + 0.10f * (float) Math.cos(t * 0.13f)), size * 0.18f,
			Colors.withAlpha(JacConfig.accentAt(0f), 0x70));
		blob(g, width * (0.72f + 0.08f * (float) Math.cos(t * 0.09f)), height * (0.62f + 0.12f * (float) Math.sin(t * 0.07f)), size * 0.22f,
			Colors.withAlpha(JacConfig.accentAt(1f), 0x58));
		blob(g, width * (0.55f + 0.15f * (float) Math.sin(t * 0.05f + 2f)), height * (0.20f + 0.08f * (float) Math.sin(t * 0.12f + 1f)), size * 0.14f,
			Colors.withAlpha(Colors.lerp(JacConfig.accentAt(0.5f), 0xFFFFFFFF, 0.2f), 0x40));
		// A faint grid gives the empty space some texture, as in the loading screen mockup.
		int grid = 0x08FFFFFF;
		for (float x = (t * 2f) % 24f; x < width; x += 24f) {
			Draw.rect(g, x, 0, 0.5f, height, 0, grid);
		}
		for (float y = 0; y < height; y += 24f) {
			Draw.rect(g, 0, y, width, 0.5f, 0, grid);
		}
	}

	private static void blob(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		Draw.shadow(g, cx - r * 0.3f, cy - r * 0.3f, r * 0.6f, r * 0.6f, r * 0.3f, r, color);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		float a = intro.animateTo(1f).get();
		float slide = (1f - a) * 10f;

		// Wordmark.
		float wx = menuX;
		float wy = menuY - 44f + slide;
		JacText.draw(g, "justaclient", wx, wy, Colors.fade(Theme.TEXT, a), 24f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Visuals for Minecraft " + SharedConstants.getCurrentVersion().name(), wx + 1f, wy + JacText.lineHeight(24f) - 2f,
			Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);

		// Menu.
		g.pose().pushMatrix();
		g.pose().translate(0, slide * 0.6f);
		float h = menuHeight();
		Draw.panel(g, menuX, menuY, MENU_W, h, a);
		int hovered = rowAt(mouseX, mouseY - slide * 0.6f);
		int active = hovered >= 0 ? hovered : keyboardRow;
		if (active >= 0 && !rows.get(active).enabled) {
			active = -1;
		}
		markerAlpha.animateTo(active >= 0 ? 1f : 0f);
		if (active >= 0) {
			marker.setTarget(rowY(active));
			if (!markerPlaced) {
				marker.snap(rowY(active));
				markerPlaced = true;
			}
		}
		float my = marker.update();
		float m = markerAlpha.get() * a;
		int accent = JacConfig.accent();
		float radius = Math.min(5f, JacConfig.get().style.cornerRadius);
		if (m > 0.01f) {
			Draw.rect(g, menuX + 4, my, MENU_W - 8, ROW_H, radius, Colors.fade(Colors.withAlpha(accent, 0x38), m));
			Draw.rect(g, menuX + 4, my + 4, 1.5f, ROW_H - 8, 0.75f, Colors.fade(accent, m));
		}
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			float y = rowY(i);
			if (row == null) {
				Draw.rect(g, menuX + 12, y + 4, MENU_W - 24, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
				continue;
			}
			// Rows fade in one after another.
			float ra = Math.max(0f, Math.min(1f, a * 1.6f - i * 0.08f));
			float hv = hover.get(i).animateTo(i == active ? 1f : 0f).get();
			int color = row.enabled ? Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.55f + hv * 0.45f) : 0x60FFFFFF;
			float ty = y + (ROW_H - JacText.lineHeight(Theme.LABEL + 0.5f)) / 2f;
			JacText.icon(g, row.icon, menuX + 13 + hv * 1.5f, y + (ROW_H - 8f) / 2f, 8f, Colors.fade(i == active ? accent : color, ra));
			JacText.draw(g, row.label, menuX + 28 + hv * 1.5f, ty, Colors.fade(color, ra), Theme.LABEL + 0.5f, i == active ? JacText.Weight.SEMIBOLD
				: JacText.Weight.REGULAR);
			if (row.badge != null) {
				float bw = JacText.width(row.badge, Theme.SMALL, JacText.Weight.SEMIBOLD) + 8f;
				float bx = menuX + MENU_W - 10 - bw;
				Draw.rect(g, bx, y + 5, bw, ROW_H - 10, 3f, Colors.fade(Theme.CONTROL_HOVER, ra));
				JacText.draw(g, row.badge, bx + 4, y + (ROW_H - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(Theme.TEXT_DIM, ra), Theme.SMALL,
					JacText.Weight.SEMIBOLD);
			}
		}
		// Language and accessibility, as quiet links under the menu.
		float ly = menuY + h + SMALL_LINKS_GAP;
		link(g, "Language", menuX + 4, ly, mouseX, mouseY - slide * 0.6f, a);
		link(g, "Accessibility", menuX + 4 + JacText.width("Language", Theme.SMALL, JacText.Weight.REGULAR) + 12, ly, mouseX, mouseY - slide * 0.6f, a);
		g.pose().popMatrix();

		drawAccount(g, a);
		drawStatus(g, mouseX, mouseY, a);
	}

	private void link(GuiGraphicsExtractor g, String label, float x, float y, double mx, double my, float a) {
		float w = JacText.width(label, Theme.SMALL, JacText.Weight.REGULAR);
		boolean hv = mx >= x && mx < x + w && my >= y - 2 && my < y + 9;
		JacText.draw(g, label, x, y, Colors.fade(hv ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		if (hv) {
			Draw.rect(g, x, y + JacText.lineHeight(Theme.SMALL), w, 0.5f, 0, Colors.fade(Theme.TEXT_DIM, a));
		}
	}

	private float accountWidth() {
		String name = minecraft.getUser().getName();
		String kind = accountKind();
		return Math.max(JacText.width(name, Theme.LABEL, JacText.Weight.SEMIBOLD), JacText.width(kind, Theme.SMALL, JacText.Weight.REGULAR)) + 40f;
	}

	private String accountKind() {
		return isOnlineAccount() ? "Microsoft account" : "Offline account";
	}

	/**
	 * Most launchers (Prism, MultiMC, Modrinth, CurseForge) never pass an Xbox user id, so that alone is not a sign of an
	 * offline account. A signed-in account always has a real access token, which is a JWT; offline ones get a
	 * placeholder like "0".
	 */
	private boolean isOnlineAccount() {
		if (minecraft.isOfflineDeveloperMode()) {
			return false;
		}
		if (minecraft.getUser().getXuid().isPresent()) {
			return true;
		}
		String token = minecraft.getUser().getAccessToken();
		return token != null && token.length() > 100 && token.chars().filter(c -> c == '.').count() == 2;
	}

	private void drawAccount(GuiGraphicsExtractor g, float a) {
		String name = minecraft.getUser().getName();
		String kind = accountKind();
		float w = accountWidth();
		float h = 28f;
		float x = accountRight ? width - 12f - w : menuX;
		float y = accountRight ? height - 12f - JacText.lineHeight(Theme.SMALL) - 6f - h : height - h - 12f;
		Draw.panel(g, x, y, w, h, a);
		if (skin != null) {
			g.pose().pushMatrix();
			g.pose().translate(x + 6, y + 6);
			g.pose().scale(2f, 2f);
			PlayerFaceExtractor.extractRenderState(g, skin.get(), 0, 0, 8, Colors.withAlpha(0xFFFFFF, Math.round(a * 255)));
			g.pose().popMatrix();
		}
		JacText.draw(g, name, x + 28, y + 6, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		JacText.draw(g, kind, x + 28, y + 15.5f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
	}

	private void drawStatus(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric · Justaclient " + modVersion("justaclient");
		float right = width - 12f;
		float vy = height - 12f - JacText.lineHeight(Theme.SMALL);
		String credits = "Credits";
		float cw = JacText.width(credits, Theme.SMALL, JacText.Weight.REGULAR);
		link(g, credits, right - cw, vy, mouseX, mouseY, a);
		float vw = JacText.width(version + "  ·  ", Theme.SMALL, JacText.Weight.REGULAR);
		JacText.draw(g, version + "  ·  ", right - cw - vw, vy, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);

		List<String> pills = new ArrayList<>();
		if (FabricLoader.getInstance().isModLoaded("sodium")) {
			pills.add("Sodium " + modVersion("sodium") + " loaded");
		}
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			pills.add("Iris " + modVersion("iris") + " loaded");
		}
		float px = right;
		float py = vy - 22f - (accountRight ? 34f : 0f);
		for (String pill : pills) {
			float pw = JacText.width(pill, Theme.SMALL, JacText.Weight.REGULAR) + 22f;
			px -= pw;
			Draw.panel(g, px, py, pw, 16, a);
			Draw.circle(g, px + 8, py + 8, 2.2f, Colors.fade(0xFF22C55E, a));
			JacText.draw(g, pill, px + 14, py + (16 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
			px -= 6f;
		}
	}

	private static String modVersion(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(c -> {
			String v = c.getMetadata().getVersion().getFriendlyString();
			int plus = v.indexOf('+');
			return plus > 0 ? v.substring(0, plus) : v;
		}).orElse("?");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		int row = rowAt(mx, my);
		if (row >= 0 && rows.get(row).enabled) {
			activate(row);
			return true;
		}
		float ly = menuY + menuHeight() + SMALL_LINKS_GAP;
		float lw = JacText.width("Language", Theme.SMALL, JacText.Weight.REGULAR);
		if (my >= ly - 2 && my < ly + 9) {
			if (mx >= menuX + 4 && mx < menuX + 4 + lw) {
				minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
				return true;
			}
			float ax = menuX + 4 + lw + 12;
			if (mx >= ax && mx < ax + JacText.width("Accessibility", Theme.SMALL, JacText.Weight.REGULAR)) {
				minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
				return true;
			}
		}
		float cw = JacText.width("Credits", Theme.SMALL, JacText.Weight.REGULAR);
		float vy = height - 12f - JacText.lineHeight(Theme.SMALL);
		if (mx >= width - 12f - cw && mx < width - 12f && my >= vy - 2 && my < vy + 9) {
			minecraft.gui.setScreen(new CreditsAndAttributionScreen(this));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void activate(int row) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		rows.get(row).action.run();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Justaclient.OPEN_MENU.matches(event)) {
			minecraft.gui.setScreen(new ClickGuiScreen(this));
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP || key == InputConstants.KEY_TAB) {
			int dir = key == InputConstants.KEY_UP || key == InputConstants.KEY_TAB && event.hasShiftDown() ? -1 : 1;
			int next = keyboardRow;
			for (int i = 0; i < rows.size(); i++) {
				next = Math.floorMod(next + dir, rows.size());
				if (rows.get(next) != null && rows.get(next).enabled) {
					break;
				}
			}
			keyboardRow = next;
			return true;
		}
		if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) && keyboardRow >= 0) {
			activate(keyboardRow);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (rowAt(mouseX, mouseY) >= 0) {
			keyboardRow = -1;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
