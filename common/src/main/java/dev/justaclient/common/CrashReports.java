package dev.justaclient.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a Minecraft crash report (or a JVM hs_err log) and says in plain words what probably went wrong. */
public final class CrashReports {
	private static final Pattern MIXIN_HANDLER = Pattern.compile("\\$([a-z][a-z0-9_-]{1,63})(?=\\$)");
	private static final Pattern EXCEPTION = Pattern.compile("^([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+(?:Exception|Error|Throwable))(?::\\s*(.*))?$");

	public record Info(String description, String exception, String detail, String explanation, String advice, List<String> suspects,
		boolean nativeCrash) {
	}

	private CrashReports() {
	}

	/**
	 * @param text the report
	 * @param modIds ids of the installed mods, used to spot which ones show up in the stack trace
	 * @param ignore ids never named as suspects (Minecraft, Java, the loader)
	 */
	public static Info parse(String text, Set<String> modIds, Set<String> ignore) {
		boolean nativeCrash = text.startsWith("#") && text.contains("A fatal error has been detected by the Java Runtime Environment");
		String description = "";
		String exception = "";
		String detail = "";
		Set<String> suspects = new LinkedHashSet<>();
		String[] lines = text.split("\\R");
		for (String raw : lines) {
			String line = raw.strip();
			if (description.isEmpty() && line.startsWith("Description:")) {
				description = line.substring("Description:".length()).strip();
			}
			if (exception.isEmpty()) {
				Matcher m = EXCEPTION.matcher(line);
				if (m.matches()) {
					exception = m.group(1);
					detail = m.group(2) == null ? "" : m.group(2).strip();
				}
			}
			if (line.startsWith("at ") || line.contains("Mixin") || line.startsWith("Caused by")) {
				Matcher m = MIXIN_HANDLER.matcher(line);
				while (m.find()) {
					String id = m.group(1);
					if (modIds.contains(id) && !ignore.contains(id)) {
						suspects.add(id);
					}
				}
			}
			// Mixin errors name the config file, which is usually <modid>.mixins.json.
			int mixins = line.indexOf(".mixins.json");
			if (mixins > 0) {
				int start = mixins;
				while (start > 0 && (Character.isLetterOrDigit(line.charAt(start - 1)) || line.charAt(start - 1) == '_' || line.charAt(start - 1) == '-')) {
					start--;
				}
				String id = line.substring(start, mixins);
				if (modIds.contains(id) && !ignore.contains(id)) {
					suspects.add(id);
				}
			}
		}
		if (nativeCrash) {
			for (String raw : lines) {
				if (raw.startsWith("# Problematic frame:") || raw.startsWith("# C  [") || raw.startsWith("# J ")) {
					detail = raw.substring(1).strip();
				}
			}
		}
		String[] words = explain(nativeCrash, description, exception, detail, text);
		List<String> list = new ArrayList<>(suspects);
		return new Info(description, exception, detail, words[0], words[1], list.size() > 5 ? list.subList(0, 5) : list, nativeCrash);
	}

	private static String[] explain(boolean nativeCrash, String description, String exception, String detail, String text) {
		String ex = exception.toLowerCase(Locale.ROOT);
		String d = (detail + " " + description).toLowerCase(Locale.ROOT);
		if (description.contains("Manually triggered debug crash")) {
			return new String[] {"You held F3 + C, which crashes the game on purpose.", "Nothing to fix."};
		}
		if (ex.contains("outofmemoryerror") || text.contains("java.lang.OutOfMemoryError")) {
			return new String[] {"Minecraft ran out of memory.",
				"Give it more in your launcher (for example -Xmx4G), lower render distance, or remove a heavy resource or shader pack."};
		}
		if (nativeCrash) {
			boolean gpu = d.contains("nvoglv") || d.contains("atio") || d.contains("ig") && d.contains(".dll") || d.contains("libgl") || d.contains("opengl")
				|| d.contains("vulkan") || d.contains("amdx") || d.contains("dri");
			return new String[] {gpu ? "The graphics driver crashed inside Java." : "Java itself crashed, outside any mod's code.",
				gpu ? "Update your graphics driver, and try without shaders." : "Update Java and your graphics driver; if it keeps happening, try with fewer mods."};
		}
		if (ex.contains("mixin") || text.contains("MixinApplyError") || text.contains("InvalidInjectionException") || text.contains("Mixin transformation")) {
			return new String[] {"Two mods (or a mod and this Minecraft version) tried to change the same piece of game code.",
				"Update the mods named below, or remove one of them."};
		}
		if (ex.contains("nosuchmethoderror") || ex.contains("nosuchfielderror") || ex.contains("noclassdeffounderror") || ex.contains("classnotfoundexception")
			|| ex.contains("abstractmethoderror") || ex.contains("incompatibleclasschangeerror")) {
			return new String[] {"A mod was built for a different version of Minecraft or of another mod.",
				"Check that every mod matches your Minecraft version, and update the ones named below."};
		}
		if (d.contains("opengl") || d.contains("glfw") || d.contains("rendering") || text.contains("GL error") || d.contains("shader")) {
			return new String[] {"Something went wrong while drawing the game.", "Update your graphics driver; try without shaders or resource packs."};
		}
		if (ex.contains("stackoverflowerror")) {
			return new String[] {"Some code kept calling itself until Java gave up.", "This is a bug in a mod: update or remove the ones named below."};
		}
		if (description.toLowerCase(Locale.ROOT).contains("ticking") || ex.contains("nullpointerexception") || ex.contains("concurrentmodification")
			|| ex.contains("indexoutofbounds") || ex.contains("illegalstate")) {
			return new String[] {"A mod (or the game) hit a bug while the world was running.",
				"Update the mods named below. If it happens in one world only, a broken block or entity there may be the cause."};
		}
		return new String[] {"The game hit an error it could not recover from.",
			"Update your mods; if it keeps happening, send the diagnostic zip with a bug report."};
	}
}
