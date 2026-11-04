package dev.justaclient.common.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModIconsTest {
	// 20x10 images, left half opaque red, right half half-transparent blue (made with Pillow).
	private static final String LOSSLESS = "UklGRigAAABXRUJQVlA4TBsAAAAvE0ACEA8Q+z//D/yPCgIBwoH/VwMOIvof5AIA";
	private static final String LOSSY_ALPHA = "UklGRqAAAABXRUJQVlA4WAoAAAAQAAAAEwAACQAAQUxQSBQAAAABD/DA/4iIIBAgHPh/NeAgov9BLlZQOCBmAAAAsAQAnQEqFAAKAD4xFIhCoiEhGAQAIAMEoAnTKCtAfgBWgH8AygD9AALVrL0tAAD++urvkXlbX9Ip+/+xm//0NYU4Leb//Rm/+hrCnBbzf/8GY692msZA//8GYhq74T2uaviBAgAA";
	private static final String LOSSY = "UklGRnIAAABXRUJQVlA4IGYAAACwBACdASoUAAoAPjEUiEKiISEYBAAgAwSgCdMoK0B+AFaAfwDKAP0AAtWsvS0AAP766u+ReVtf0in7/7Gb//Q1hTgt5v/9Gb/6GsKcFvN//wZjr3aaxkD//wZiGrvhPa5q+IECAAA=";

	private static byte[] b64(String s) {
		return Base64.getDecoder().decode(s);
	}

	@Test
	void decodesLosslessWebP() {
		BufferedImage img = ModIcons.decode(b64(LOSSLESS));
		assertNotNull(img);
		assertEquals(20, img.getWidth());
		assertEquals(10, img.getHeight());
		assertEquals(0xFFFF0000, img.getRGB(2, 5));
		assertEquals(128, img.getRGB(17, 5) >>> 24, 2);
		assertEquals(0x0000FF, img.getRGB(17, 5) & 0xFFFFFF);
	}

	@Test
	void decodesLossyWebPWithAndWithoutAlpha() {
		BufferedImage alpha = ModIcons.decode(b64(LOSSY_ALPHA));
		assertNotNull(alpha);
		assertEquals(20, alpha.getWidth());
		int left = alpha.getRGB(2, 5);
		assertEquals(255, left >>> 24);
		assertTrue((left >> 16 & 0xFF) > 200 && (left & 0xFF) < 60, Integer.toHexString(left));
		int right = alpha.getRGB(17, 5);
		assertTrue(Math.abs((right >>> 24) - 128) < 8, Integer.toHexString(right));
		BufferedImage plain = ModIcons.decode(b64(LOSSY));
		assertNotNull(plain);
		assertEquals(255, plain.getRGB(17, 5) >>> 24);
	}

	@Test
	void decodesPngAndRejectsJunk() throws Exception {
		BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		src.setRGB(1, 1, 0xFF00FF00);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(src, "png", out);
		BufferedImage png = ModIcons.decode(out.toByteArray());
		assertNotNull(png);
		assertEquals(0xFF00FF00, png.getRGB(1, 1));
		assertNull(ModIcons.decode("<html>not an image</html>".getBytes()));
		assertNull(ModIcons.decode("RIFF\0\0\0\0WEBPjunk".getBytes()));
	}

	@Test
	void squareFitsCentresAndRoundsCorners() {
		BufferedImage wide = ModIcons.decode(b64(LOSSLESS));
		int[] px = ModIcons.square(wide, 96, 0.22f);
		assertEquals(96 * 96, px.length);
		// 20x10 fitted into 96: 96x48 in the middle, transparent above and below.
		assertEquals(0, px[5 * 96 + 48] >>> 24);
		assertEquals(255, px[48 * 96 + 20] >>> 24);
		// Corners cut away, edges and centre kept.
		BufferedImage full = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 96; y++) {
			for (int x = 0; x < 96; x++) {
				full.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		int[] r = ModIcons.square(full, 96, 0.22f);
		assertEquals(0, r[0] >>> 24);
		assertEquals(0, r[95] >>> 24);
		assertEquals(0, r[95 * 96 + 95] >>> 24);
		assertEquals(255, r[48 * 96 + 48] >>> 24);
		assertEquals(255, r[0 * 96 + 48] >>> 24);
		// The curve is smooth: somewhere along the diagonal there is a partly covered pixel.
		boolean partial = false;
		for (int i = 0; i < 20; i++) {
			int alpha = r[i * 96 + i] >>> 24;
			partial |= alpha > 0 && alpha < 255;
		}
		assertTrue(partial);
	}

	@Test
	void pixelArtStaysSharp() {
		BufferedImage tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		tiny.setRGB(0, 0, 0xFFFF0000);
		tiny.setRGB(1, 0, 0xFF0000FF);
		tiny.setRGB(0, 1, 0xFF0000FF);
		tiny.setRGB(1, 1, 0xFFFF0000);
		int[] px = ModIcons.square(tiny, 96, 0f);
		// Right next to the middle line the colours are still pure, not blended.
		assertEquals(0xFFFF0000, px[30 * 96 + 47]);
		assertEquals(0xFF0000FF, px[30 * 96 + 48]);
	}

	@Test
	void onlyModrinthHostsOverHttps() {
		assertTrue(ModIcons.allowed("https://cdn.modrinth.com/data/AANobbMI/abc_96.webp"));
		assertFalse(ModIcons.allowed("http://cdn.modrinth.com/data/x.png"));
		assertFalse(ModIcons.allowed("https://evil.example/cdn.modrinth.com.png"));
		assertFalse(ModIcons.allowed("https://cdn.modrinth.com.evil.example/x.png"));
		assertFalse(ModIcons.allowed(""));
		assertFalse(ModIcons.allowed("not a url at all"));
	}

	@Test
	void cacheFileNamesAreSafeAndDistinct(@TempDir Path dir) {
		ModIcons icons = new ModIcons("test", dir);
		Path a = icons.cacheFile("https://cdn.modrinth.com/data/a/../../x.webp");
		Path b = icons.cacheFile("https://cdn.modrinth.com/data/b/icon.png");
		assertEquals(dir, a.getParent());
		assertEquals(dir, b.getParent());
		assertNotEquals(a, b);
		assertTrue(a.getFileName().toString().matches("[0-9a-f]{40}\\.img"));
	}

	@Test
	void refusesOtherHostsWithoutFetching(@TempDir Path dir) {
		ModIcons icons = new ModIcons("test", dir);
		assertThrows(() -> icons.load("https://example.com/icon.png").join());
	}

	@Test
	void searchHitsCarryTheirIcon() {
		Modrinth.Page page = Modrinth.parseSearch("""
			{"hits":[{"project_id":"AANobbMI","slug":"sodium","title":"Sodium","icon_url":"https://cdn.modrinth.com/data/AANobbMI/i_96.webp"},
			{"project_id":"x","slug":"y","title":"No icon","icon_url":null}],"offset":0,"total_hits":2}""");
		assertEquals("https://cdn.modrinth.com/data/AANobbMI/i_96.webp", page.hits().get(0).iconUrl());
		assertEquals("", page.hits().get(1).iconUrl());
		assertEquals("", new Modrinth.Hit("i", "s", "t", "", "", 0, 0, java.util.List.of(), "mod").iconUrl());
	}

	private static void assertThrows(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			return;
		}
		throw new AssertionError("expected a failure");
	}
}
