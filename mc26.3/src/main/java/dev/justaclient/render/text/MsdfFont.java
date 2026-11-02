package dev.justaclient.render.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * An MSDF font atlas in BMFont JSON layout (as written by msdf-bmfont-xml). Metrics are in atlas pixels at
 * {@link #emSize}; callers scale by {@code size / emSize}.
 */
public final class MsdfFont {
	public final Identifier texture;
	public final float emSize;
	public final float lineHeight;
	public final float base;
	public final float atlasWidth;
	public final float atlasHeight;
	private final Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
	private final Long2FloatOpenHashMap kerning = new Long2FloatOpenHashMap();
	private final Glyph fallback;

	public record Glyph(float x, float y, float width, float height, float xOffset, float yOffset, float advance) {
	}

	private MsdfFont(Identifier texture, JsonObject json) {
		this.texture = texture;
		JsonObject info = json.getAsJsonObject("info");
		JsonObject common = json.getAsJsonObject("common");
		this.emSize = info.get("size").getAsFloat();
		this.lineHeight = common.get("lineHeight").getAsFloat();
		this.base = common.get("base").getAsFloat();
		this.atlasWidth = common.get("scaleW").getAsFloat();
		this.atlasHeight = common.get("scaleH").getAsFloat();
		for (JsonElement element : json.getAsJsonArray("chars")) {
			JsonObject c = element.getAsJsonObject();
			glyphs.put(c.get("id").getAsInt(), new Glyph(
				c.get("x").getAsFloat(), c.get("y").getAsFloat(),
				c.get("width").getAsFloat(), c.get("height").getAsFloat(),
				c.get("xoffset").getAsFloat(), c.get("yoffset").getAsFloat(),
				c.get("xadvance").getAsFloat()
			));
		}
		JsonArray kernings = json.getAsJsonArray("kernings");
		if (kernings != null) {
			for (JsonElement element : kernings) {
				JsonObject k = element.getAsJsonObject();
				kerning.put(pair(k.get("first").getAsInt(), k.get("second").getAsInt()), k.get("amount").getAsFloat());
			}
		}
		Glyph question = glyphs.get('?');
		this.fallback = question != null ? question : new Glyph(0, 0, 0, 0, 0, 0, emSize / 2f);
	}

	/** Loads {@code assets/<ns>/msdf/<name>.json} with its atlas at {@code textures/font/<name>.png}. */
	public static MsdfFont load(Identifier id) {
		Identifier json = id.withPath(p -> "msdf/" + p + ".json");
		Identifier texture = id.withPath(p -> "textures/font/" + p + ".png");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(json)) {
			return new MsdfFont(texture, JsonParser.parseReader(reader).getAsJsonObject());
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("Could not load MSDF font " + json, e);
		}
	}

	public Glyph glyph(int codepoint) {
		Glyph glyph = glyphs.get(codepoint);
		return glyph != null ? glyph : fallback;
	}

	public boolean has(int codepoint) {
		return glyphs.containsKey(codepoint);
	}

	public float kerning(int first, int second) {
		return kerning.getOrDefault(pair(first, second), 0f);
	}

	/** Width of {@code text} in atlas pixels. */
	public float width(String text) {
		float width = 0f;
		int previous = -1;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (previous >= 0) {
				width += kerning(previous, cp);
			}
			width += glyph(cp).advance();
			previous = cp;
		}
		return width;
	}

	private static long pair(int first, int second) {
		return ((long) first << 32) | (second & 0xFFFFFFFFL);
	}
}
