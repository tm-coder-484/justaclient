package dev.justaclient.hud.widgets;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.media.AlbumArt;
import dev.justaclient.media.Lyrics;
import dev.justaclient.media.MediaSource;
import dev.justaclient.media.MediaState;
import dev.justaclient.media.NowPlaying;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.Projection;
import dev.justaclient.render.SmoothTexture;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * The Spotify card: album cover, title and artist, a progress bar with media buttons, and time-synced lyrics that fill
 * in as they are sung. The card takes its colours from the cover and lays a blurred copy of it behind everything.
 * Works with the Spotify desktop app on Windows, macOS and Linux and the Spotify web player on Windows (and, if allowed,
 * any other player Windows or Linux can see).
 */
public final class NowPlayingHud extends HudFeature {
	private static final Identifier COVER = Justaclient.id("now_playing/cover");
	private static final Identifier BACKDROP = Justaclient.id("now_playing/backdrop");
	private static final float ART = 42f;
	private static final float GAP = 6f;
	private static final float LYRICS_H = 24f;
	/** Shown for instrumental breaks. The client font has no music note glyph, so three dots stand in. */
	private static final String BREAK = "•  •  •";

	public enum LyricsMode implements ChoiceSetting.Named {
		OFF("Off"), LINE("Current line"), TWO("Current and next");

		private final String label;

		LyricsMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum SkyLayout implements ChoiceSetting.Named {
		FLAT("Flat"), CURVED("Curved, far away");

		private final String label;

		SkyLayout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum SkyAnchor implements ChoiceSetting.Named {
		CARDINAL("Nearest direction you face"), SOUTH("Always south"), FOLLOW("Follows you around");

		private final String label;

		SkyAnchor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum SkyMode implements ChoiceSetting.Named {
		OFF("Off"), LYRICS("Lyrics"), NOTES("Music notes");

		private final String label;

		SkyMode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final BoolSetting showCard = bool("card", "Show the card (off: only what is in the sky)", true);
	private final NumberSetting cardWidth = number("width", "Width", 184, 150, 260, 2, NumberSetting.unit(" px"));
	private final BoolSetting spotifyOnly = bool("spotify_only", "Spotify only (app or web player, ignore other players)", true);
	private final BoolSetting artColors = bool("art_colors", "Colours from the album cover", true);
	private final BoolSetting backdrop = bool("backdrop", "Blurred cover behind the card", true);
	private final ChoiceSetting<LyricsMode> lyrics = choice("lyrics", "Synced lyrics (from lrclib.net)", LyricsMode.TWO);
	private final BoolSetting karaoke = bool("karaoke", "Fill lyrics as they are sung", true).visibleWhen(() -> lyrics.get() != LyricsMode.OFF);
	private final ChoiceSetting<SkyMode> sky = choice("sky", "In the sky", SkyMode.OFF);
	private final NumberSetting skySize = number("sky_size", "Sky text size", 1.0, 0.5, 2.0, 0.1, v -> String.format("%.1fx", v))
		.visibleWhen(() -> sky.get() != SkyMode.OFF);
	private final NumberSetting skyHeight = number("sky_height", "Sky height (degrees above the horizon)", 30, 5, 75, 1, NumberSetting.unit("°"))
		.visibleWhen(() -> sky.get() != SkyMode.OFF);
	private final ChoiceSetting<SkyLayout> skyLayout = choice("sky_layout", "Sky text layout", SkyLayout.FLAT).visibleWhen(() -> sky.get() == SkyMode.LYRICS);
	private final ChoiceSetting<SkyAnchor> skyAnchor = choice("sky_anchor", "Sky position", SkyAnchor.CARDINAL).visibleWhen(() -> sky.get() != SkyMode.OFF);
	private final BoolSetting skyPulse = bool("sky_pulse", "Pulse to the beat (tempo from Deezer)", true).visibleWhen(() -> sky.get() != SkyMode.OFF);
	private final NumberSetting lyricTiming = number("lyric_timing", "Lyrics timing (+ shows them earlier)", 0.2, -1.0, 1.0, 0.05,
		v -> String.format("%+.2f s", v)).visibleWhen(this::wantsLyrics);
	private final BoolSetting skyInfo = bool("sky_info", "Cover and song name in the sky", true).visibleWhen(() -> sky.get() != SkyMode.OFF);
	private final BoolSetting equalizer = bool("equalizer", "Moving bars while playing", true);
	private final BoolSetting showPaused = bool("show_paused", "Keep showing while paused", true);
	private final KeySetting playKey = key("play_key", "Play / pause key", KeySetting.NONE);
	private final KeySetting nextKey = key("next_key", "Next track key", KeySetting.NONE);
	private final KeySetting previousKey = key("previous_key", "Previous track key", KeySetting.NONE);

	private @Nullable SmoothTexture cover;
	private @Nullable SmoothTexture back;
	private int uploadedVersion = -1;
	private float uploadedRadius = -1f;
	private boolean hasArt;
	private int accent = 0xFF4C8DFF;
	private long lastFrame = System.nanoTime();
	private int lyricIndex = -2;
	private final Animation skyAlpha = new Animation(Easing.CUBIC_OUT, 600, 0f);
	private final Animation skySlide = new Animation(Easing.CUBIC_OUT, 450, 0f);
	private final Animation skyLine = new Animation(Easing.CUBIC_OUT, 500, 0f);
	private final Animation skyInfoDrop = new Animation(Easing.CUBIC_OUT, 600, 0f);
	private Lyrics.@Nullable Line shownLine;
	private Lyrics.@Nullable Line shownAfter;
	private float lineKick;
	private float skyCardinal;
	private long skyFrame;
	private int skyIndex = -2;
	private float skyYaw;
	private final float[][] notes = new float[10][4];
	private long skyLast;
	private final Animation lyricSlide = new Animation(Easing.CUBIC_OUT, 320, 0f);
	private final Animation appear = new Animation(Easing.CUBIC_OUT, 260, 0f);
	private final Animation playing = new Animation(Easing.CUBIC_OUT, 200, 0f);
	private final boolean[] keyWasDown = new boolean[3];
	private boolean lyricsShown;
	/** Where the media buttons were last drawn, in widget space, for clicks from the chat screen. */
	private final float[] buttonX = new float[3];
	private float buttonY;

	public NowPlayingHud() {
		super("now_playing", "Spotify", "Album cover, track, progress, media keys and synced lyrics", Icons.MUSIC, true, 1f, 0.4f);
	}

	@Override
	protected void onEnable() {
		NowPlaying.start(spotifyOnly.on(), wantsLyrics());
	}

	@Override
	protected void onDisable() {
		NowPlaying.stop();
	}

	@Override
	public void tick() {
		NowPlaying.start(spotifyOnly.on(), wantsLyrics());
		Minecraft mc = Minecraft.getInstance();
		KeySetting[] keys = {playKey, nextKey, previousKey};
		MediaSource.Command[] commands = {MediaSource.Command.TOGGLE, MediaSource.Command.NEXT, MediaSource.Command.PREVIOUS};
		for (int i = 0; i < keys.length; i++) {
			boolean down = mc.screen == null && keys[i].isDown();
			if (down && !keyWasDown[i]) {
				NowPlaying.send(commands[i]);
			}
			keyWasDown[i] = down;
		}
	}

	private boolean wantsLyrics() {
		return lyrics.get() != LyricsMode.OFF || sky.get() == SkyMode.LYRICS;
	}

	private boolean showLyrics(boolean preview) {
		if (lyrics.get() == LyricsMode.OFF) {
			return false;
		}
		if (preview) {
			return true;
		}
		Lyrics.Status status = NowPlaying.lyrics().status();
		return status == Lyrics.Status.SYNCED;
	}

	@Override
	public void layout(boolean preview) {
		width = cardWidth.f();
		lyricsShown = showLyrics(preview);
		height = PAD * 2 + ART + (lyricsShown ? LYRICS_H - (lyrics.get() == LyricsMode.LINE ? 9f : 0f) : 0f);
	}

	@Override
	public boolean hasContent() {
		MediaState s = NowPlaying.state();
		return showCard.on() && s.hasTrack() && (s.status() == MediaState.Status.PLAYING || showPaused.on());
	}

	// Textures

	private void upload(float radius) {
		int version = NowPlaying.artVersion();
		if (version == uploadedVersion && radius == uploadedRadius) {
			return;
		}
		uploadedVersion = version;
		uploadedRadius = radius;
		AlbumArt art = NowPlaying.art();
		hasArt = art != null;
		if (art == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (cover == null) {
			cover = new SmoothTexture(() -> "Justaclient album cover", AlbumArt.COVER, AlbumArt.COVER);
			mc.getTextureManager().register(COVER, cover);
			back = new SmoothTexture(() -> "Justaclient album backdrop", AlbumArt.BACK_W, AlbumArt.BACK_H);
			mc.getTextureManager().register(BACKDROP, back);
		}
		NativeImage pixels = cover.getPixels();
		if (pixels != null) {
			// Bake the rounded corners into the cover's alpha, anti-aliased, at the size it is drawn.
			int n = AlbumArt.COVER;
			float r = radius * n / ART;
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					float a = cornerCoverage(x + 0.5f, y + 0.5f, n, r);
					int c = art.cover()[y * n + x];
					pixels.setPixel(x, y, (int) (a * 255f) << 24 | (c & 0xFFFFFF));
				}
			}
			cover.upload();
		}
		NativeImage backPixels = back == null ? null : back.getPixels();
		if (backPixels != null) {
			for (int y = 0; y < AlbumArt.BACK_H; y++) {
				for (int x = 0; x < AlbumArt.BACK_W; x++) {
					backPixels.setPixel(x, y, art.backdrop()[y * AlbumArt.BACK_W + x]);
				}
			}
			back.upload();
		}
	}

	private static float cornerCoverage(float x, float y, int n, float r) {
		if (r <= 0.5f) {
			return 1f;
		}
		float cx = Math.max(r - x, Math.max(0f, x - (n - r)));
		float cy = Math.max(r - y, Math.max(0f, y - (n - r)));
		if (cx <= 0f || cy <= 0f) {
			return 1f;
		}
		float d = (float) Math.sqrt(cx * cx + cy * cy) - r;
		return Math.max(0f, Math.min(1f, 0.5f - d));
	}

	// Drawing

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		float coverRadius = Math.min(5f, radius);
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		MediaState s = preview ? null : NowPlaying.state();
		if (!preview) {
			upload(coverRadius);
		}
		boolean art = !preview && hasArt && cover != null;
		int targetAccent = art && artColors.on() && NowPlaying.art() != null ? NowPlaying.art().accent() : JacConfig.accent();
		// Ease towards the new cover's colours instead of snapping on a track change.
		float k = 1f - (float) Math.exp(-dt * 6f);
		accent = Colors.lerp(accent, targetAccent, k);
		float a = appear.animateTo(1f).get();
		float play = playing.animateTo(preview || s.status() == MediaState.Status.PLAYING ? 1f : 0f).get();

		panel(g, width, height);
		if (art && backdrop.on()) {
			g.blit(RenderPipelines.GUI_TEXTURED, BACKDROP, 0, 0, 0f, 0f, (int) width, (int) height, AlbumArt.BACK_W, AlbumArt.BACK_H, AlbumArt.BACK_W,
				AlbumArt.BACK_H, Colors.fade(0xFFFFFFFF, 0.85f * a));
		}

		// Cover.
		float ax = PAD;
		float ay = PAD;
		if (art) {
			Draw.shadow(g, ax, ay + 1f, ART, ART, coverRadius, 5f, 0x60000000);
			g.pose().pushMatrix();
			g.pose().translate(ax, ay);
			g.pose().scale(ART / AlbumArt.COVER, ART / AlbumArt.COVER);
			g.blit(RenderPipelines.GUI_TEXTURED, COVER, 0, 0, 0f, 0f, AlbumArt.COVER, AlbumArt.COVER, AlbumArt.COVER, AlbumArt.COVER);
			g.pose().popMatrix();
		} else {
			Draw.gradient(g, ax, ay, ART, ART, coverRadius, Colors.lerp(accent, 0xFF000000, 0.15f), Colors.lerp(accent, 0xFF000000, 0.45f),
				Colors.lerp(accent, 0xFF000000, 0.7f), Colors.lerp(accent, 0xFF000000, 0.4f));
			JacText.icon(g, Icons.MUSIC, ax + ART / 2f - 8f, ay + ART / 2f - 8f, 16f, 0xD0FFFFFF);
		}

		float tx = PAD + ART + GAP;
		float tw = width - tx - PAD;
		String title = preview ? "Midnight City" : s.title();
		String artist = preview ? "M83 · Hurry Up, We're Dreaming" : s.album().isBlank() ? s.artist() : s.artist() + " · " + s.album();

		// Equalizer bars at the top right, settling flat when paused.
		float eqW = 0f;
		if (equalizer.on()) {
			eqW = 11f;
			float bx = width - PAD - eqW;
			double t = now / 1e9;
			for (int i = 0; i < 4; i++) {
				double wave = 0.5 + 0.5 * Math.sin(t * (5.1 + i * 1.7) + i * 1.3) * Math.sin(t * (2.3 + i * 0.9) + i);
				float h = 1.5f + (float) (6.5 * Math.abs(wave)) * play;
				Draw.rect(g, bx + i * 3f, PAD + 9f - h, 2f, h, 1f, Colors.withAlpha(accent, 0xE0));
			}
		}

		marquee(g, title, tx, PAD + 1f, tw - eqW - 3f, 7.5f, JacText.Weight.SEMIBOLD, Theme.TEXT, now);
		marquee(g, artist, tx, PAD + 11.5f, tw, TEXT - 0.5f, JacText.Weight.REGULAR, Colors.withAlpha(Theme.TEXT, 0xB0), now + 1_500_000_000L);

		// Progress bar with a knob at the play head.
		long duration = preview ? 243_000L : s.durationMs();
		long pos = preview ? 97_000L : NowPlaying.position();
		float frac = duration > 0 ? Math.min(1f, pos / (float) duration) : 0f;
		float barY = PAD + 24f;
		Draw.rect(g, tx, barY, tw, 2.5f, 1.25f, 0x33FFFFFF);
		if (frac > 0f) {
			Draw.horizontalGradient(g, tx, barY, Math.max(2.5f, tw * frac), 2.5f, 1.25f, Colors.lerp(accent, 0xFFFFFFFF, 0.25f), accent);
		}
		Draw.circle(g, tx + tw * frac, barY + 1.25f, 2.6f, 0xFFFFFFFF);

		float timeY = PAD + 31f;
		JacText.draw(g, time(pos), tx, timeY, Colors.withAlpha(Theme.TEXT, 0xA0), Theme.SMALL, JacText.Weight.REGULAR);
		String total = duration > 0 ? time(duration) : "";
		JacText.draw(g, total, tx + tw - JacText.width(total, Theme.SMALL, JacText.Weight.REGULAR), timeY, Colors.withAlpha(Theme.TEXT, 0xA0), Theme.SMALL,
			JacText.Weight.REGULAR);

		// Previous, play/pause, next, centred under the bar. Clickable while the chat is open.
		float mid = tx + tw / 2f;
		buttonY = timeY - 1.5f;
		Icons[] icons = {Icons.SKIP_BACK, play > 0.5f ? Icons.PAUSE : Icons.PLAY, Icons.SKIP_FORWARD};
		float[] sizes = {7f, 9f, 7f};
		float[] offsets = {-14f, 0f, 14f};
		for (int i = 0; i < 3; i++) {
			float size = sizes[i];
			buttonX[i] = mid + offsets[i];
			if (i == 1) {
				Draw.circle(g, buttonX[i], buttonY + 3.5f, 6.2f, Colors.withAlpha(0xFFFFFFFF, 0x26));
			}
			JacText.icon(g, icons[i], buttonX[i] - size / 2f, buttonY + 3.5f - size / 2f, size, i == 1 ? 0xFFFFFFFF : Colors.withAlpha(Theme.TEXT, 0xC8));
		}

		if (lyricsShown) {
			drawLyrics(g, preview, pos, PAD + ART + 6f, now);
		}
	}

	private void drawLyrics(GuiGraphics g, boolean preview, long pos, float y, long now) {
		Lyrics.Result result = NowPlaying.lyrics();
		String current;
		String next;
		float fill;
		int index;
		if (preview) {
			current = "Waiting in a car, waiting for a ride in the dark";
			next = "The night city grows, look and see her eyes, they glow";
			fill = 0.45f;
			index = 3;
		} else {
			pos += lyricLead();
			index = result.indexAt(pos);
			Lyrics.Line line = index >= 0 ? result.lines().get(index) : null;
			Lyrics.Line after = index + 1 < result.lines().size() ? result.lines().get(index + 1) : null;
			current = line == null ? BREAK : line.text().isBlank() ? BREAK : line.text();
			next = after == null ? "" : after.text().isBlank() ? BREAK : after.text();
			if (line != null && after != null) {
				long span = Math.min(8000L, Math.max(1L, after.timeMs() - line.timeMs()));
				fill = Math.min(1f, (pos - line.timeMs()) / (float) span);
			} else {
				fill = line == null ? 0f : 1f;
			}
		}
		if (index != lyricIndex) {
			// A new line slides up into place.
			if (lyricIndex != -2) {
				lyricSlide.snap(1f);
			}
			lyricIndex = index;
		}
		float slide = lyricSlide.animateTo(0f).get();
		float left = PAD + 2f;
		float avail = width - PAD * 2 - 4f;
		g.enableScissor((int) PAD, (int) (y - 1f), (int) (width - PAD), (int) (height - PAD + 2f));
		float lineY = y + slide * 9f;
		float size = fitSize(current, avail, 7f, JacText.Weight.SEMIBOLD);
		String shown = ellipsize(current, avail, size, JacText.Weight.SEMIBOLD);
		float w = JacText.width(shown, size, JacText.Weight.SEMIBOLD);
		float x = left + (avail - w) / 2f;
		int alpha = (int) (255 * (1f - slide * 0.6f));
		if (karaoke.on()) {
			JacText.draw(g, shown, x, lineY, Colors.withAlpha(Theme.TEXT, (int) (alpha * 0.42f)), size, JacText.Weight.SEMIBOLD);
			if (fill > 0f) {
				g.enableScissor((int) x - 1, (int) (y - 1f), (int) Math.ceil(x + w * fill), (int) (height - PAD + 2f));
				JacText.draw(g, shown, x, lineY, Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.55f), alpha), size, JacText.Weight.SEMIBOLD);
				g.disableScissor();
			}
		} else {
			JacText.draw(g, shown, x, lineY, Colors.withAlpha(Theme.TEXT, alpha), size, JacText.Weight.SEMIBOLD);
		}
		if (lyrics.get() == LyricsMode.TWO && !next.isEmpty()) {
			String nextShown = ellipsize(next, avail, Theme.SMALL, JacText.Weight.REGULAR);
			float nw = JacText.width(nextShown, Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, nextShown, left + (avail - nw) / 2f, lineY + 10f, Colors.withAlpha(Theme.TEXT, (int) (0x70 * (1f - slide))), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
		g.disableScissor();
	}

	// Sky

	/**
	 * Draws the current lyric line (or drifting notes) high in the sky, with the cover, song name and moving bars under
	 * it. Called from the HUD pass with the world's camera. The anchor sits 30 degrees above the horizon; in "follow" it
	 * stays put while you look around near it and glides back in front of you when you have looked away for a moment.
	 */
	public void drawSky(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || sky.get() == SkyMode.OFF || mc.player == null || mc.level == null) {
			return;
		}
		MediaState s = NowPlaying.state();
		if (!showCard.on()) {
			// The card is not drawing, so keep the cover texture and its colours up to date from here.
			upload(Math.min(5f, Math.min(6f, JacConfig.get().style.cornerRadius)));
			AlbumArt cover = NowPlaying.art();
			accent = hasArt && artColors.on() && cover != null ? cover.accent() : JacConfig.accent();
		}
		boolean playing = s.hasTrack() && s.status() == MediaState.Status.PLAYING;
		boolean lyricsReady = sky.get() == SkyMode.NOTES || NowPlaying.lyrics().status() == Lyrics.Status.SYNCED;
		boolean info = skyInfo.on() && s.hasTrack();
		float a = skyAlpha.animateTo(playing && (lyricsReady || info) ? 1f : 0f).get();
		if (a < 0.01f) {
			return;
		}
		long now = System.nanoTime();
		float dt = skyFrame == 0 ? 0f : Math.min(0.1f, (now - skyFrame) / 1e9f);
		skyFrame = now;
		updateAnchor(mc, dt);
		double yaw = Math.toRadians(skyYaw);
		double pitch = Math.toRadians(skyHeight.get());
		Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 center = Projection.camera().add(dir.scale(100));
		Vector2f at = Projection.toGui(center, g.guiWidth(), g.guiHeight());
		Vector2f side = Projection.toGui(center.add(right.scale(10)), g.guiWidth(), g.guiHeight());
		if (at == null || side == null) {
			return;
		}
		float perBlock = (float) Math.hypot(side.x - at.x, side.y - at.y) / 10f;
		int glow = Colors.lerp(accent, 0xFFFFFFFF, 0.55f);
		float size = Math.max(8f, Math.min(72f, perBlock * 7f * skySize.f()));
		long pos = NowPlaying.position() + lyricLead();
		float beat = beat(pos);

		// The lyric line and the one after it.
		boolean showLine = false;
		if (lyricsReady && sky.get() == SkyMode.LYRICS) {
			Lyrics.Result result = NowPlaying.lyrics();
			int index = result.indexAt(pos);
			Lyrics.Line line = index >= 0 ? result.lines().get(index) : null;
			Lyrics.Line after = index + 1 < result.lines().size() ? result.lines().get(index + 1) : null;
			showLine = !inBreak(line, after, pos);
			if (showLine && index != skyIndex) {
				if (skyIndex != -2) {
					skySlide.snap(1f);
				}
				skyIndex = index;
				// Each new line gives the visuals a kick, on top of the tempo.
				lineKick = 1f;
			}
			if (showLine) {
				shownLine = line;
				shownAfter = after;
			}
		}
		float lineAlpha = skyLine.animateTo(showLine ? 1f : 0f).get() * a;
		lineKick = Math.max(0f, lineKick - dt * 2.5f);
		if (lineAlpha > 0.01f && shownLine != null) {
			float fill = 1f;
			if (shownAfter != null) {
				long span = Math.min(8000L, Math.max(1L, shownAfter.timeMs() - shownLine.timeMs()));
				fill = Math.max(0f, Math.min(1f, (pos - shownLine.timeMs()) / (float) span));
			}
			float slide = skySlide.animateTo(0f).get();
			float y = at.y - JacText.lineHeight(size) / 2f + slide * size * 0.6f;
			float la = lineAlpha * (1f - slide * 0.8f);
			String next = shownAfter == null || shownAfter.text().isBlank() ? "" : shownAfter.text();
			if (skyLayout.get() == SkyLayout.CURVED) {
				drawCurved(g, shownLine.text(), at.x, y, size, la, fill, glow, true);
				if (!next.isEmpty()) {
					drawCurved(g, next, at.x, y + JacText.lineHeight(size) * 1.15f, size * 0.55f, lineAlpha * (1f - slide) * 0.75f, 0f, glow, false);
				}
			} else {
				drawFlat(g, shownLine.text(), at.x, y, size, la, fill, glow);
				if (!next.isEmpty()) {
					float ns = size * 0.55f;
					float nw = JacText.width(next, ns, JacText.Weight.REGULAR);
					JacText.draw(g, next, at.x - nw / 2f, y + JacText.lineHeight(size) + 2f, Colors.fade(Colors.withAlpha(0xFFFFFF, 0x90),
						lineAlpha * (1f - slide)), ns, JacText.Weight.REGULAR);
				}
			}
		}
		if (sky.get() == SkyMode.NOTES) {
			drawNotes(g, at.x, at.y, perBlock, a, glow);
		}
		if (info) {
			// Under the lyric (or in its place in a break or with music notes), easing between the two spots.
			// The curved next line bends down at its ends, so the row sits a little lower under it.
			float under = at.y + JacText.lineHeight(size) / 2f + JacText.lineHeight(size * 0.55f) + size * (skyLayout.get() == SkyLayout.CURVED ? 0.85f : 0.45f);
			float alone = at.y - size * 0.85f;
			float t = skyInfoDrop.animateTo(sky.get() == SkyMode.LYRICS && (showLine || lineAlpha > 0.01f) ? 1f : 0f).get();
			drawSkyInfo(g, s, at.x, alone + (under - alone) * t, size, a, beat);
		}
	}

	/**
	 * Moves the anchor's direction towards its target: the cardinal direction nearest the view (switching only once the
	 * view is well past the halfway point, so it does not flip back and forth), due south, or the view itself. It glides
	 * there rather than jumping, quickly at first and settling softly.
	 */
	private void updateAnchor(Minecraft mc, float dt) {
		float view = mc.gameRenderer.getMainCamera().yRot();
		float target = switch (skyAnchor.get()) {
			case SOUTH -> 0f;
			case FOLLOW -> view;
			case CARDINAL -> {
				if (Math.abs(net.minecraft.util.Mth.wrapDegrees(view - skyCardinal)) > 55f) {
					skyCardinal = Math.round(view / 90f) * 90f;
				}
				yield skyCardinal;
			}
		};
		if (dt <= 0f) {
			skyCardinal = Math.round(view / 90f) * 90f;
			skyYaw = skyAnchor.get() == SkyAnchor.CARDINAL ? skyCardinal : target;
			return;
		}
		float off = net.minecraft.util.Mth.wrapDegrees(target - skyYaw);
		// Following trails the view a little, so turning feels like the sky moving with you instead of a sticker.
		float speed = skyAnchor.get() == SkyAnchor.FOLLOW ? 6f : 3.5f;
		skyYaw = net.minecraft.util.Mth.wrapDegrees(skyYaw + off * Math.min(1f, dt * speed));
	}

	/** Time to show lyrics early by, so the words land with the voice despite polling and display delay. */
	private long lyricLead() {
		return Math.round(lyricTiming.get() * 1000.0);
	}

	/**
	 * True during a break: before the first line, on an empty line, after the last line has had time to be sung, or
	 * when a line has been up much longer than it takes to sing it and the next one is still a while off.
	 */
	private static boolean inBreak(Lyrics.@Nullable Line line, Lyrics.@Nullable Line after, long pos) {
		if (line == null || line.text().isBlank()) {
			return true;
		}
		long since = pos - line.timeMs();
		long singing = Math.min(9000L, 2500L + line.text().length() * 110L);
		if (after == null) {
			return since > singing;
		}
		return since > singing && after.timeMs() - pos > 2000L;
	}

	/** 0..1, peaking on each beat of the song's tempo (or at a gentle 100 bpm when unknown), plus a kick per lyric line. */
	private float beat(long pos) {
		if (!skyPulse.on()) {
			return 0f;
		}
		float bpm = NowPlaying.tempo();
		float beats = pos / 60_000f * (bpm > 0 ? bpm : 100f);
		float phase = beats - (float) Math.floor(beats);
		float pulse = (float) Math.exp(-phase * 5.0) * (bpm > 0 ? 1f : 0.5f);
		return Math.min(1f, pulse * 0.8f + lineKick * 0.6f);
	}

	private void drawFlat(GuiGraphics g, String text, float cx, float y, float size, float la, float fill, int glow) {
		float w = JacText.width(text, size, JacText.Weight.SEMIBOLD);
		float x = cx - w / 2f;
		// A soft dark halo keeps the words readable against a bright sky.
		Draw.shadow(g, x - size * 0.3f, y + size * 0.1f, w + size * 0.6f, size * 0.9f, size * 0.45f, size * 1.2f, Colors.fade(0x78000000, la));
		JacText.draw(g, text, x + size * 0.04f, y + size * 0.06f, Colors.fade(0x60000000, la), size, JacText.Weight.SEMIBOLD);
		JacText.draw(g, text, x, y, Colors.fade(Colors.withAlpha(0xFFFFFF, 0xB0), la), size, JacText.Weight.SEMIBOLD);
		if (fill > 0f) {
			g.enableScissor((int) x - 1, (int) (y - size), (int) Math.ceil(x + w * fill), (int) (y + size * 2));
			JacText.draw(g, text, x, y, Colors.fade(glow, la), size, JacText.Weight.SEMIBOLD);
			g.disableScissor();
		}
	}

	/**
	 * Text bent along a wide arc, letters tilting with it and shrinking a little towards the ends, as if painted on the
	 * inside of a faraway dome. Letters up to {@code fill} take the glow colour.
	 */
	private void drawCurved(GuiGraphics g, String text, float cx, float y, float size, float la, float fill, int glow, boolean main) {
		JacText.Weight weight = main ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR;
		float w = JacText.width(text, size, weight);
		if (w <= 0f) {
			return;
		}
		// A gentle bend: about 20 degrees at the ends of a long line, less for short ones.
		float radius = Math.max(w * 1.5f, size * 12f);
		float lineH = JacText.lineHeight(size);
		int[] cps = text.codePoints().toArray();
		// Two passes: the readability halo (following the arc letter by letter) first, so it never covers a letter.
		for (int pass = main ? 0 : 1; pass < 2; pass++) {
			float s = -w / 2f;
			for (int cp : cps) {
				String ch = new String(Character.toChars(cp));
				float cw = JacText.width(ch, size, weight);
				float mid = s + cw / 2f;
				s += cw;
				if (ch.isBlank()) {
					continue;
				}
				float angle = mid / radius;
				float edge = Math.min(1f, Math.abs(mid) / (w / 2f + 1f));
				float scale = 1f - 0.18f * edge * edge;
				float px = cx + radius * (float) Math.sin(angle);
				float py = y + lineH / 2f + radius * (1f - (float) Math.cos(angle));
				float ca = la * (1f - 0.35f * edge);
				g.pose().pushMatrix();
				g.pose().translate(px, py);
				g.pose().rotate(angle);
				g.pose().scale(scale, scale);
				if (pass == 0) {
					Draw.shadow(g, -cw / 2f - size * 0.1f, -lineH * 0.3f, cw + size * 0.2f, lineH * 0.6f, size * 0.3f, size * 0.9f, Colors.fade(0x26000000, ca));
				} else {
					boolean lit = main && (mid + w / 2f) / w <= fill;
					int color = lit ? glow : main ? Colors.withAlpha(0xFFFFFF, 0xB8) : Colors.withAlpha(0xFFFFFF, 0x90);
					if (main) {
						JacText.draw(g, ch, -cw / 2f + size * 0.04f, -lineH / 2f + size * 0.06f, Colors.fade(0x60000000, ca), size, weight);
					}
					JacText.draw(g, ch, -cw / 2f, -lineH / 2f, Colors.fade(color, ca), size, weight);
				}
				g.pose().popMatrix();
			}
		}
	}

	/**
	 * The cover with the title and artist beside it and moving bars after the title, centred on {@code cx} with its top
	 * edge at {@code top}. A soft halo behind the cover swells on the beat.
	 */
	private void drawSkyInfo(GuiGraphics g, MediaState s, float cx, float top, float size, float a, float beat) {
		float coverSize = size * 1.7f;
		float titleSize = size * 0.5f;
		float artistSize = size * 0.38f;
		float gap = size * 0.3f;
		float barsW = equalizer.on() ? titleSize * 1.6f : 0f;
		String artist = s.artist();
		float titleW = JacText.width(s.title(), titleSize, JacText.Weight.SEMIBOLD);
		float textW = Math.max(titleW + (barsW > 0 ? barsW + gap * 0.6f : 0f), JacText.width(artist, artistSize, JacText.Weight.REGULAR));
		float rowW = coverSize + gap + textW;
		float left = cx - rowW / 2f;
		Draw.shadow(g, left - size * 0.3f, top, rowW + size * 0.6f, coverSize, size * 0.4f, size * 1.2f, Colors.fade(0x66000000, a));
		if (beat > 0.01f) {
			float swell = coverSize * (0.1f + 0.25f * beat);
			Draw.shadow(g, left - swell * 0.3f, top - swell * 0.3f, coverSize + swell * 0.6f, coverSize + swell * 0.6f, coverSize * 0.2f, swell * 1.5f,
				Colors.fade(Colors.withAlpha(accent, 0xA0), a * beat));
		}
		float pop = 1f + 0.04f * beat;
		float cs = coverSize * pop;
		float co = (cs - coverSize) / 2f;
		if (hasArt && cover != null) {
			Draw.shadow(g, left, top + 1f, coverSize, coverSize, coverSize * 0.12f, size * 0.25f, Colors.fade(0x70000000, a));
			g.pose().pushMatrix();
			g.pose().translate(left - co, top - co);
			g.pose().scale(cs / AlbumArt.COVER, cs / AlbumArt.COVER);
			g.blit(RenderPipelines.GUI_TEXTURED, COVER, 0, 0, 0f, 0f, AlbumArt.COVER, AlbumArt.COVER, AlbumArt.COVER, AlbumArt.COVER, AlbumArt.COVER,
				AlbumArt.COVER, Colors.fade(0xFFFFFFFF, a));
			g.pose().popMatrix();
		} else {
			float r = coverSize * 0.12f;
			Draw.gradient(g, left, top, coverSize, coverSize, r, Colors.fade(Colors.lerp(accent, 0xFF000000, 0.15f), a),
				Colors.fade(Colors.lerp(accent, 0xFF000000, 0.45f), a), Colors.fade(Colors.lerp(accent, 0xFF000000, 0.7f), a),
				Colors.fade(Colors.lerp(accent, 0xFF000000, 0.4f), a));
			float icon = coverSize * 0.4f;
			JacText.icon(g, Icons.MUSIC, left + (coverSize - icon) / 2f, top + (coverSize - icon) / 2f, icon, Colors.fade(0xD0FFFFFF, a));
		}
		float tx = left + coverSize + gap;
		float textH = JacText.lineHeight(titleSize) + JacText.lineHeight(artistSize);
		float ty = top + (coverSize - textH) / 2f;
		JacText.draw(g, s.title(), tx + titleSize * 0.05f, ty + titleSize * 0.07f, Colors.fade(0x60000000, a), titleSize, JacText.Weight.SEMIBOLD);
		JacText.draw(g, s.title(), tx, ty, Colors.fade(0xFFFFFFFF, a), titleSize, JacText.Weight.SEMIBOLD);
		JacText.draw(g, artist, tx, ty + JacText.lineHeight(titleSize), Colors.fade(Colors.lerp(accent, 0xFFFFFFFF, 0.6f), a * 0.9f), artistSize,
			JacText.Weight.REGULAR);
		if (barsW > 0f) {
			// The card's moving bars, bigger, and kicked by the beat.
			float play = playing.animateTo(s.status() == MediaState.Status.PLAYING ? 1f : 0f).get();
			float bx = tx + titleW + gap * 0.6f;
			float base = ty + JacText.lineHeight(titleSize) * 0.85f;
			float barW = barsW / 4f * 0.6f;
			double t = System.nanoTime() / 1e9;
			for (int i = 0; i < 4; i++) {
				double wave = 0.5 + 0.5 * Math.sin(t * (5.1 + i * 1.7) + i * 1.3) * Math.sin(t * (2.3 + i * 0.9) + i);
				float h = titleSize * (0.15f + (0.55f * (float) Math.abs(wave) + 0.4f * beat * (i % 2 == 0 ? 1f : 0.7f)) * play);
				Draw.rect(g, bx + i * barsW / 4f, base - h, barW, h, barW / 2f, Colors.fade(Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.3f), 0xE8), a));
			}
		}
	}

	/** Music notes that rise and sway around the anchor, one burst per beat-ish interval. */
	private void drawNotes(GuiGraphics g, float cx, float cy, float perBlock, float a, int color) {
		long now = System.nanoTime();
		float dt = skyLast == 0 ? 0f : Math.min(0.1f, (now - skyLast) / 1e9f);
		skyLast = now;
		float spread = perBlock * 30f;
		for (float[] n : notes) {
			// n = {x offset, rise, life 0..1, phase}
			n[2] += dt * 0.25f;
			if (n[2] >= 1f || n[2] == 0f) {
				n[0] = (float) (Math.random() - 0.5) * 2f;
				n[1] = 0f;
				n[2] = (float) Math.random() * 0.3f + 0.0001f;
				n[3] = (float) (Math.random() * Math.PI * 2);
			}
			float life = n[2];
			float fade = Math.min(1f, life * 5f) * Math.min(1f, (1f - life) * 3f);
			float x = cx + n[0] * spread + (float) Math.sin(life * 6f + n[3]) * perBlock * 3f;
			float y = cy + spread * 0.3f - life * spread * 0.9f;
			float size = Math.max(5f, Math.min(48f, perBlock * 4f * skySize.f()));
			JacText.icon(g, Icons.MUSIC, x - size / 2f, y - size / 2f, size, Colors.fade(color, a * fade * 0.9f));
		}
	}

	/** Shrinks long lines a little before cutting them. */
	private static float fitSize(String text, float avail, float size, JacText.Weight weight) {
		float w = JacText.width(text, size, weight);
		return w <= avail ? size : Math.max(size * 0.8f, size * avail / w);
	}

	private static String ellipsize(String text, float avail, float size, JacText.Weight weight) {
		if (JacText.width(text, size, weight) <= avail) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && JacText.width(s + "…", size, weight) > avail) {
			s = s.substring(0, s.length() - 1);
		}
		return s.stripTrailing() + "…";
	}

	/** Text that scrolls sideways, pausing at the start, when it does not fit. */
	private void marquee(GuiGraphics g, String text, float x, float y, float avail, float size, JacText.Weight weight, int color, long now) {
		float w = JacText.width(text, size, weight);
		if (w <= avail) {
			text(g, text, x, y, color, size, weight);
			return;
		}
		float gap = 24f;
		float cycle = w + gap;
		float speed = 18f;
		float pause = 2.2f;
		double t = (now / 1e9) % (cycle / speed + pause);
		float offset = t < pause ? 0f : (float) ((t - pause) * speed);
		g.enableScissor((int) x, (int) (y - 2f), (int) (x + avail), (int) (y + size + 3f));
		text(g, text, x - offset, y, color, size, weight);
		text(g, text, x - offset + cycle, y, color, size, weight);
		g.disableScissor();
	}

	private static String time(long ms) {
		long sec = ms / 1000L;
		return sec >= 3600 ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60) : String.format("%d:%02d", sec / 60, sec % 60);
	}

	/** A click on the chat screen in GUI coordinates. Returns true if it pressed one of the card's media buttons. */
	public static boolean clickFromChat(double x, double y, int screenWidth, int screenHeight) {
		NowPlayingHud hud = dev.justaclient.feature.Features.get(NowPlayingHud.class);
		if (hud == null || !hud.isEnabled() || !hud.hasContent()) {
			return false;
		}
		float scale = hud.scale.f();
		return hud.click((x - hud.screenX(screenWidth)) / scale, (y - hud.screenY(screenHeight)) / scale);
	}

	private boolean click(double localX, double localY) {
		if (Math.abs(localY - (buttonY + 3.5f)) > 7f) {
			return false;
		}
		MediaSource.Command[] commands = {MediaSource.Command.PREVIOUS, MediaSource.Command.TOGGLE, MediaSource.Command.NEXT};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(localX - buttonX[i]) <= 6.5f) {
				NowPlaying.send(commands[i]);
				return true;
			}
		}
		return false;
	}
}
