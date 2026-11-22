package dev.justaclient.config;

/**
 * The style tokens every widget reads. Changing a token restyles the whole client at once; presets are saved token sets.
 * Defaults are the "dark glass" look.
 */
public final class StyleTokens {
	/** Primary accent, #RRGGBB. */
	public String accent = "#4C8DFF";
	/** Optional second accent stop for gradients; null means a solid accent. */
	public String accentEnd = null;
	/** Cycles the accent through the hue wheel. */
	public boolean rainbowAccent = false;

	/** Panel base colour, #RRGGBB. */
	public String surface = "#16181E";
	/** Panel opacity, 0..1. */
	public float surfaceOpacity = 0.70f;

	/** Background blur behind panels. */
	public boolean blur = true;
	/** Also blur behind HUD widgets. Costs a blur pass every frame, so it can be turned off on its own. */
	public boolean hudBlur = true;
	/** Blur passes, 1 (soft) to 5 (strong). */
	public int blurStrength = 3;

	/** Panel corner radius in GUI pixels, 0..16. */
	public float cornerRadius = 6f;

	public Outline outline = Outline.OFF;

	public FontChoice font = FontChoice.INTER;

	public Motion motion = Motion.NORMAL;

	/** Which menu the menu key opens: the classic panel or the ring. */
	public Layout layout = Layout.CLASSIC;

	/** How the ring layout looks; edited live from the ring itself. */
	public Ring ring = new Ring();

	public enum Layout {
		CLASSIC, RING
	}

	/** Options for the ring layout. */
	public static final class Ring {
		public RingLook look = RingLook.PARTICLES;
		/** 0 still, 1 normal, 2 fast. */
		public int speed = 1;
		/** 0 few, 1 normal, 2 many. */
		public int density = 1;
		/** 0 off, 1 soft, 2 strong. */
		public int glow = 1;
		public Gauge leftGauge = Gauge.FPS;
		public Gauge rightGauge = Gauge.MEMORY;
		public boolean pageIcons = true;
		public boolean strip = true;
		public boolean connectors = true;

		void sanitize() {
			speed = Math.max(0, Math.min(2, speed));
			density = Math.max(0, Math.min(2, density));
			glow = Math.max(0, Math.min(2, glow));
			if (look == null) {
				look = RingLook.PARTICLES;
			}
			if (leftGauge == null) {
				leftGauge = Gauge.FPS;
			}
			if (rightGauge == null) {
				rightGauge = Gauge.MEMORY;
			}
		}
	}

	public enum RingLook {
		PARTICLES, DASHES, SOLID
	}

	/** What a ring gauge shows. */
	public enum Gauge {
		FPS, MEMORY, PING, FEATURES, OFF
	}

	public enum Outline {
		OFF, THIN, GLOW
	}

	public enum FontChoice {
		INTER, VANILLA
	}

	public enum Motion {
		OFF(0.0), NORMAL(1.0), FAST(0.55);

		/** Multiplier applied to every animation duration. */
		public final double scale;

		Motion(double scale) {
			this.scale = scale;
		}
	}

	/** Clamps values that may have been hand-edited in the config file. */
	public void sanitize() {
		surfaceOpacity = Math.max(0f, Math.min(1f, surfaceOpacity));
		blurStrength = Math.max(1, Math.min(5, blurStrength));
		cornerRadius = Math.max(0f, Math.min(16f, cornerRadius));
		if (outline == null) {
			outline = Outline.OFF;
		}
		if (font == null) {
			font = FontChoice.INTER;
		}
		if (motion == null) {
			motion = Motion.NORMAL;
		}
		if (layout == null) {
			layout = Layout.CLASSIC;
		}
		if (ring == null) {
			ring = new Ring();
		}
		ring.sanitize();
		if (accent == null) {
			accent = "#4C8DFF";
		}
		if (surface == null) {
			surface = "#16181E";
		}
	}
}
