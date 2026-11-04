package dev.justaclient.common.cosmetics;

/**
 * Turns a picture into a cape texture. A file that already is a cape texture (twice as wide as it is tall, like 64 x 32,
 * or OptiFine's 46 x 22) is used as it is; any other picture is fitted onto the back of the cape and onto the elytra
 * wings, with its edges carried round the sides and a darker mirror on the inside.
 */
public final class CapeLayout {
	/** A finished texture: ARGB pixels, {@code width} x {@code height}, and whether it was a picture fitted on. */
	public record Texture(int width, int height, int[] argb, boolean fitted) {
		public int at(int x, int y) {
			return argb[y * width + x];
		}
	}

	/** The largest scale for fitted pictures: 16 makes a 1024 x 512 texture. */
	public static final int MAX_SCALE = 16;

	private CapeLayout() {
	}

	public static Texture build(int[] src, int w, int h) {
		if (w == h * 2 && w >= 64) {
			return new Texture(w, h, src.clone(), false);
		}
		if (w % 46 == 0 && h % 22 == 0 && w / 46 == h / 22) {
			// OptiFine capes: the same layout as the top-left of a 64 x 32 texture.
			int s = w / 46;
			int tw = 64 * s;
			int th = 32 * s;
			int[] out = new int[tw * th];
			for (int y = 0; y < h; y++) {
				System.arraycopy(src, y * w, out, y * tw, w);
			}
			return new Texture(tw, th, out, false);
		}
		return fit(src, w, h);
	}

	private static Texture fit(int[] src, int w, int h) {
		int s = Math.max(1, Math.min(MAX_SCALE, Math.round(h / 16f)));
		int tw = 64 * s;
		int th = 32 * s;
		int[] out = new int[tw * th];
		// The cape: a 10 x 16 x 1 box at (0, 0). Faces: outside at (1, 1), inside at (12, 1), edges round them.
		int[] face = cover(src, w, h, 10 * s, 16 * s);
		box(out, tw, s, 0, 0, 10, 16, 1, face, 10 * s, 16 * s);
		// The elytra wing: a 10 x 20 x 2 box at (22, 0).
		int[] wing = cover(src, w, h, 10 * s, 20 * s);
		box(out, tw, s, 22, 0, 10, 20, 2, wing, 10 * s, 20 * s);
		return new Texture(tw, th, out, true);
	}

	/**
	 * Paints a model box whose texture starts at (u, v) and is {@code bw} x {@code bh} x {@code bd} in model pixels: the
	 * front face gets the picture, the back a darker mirror, and the sides, top and bottom the picture's edge pixels.
	 */
	private static void box(int[] out, int tw, int s, int u, int v, int bw, int bh, int bd, int[] pic, int pw, int ph) {
		int d = bd * s;
		int x0 = u * s;
		int y0 = v * s;
		int fw = bw * s;
		int fh = bh * s;
		for (int y = 0; y < fh; y++) {
			for (int x = 0; x < fw; x++) {
				int c = pic[y * pw + x];
				put(out, tw, x0 + d + x, y0 + d + y, c);
				// Inside: mirrored, so the picture lines up through the cloth, and darker.
				put(out, tw, x0 + d + fw + d + (fw - 1 - x), y0 + d + y, darker(c));
			}
			for (int k = 0; k < d; k++) {
				put(out, tw, x0 + k, y0 + d + y, pic[y * pw]);
				put(out, tw, x0 + d + fw + k, y0 + d + y, pic[y * pw + pw - 1]);
			}
		}
		for (int k = 0; k < d; k++) {
			for (int x = 0; x < fw; x++) {
				put(out, tw, x0 + d + x, y0 + k, pic[x]);
				put(out, tw, x0 + d + fw + x, y0 + k, pic[(ph - 1) * pw + x]);
			}
		}
	}

	private static void put(int[] out, int tw, int x, int y, int c) {
		int i = y * tw + x;
		if (x >= 0 && x < tw && i >= 0 && i < out.length) {
			out[i] = c;
		}
	}

	static int darker(int c) {
		int a = c >>> 24;
		int r = (c >> 16 & 0xFF) * 65 / 100;
		int g = (c >> 8 & 0xFF) * 65 / 100;
		int b = (c & 0xFF) * 65 / 100;
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** The picture scaled to cover {@code tw} x {@code th}, cropped evenly, sampled at pixel centres. Fully opaque. */
	static int[] cover(int[] src, int w, int h, int tw, int th) {
		float scale = Math.max(tw / (float) w, th / (float) h);
		float ox = (w - tw / scale) / 2f;
		float oy = (h - th / scale) / 2f;
		int[] out = new int[tw * th];
		for (int y = 0; y < th; y++) {
			int sy = Math.min(h - 1, Math.max(0, (int) (oy + (y + 0.5f) / scale)));
			for (int x = 0; x < tw; x++) {
				int sx = Math.min(w - 1, Math.max(0, (int) (ox + (x + 0.5f) / scale)));
				out[y * tw + x] = src[sy * w + sx] | 0xFF000000;
			}
		}
		return out;
	}
}
