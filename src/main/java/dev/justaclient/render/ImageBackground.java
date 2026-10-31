package dev.justaclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

/**
 * A player-supplied picture (PNG, JPG or animated GIF) drawn to cover an area, for the title screen and the menu header.
 * Decoding runs off the render thread; frames are scaled down so a big wallpaper does not eat texture memory.
 */
public final class ImageBackground {
	private static final int MAX_FRAMES = 240;

	private record Frame(int[] argb, int delayMs) {
	}

	private record Decoded(int width, int height, List<Frame> frames) {
	}

	private final String name;
	private @Nullable Path loadedPath;
	private long loadedStamp;
	private int loadedMaxSize;
	private @Nullable CompletableFuture<Decoded> pending;
	private final List<SmoothTexture> textures = new ArrayList<>();
	private final List<Identifier> ids = new ArrayList<>();
	private int[] delays = new int[0];
	private int totalMs;
	private int width;
	private int height;
	private @Nullable String error;

	public ImageBackground(String name) {
		this.name = name;
	}

	/**
	 * Points at {@code path}, reloading when it or the file changes. A null path clears the picture. {@code soft} keeps it
	 * at a small size so linear filtering blurs it, which is cheaper than a blur pass and keeps busy wallpapers readable.
	 */
	public void use(@Nullable Path path, int maxSize, boolean soft) {
		if (soft) {
			maxSize = -Math.max(64, maxSize / 4);
		}
		long stamp = 0;
		if (path != null) {
			try {
				stamp = Files.getLastModifiedTime(path).toMillis();
			} catch (IOException e) {
				path = null;
			}
		}
		if (java.util.Objects.equals(path, loadedPath) && stamp == loadedStamp && maxSize == loadedMaxSize) {
			return;
		}
		loadedPath = path;
		loadedStamp = stamp;
		loadedMaxSize = maxSize;
		error = null;
		release();
		if (path == null) {
			pending = null;
			return;
		}
		Path file = path;
		int max = Math.abs(maxSize);
		boolean blur = maxSize < 0;
		pending = CompletableFuture.supplyAsync(() -> {
			try {
				return decode(file, max, blur);
			} catch (IOException | RuntimeException e) {
				throw new IllegalStateException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
			}
		});
	}

	/** True once at least one frame is on the GPU. */
	public boolean ready() {
		poll();
		return !textures.isEmpty();
	}

	public @Nullable String error() {
		return error;
	}

	public boolean loading() {
		return pending != null;
	}

	/** Draws the current frame scaled to cover {@code w} x {@code h} at the origin, cropping the overflow evenly. */
	public void drawCover(GuiGraphics g, int w, int h, int color) {
		if (!ready()) {
			return;
		}
		int frame = 0;
		if (textures.size() > 1 && totalMs > 0) {
			long t = System.currentTimeMillis() % totalMs;
			for (int i = 0; i < delays.length; i++) {
				t -= delays[i];
				if (t < 0) {
					frame = i;
					break;
				}
			}
		}
		float scale = Math.max(w / (float) width, h / (float) height);
		float srcW = w / scale;
		float srcH = h / scale;
		float u = (width - srcW) / 2f;
		float v = (height - srcH) / 2f;
		g.blit(RenderPipelines.GUI_TEXTURED, ids.get(frame), 0, 0, u, v, w, h, Math.round(srcW), Math.round(srcH), width, height, color);
	}

	private void poll() {
		if (pending == null || !pending.isDone()) {
			return;
		}
		CompletableFuture<Decoded> done = pending;
		pending = null;
		Decoded decoded;
		try {
			decoded = done.join();
		} catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			error = cause.getMessage();
			Justaclient.LOGGER.warn("Could not read background {}", loadedPath, cause);
			return;
		}
		width = decoded.width();
		height = decoded.height();
		delays = new int[decoded.frames().size()];
		totalMs = 0;
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < decoded.frames().size(); i++) {
			Frame f = decoded.frames().get(i);
			SmoothTexture tex = new SmoothTexture(() -> "Justaclient background", width, height);
			NativeImage pixels = tex.getPixels();
			if (pixels != null) {
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						pixels.setPixel(x, y, f.argb()[y * width + x]);
					}
				}
				tex.upload();
			}
			Identifier id = Justaclient.id("background/" + name + "/" + i);
			mc.getTextureManager().register(id, tex);
			textures.add(tex);
			ids.add(id);
			delays[i] = f.delayMs();
			totalMs += f.delayMs();
		}
	}

	/** Frees the GPU copies, e.g. when the title screen closes. The file is decoded again next time it is used. */
	public void unload() {
		release();
		pending = null;
		loadedPath = null;
	}

	private void release() {
		Minecraft mc = Minecraft.getInstance();
		for (Identifier id : ids) {
			mc.getTextureManager().release(id);
		}
		textures.clear();
		ids.clear();
	}

	// Decoding

	private static Decoded decode(Path file, int maxSize, boolean blur) throws IOException {
		String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".gif")) {
			return decodeGif(file, maxSize, blur);
		}
		BufferedImage image = ImageIO.read(file.toFile());
		if (image == null) {
			throw new IOException("not a PNG, JPG or GIF");
		}
		int[] size = fit(image.getWidth(), image.getHeight(), maxSize);
		return new Decoded(size[0], size[1], List.of(new Frame(pixels(image, size[0], size[1], blur), 1000)));
	}

	/** Composites each GIF frame onto a canvas, honouring frame offsets and the disposal method, so partial frames look right. */
	private static Decoded decodeGif(Path file, int maxSize, boolean blur) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new IOException("no GIF reader");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, false);
				int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
				int cw = 0;
				int ch = 0;
				IIOMetadata stream = reader.getStreamMetadata();
				if (stream != null) {
					Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
					if (screen != null) {
						cw = intAttr(screen, "logicalScreenWidth", 0);
						ch = intAttr(screen, "logicalScreenHeight", 0);
					}
				}
				if (cw <= 0 || ch <= 0) {
					cw = reader.getWidth(0);
					ch = reader.getHeight(0);
				}
				int[] size = fit(cw, ch, maxSize);
				BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = canvas.createGraphics();
				List<Frame> frames = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					BufferedImage frame = reader.read(i);
					Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
					Node desc = child(tree, "ImageDescriptor");
					Node control = child(tree, "GraphicControlExtension");
					int fx = desc == null ? 0 : intAttr(desc, "imageLeftPosition", 0);
					int fy = desc == null ? 0 : intAttr(desc, "imageTopPosition", 0);
					int delay = control == null ? 10 : intAttr(control, "delayTime", 10);
					String disposal = control == null ? "none" : ((IIOMetadataNode) control).getAttribute("disposalMethod");
					BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
					g.drawImage(frame, fx, fy, null);
					// Browsers treat delays under 20 ms as 100 ms; do the same so old GIFs do not race.
					frames.add(new Frame(pixels(canvas, size[0], size[1], blur), delay < 2 ? 100 : delay * 10));
					if ("restoreToBackgroundColor".equals(disposal)) {
						g.setComposite(java.awt.AlphaComposite.Clear);
						g.fillRect(fx, fy, frame.getWidth(), frame.getHeight());
						g.setComposite(java.awt.AlphaComposite.SrcOver);
					} else if (before != null) {
						g.setComposite(java.awt.AlphaComposite.Src);
						g.drawImage(before, 0, 0, null);
						g.setComposite(java.awt.AlphaComposite.SrcOver);
					}
				}
				g.dispose();
				if (frames.isEmpty()) {
					throw new IOException("GIF has no frames");
				}
				return new Decoded(size[0], size[1], frames);
			} finally {
				reader.dispose();
			}
		}
	}

	private static int[] fit(int w, int h, int max) {
		float s = Math.min(1f, max / (float) Math.max(w, h));
		return new int[] {Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s))};
	}

	/** The image scaled to {@code w} x {@code h} as ARGB ints, optionally softened with three box blurs (close to a Gaussian). */
	private static int[] pixels(BufferedImage src, int w, int h, boolean blur) {
		BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		int[] argb = scaled.getRGB(0, 0, w, h, null, 0, w);
		if (blur) {
			int r = Math.max(2, Math.max(w, h) / 90);
			for (int i = 0; i < 3; i++) {
				argb = boxBlur(argb, w, h, r, true);
				argb = boxBlur(argb, w, h, r, false);
			}
		}
		return argb;
	}

	private static int[] boxBlur(int[] in, int w, int h, int r, boolean horizontal) {
		int[] out = new int[in.length];
		int len = horizontal ? w : h;
		int lines = horizontal ? h : w;
		int span = 2 * r + 1;
		for (int line = 0; line < lines; line++) {
			long a = 0;
			long rr = 0;
			long gg = 0;
			long bb = 0;
			for (int k = -r; k <= r; k++) {
				int c = in[index(Math.max(0, Math.min(len - 1, k)), line, w, horizontal)];
				a += c >>> 24;
				rr += c >> 16 & 0xFF;
				gg += c >> 8 & 0xFF;
				bb += c & 0xFF;
			}
			for (int i = 0; i < len; i++) {
				out[index(i, line, w, horizontal)] = (int) (a / span) << 24 | (int) (rr / span) << 16 | (int) (gg / span) << 8 | (int) (bb / span);
				int add = in[index(Math.min(len - 1, i + r + 1), line, w, horizontal)];
				int sub = in[index(Math.max(0, i - r), line, w, horizontal)];
				a += (add >>> 24) - (sub >>> 24);
				rr += (add >> 16 & 0xFF) - (sub >> 16 & 0xFF);
				gg += (add >> 8 & 0xFF) - (sub >> 8 & 0xFF);
				bb += (add & 0xFF) - (sub & 0xFF);
			}
		}
		return out;
	}

	private static int index(int i, int line, int w, boolean horizontal) {
		return horizontal ? line * w + i : i * w + line;
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	private static @Nullable Node child(Node node, String name) {
		for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c.getNodeName().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static int intAttr(Node node, String name, int fallback) {
		try {
			return Integer.parseInt(((IIOMetadataNode) node).getAttribute(name));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
