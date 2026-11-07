package dev.justaclient.common.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.google.gson.stream.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;

/**
 * What a mod jar that is not loaded says about itself in {@code fabric.mod.json}, including the mods bundled inside it, so
 * the Mods panel can show switched-off mods and check dependencies. Adapted from Tom's server-switcher mod (CC0).
 */
public record JarMetadata(String id, String name, String version, String description, @Nullable String icon, Set<String> providedIds,
	Set<String> requiredIds) {
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final int MAX_NESTING = 3;

	public static @Nullable JarMetadata read(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("META-INF/neoforge.mods.toml");
            if(entry==null) entry=zip.getEntry("META-INF/mods.toml");
            boolean nativeMod=entry!=null;
            if(entry==null) entry=zip.getEntry(FABRIC_MOD_JSON);
			if (entry == null) {
				return null;
			}
			JsonObject json;
			try (InputStream in = zip.getInputStream(entry)) {
				json = nativeMod ? parseNative(in) : parse(in);
			}
			if(nativeMod) {
                var bundled=new com.google.gson.JsonArray();
                zip.stream().filter(e->e.getName().startsWith("META-INF/jarjar/") && e.getName().endsWith(".jar")).forEach(e->{
                    var nested=new JsonObject();nested.addProperty("file",e.getName());bundled.add(nested);
                });
                json.add("jars",bundled);
                if(string(json,"version", "?").contains("${file.jarVersion}")) {
                    var manifestEntry=zip.getEntry("META-INF/MANIFEST.MF");
                    if(manifestEntry!=null)try(var in=zip.getInputStream(manifestEntry)) {
                        var attributes=new java.util.jar.Manifest(in).getMainAttributes();
                        json.addProperty("version",java.util.Objects.requireNonNullElse(attributes.getValue("Implementation-Version"),"?"));
                    }
                }
            }
            Set<String> provided = new LinkedHashSet<>();
			Set<String> required = new LinkedHashSet<>();
			collectIds(json, provided, required);
			for (String nested : nestedJars(json)) {
				ZipEntry nestedEntry = zip.getEntry(nested);
				if (nestedEntry != null) {
					try (InputStream in = zip.getInputStream(nestedEntry)) {
						readNested(in.readAllBytes(), provided, required, 1);
					}
				}
			}
			required.removeAll(provided);
			String id = string(json, "id", jar.getFileName().toString());
			return new JarMetadata(id, string(json, "name", id), string(json, "version", "?"), string(json, "description", ""), icon(json),
				provided, required);
		} catch (RuntimeException | IOException e) {
			return null;
		}
	}

	private static void readNested(byte[] jarBytes, Set<String> provided, Set<String> required, int depth) throws IOException {
		Map<String, byte[]> jars = new HashMap<>();
		JsonObject json = null;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if(entry.getName().equals("META-INF/neoforge.mods.toml") || entry.getName().equals("META-INF/mods.toml")) {
                    json=parseNative(zip);
                } else if (entry.getName().equals(FABRIC_MOD_JSON) && json==null) {
					json = parse(zip);
				} else if (depth < MAX_NESTING && entry.getName().endsWith(".jar")) {
					jars.put(entry.getName(), zip.readAllBytes());
				}
			}
		}
		if (json == null) {
			return;
		}
		collectIds(json, provided, required);
		for (String nested : json.has("native") ? jars.keySet() : nestedJars(json)) {
			byte[] bytes = jars.get(nested);
			if (bytes != null) {
				readNested(bytes, provided, required, depth + 1);
			}
		}
	}

    private static JsonObject parseNative(InputStream in) {
        var config=new com.electronwill.nightconfig.toml.TomlParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8));
        java.util.List<? extends com.electronwill.nightconfig.core.UnmodifiableConfig> mods=config.getOrElse("mods",java.util.List.of());
        if(mods.isEmpty()) throw new IllegalArgumentException("No mods in metadata");
        var first=mods.getFirst();var json=new JsonObject();json.addProperty("native",true);
        String id=first.get("modId");json.addProperty("id",id);
        json.addProperty("name",first.<String>getOrElse("displayName",id));
        json.addProperty("version",first.<String>getOrElse("version","?"));
        json.addProperty("description",first.<String>getOrElse("description",""));
        first.<String>getOptional("logoFile").ifPresent(icon->json.addProperty("icon",icon));
        var provided=new com.google.gson.JsonArray();var depends=new JsonObject();
        for(var mod:mods) {
            String modId=mod.get("modId");provided.add(modId);
            java.util.List<? extends com.electronwill.nightconfig.core.UnmodifiableConfig> dependencies=config.getOrElse(java.util.List.of("dependencies",modId),java.util.List.of());
            for(var dependency:dependencies) {
                boolean required=dependency.<String>getOptional("type").map("required"::equals).orElseGet(()->dependency.getOrElse("mandatory",false));
                if(required)depends.addProperty(dependency.<String>get("modId"),dependency.<String>getOrElse("versionRange","*"));
            }
        }
        json.add("provides",provided);json.add("depends",depends);return json;
    }

	private static JsonObject parse(InputStream in) {
		// Not closed here: closing would close the zip stream it reads from.
		JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		reader.setLenient(true);
		return JsonParser.parseReader(reader).getAsJsonObject();
	}

	private static void collectIds(JsonObject json, Set<String> provided, Set<String> required) {
		if (json.has("id") && json.get("id").isJsonPrimitive()) {
			provided.add(json.get("id").getAsString());
		}
		if (json.has("provides") && json.get("provides").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("provides")) {
				if (element.isJsonPrimitive()) {
					provided.add(element.getAsString());
				}
			}
		}
		if (json.has("depends") && json.get("depends").isJsonObject()) {
			required.addAll(json.getAsJsonObject("depends").keySet());
		}
	}

	private static Set<String> nestedJars(JsonObject json) {
		Set<String> result = new LinkedHashSet<>();
		if (json.has("jars") && json.get("jars").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("jars")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("file")) {
					result.add(element.getAsJsonObject().get("file").getAsString());
				}
			}
		}
		return result;
	}

	/** The icon path inside the jar: a string, or an object of sizes from which the largest up to 128 is taken. */
	private static @Nullable String icon(JsonObject json) {
		JsonElement icon = json.get("icon");
		if (icon == null) {
			return null;
		}
		if (icon.isJsonPrimitive()) {
			return icon.getAsString();
		}
		if (!icon.isJsonObject()) {
			return null;
		}
		String best = null;
		int bestSize = -1;
		for (Map.Entry<String, JsonElement> e : icon.getAsJsonObject().entrySet()) {
			try {
				int size = Integer.parseInt(e.getKey());
				if (e.getValue().isJsonPrimitive() && (bestSize < 0 || (size <= 128 && size > bestSize) || (bestSize > 128 && size < bestSize))) {
					best = e.getValue().getAsString();
					bestSize = size;
				}
			} catch (NumberFormatException ignored) {
				// Not a size.
			}
		}
		return best;
	}

	private static String string(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}
}
