package dev.justaclient.anim;

/** ARGB colour helpers. Colours are packed 0xAARRGGBB ints, as Minecraft uses. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	/** Multiplies the colour's alpha by {@code factor} (0..1). */
	public static int fade(int argb, float factor) {
		return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24
			| Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8
			| Math.round(ab + (bb - ab) * t);
	}

	/** Parses "#RRGGBB" or "#AARRGGBB". Returns {@code fallback} if malformed. */
	public static int parse(String hex, int fallback) {
		if (hex == null) {
			return fallback;
		}
		String s = hex.startsWith("#") ? hex.substring(1) : hex;
		try {
			long v = Long.parseLong(s, 16);
			return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static String toHex(int argb) {
		return String.format("#%08X", argb);
	}

	/** Hue-rotating colour for the animated rainbow accent; {@code offset} shifts along the hue wheel (0..1). */
	public static int rainbow(float offset, float saturation, float brightness) {
		float hue = (System.currentTimeMillis() % 6000L) / 6000f + offset;
		return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
	}
}
