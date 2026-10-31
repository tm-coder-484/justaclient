package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Icons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shows another name in place of yours everywhere text is drawn on your screen: chat, tab list, name tags, scoreboard and
 * Justaclient's own HUD. Useful when streaming. Nothing is sent to the server; others still see your real name.
 */
public final class NameProtectFeature extends Feature {
	private final TextSetting alias = add(new TextSetting("alias", "Show as", "You", 16));

	public NameProtectFeature() {
		super("name_protect", "Name protect", "Show another name in place of yours on your screen", Category.UTILITY, Icons.USER, false);
	}

	/** Names to hide, longest first so a name that contains another is replaced whole. */
	private List<String> names() {
		List<String> names = new ArrayList<>(2);
		Minecraft mc = Minecraft.getInstance();
		String own = mc.getUser().getName();
		if (!own.isEmpty()) {
			names.add(own);
		}
		names.sort((a, b) -> b.length() - a.length());
		return names;
	}

	private String replacement() {
		String a = alias.get().trim();
		return a.isEmpty() ? "You" : a;
	}

	public String apply(String text) {
		if (!isEnabled() || text == null || text.isEmpty()) {
			return text;
		}
		String result = text;
		for (String name : names()) {
			result = replaceIgnoreCase(result, name, replacement());
		}
		return result;
	}

	public FormattedText apply(FormattedText text) {
		if (!isEnabled()) {
			return text;
		}
		return new FormattedText() {
			@Override
			public <T> Optional<T> visit(ContentConsumer<T> consumer) {
				return text.visit(s -> consumer.accept(NameProtectFeature.this.apply(s)));
			}

			@Override
			public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
				return text.visit((st, s) -> consumer.accept(st, NameProtectFeature.this.apply(s)), style);
			}
		};
	}

	/**
	 * Replaces names in a styled character stream, keeping each replacement in the style of the name's first letter.
	 * Returns the original sequence untouched when no name appears in it.
	 */
	public FormattedCharSequence apply(FormattedCharSequence sequence) {
		if (!isEnabled()) {
			return sequence;
		}
		List<Integer> codepoints = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		sequence.accept((index, style, codepoint) -> {
			codepoints.add(codepoint);
			styles.add(style);
			return true;
		});
		StringBuilder plain = new StringBuilder(codepoints.size());
		for (int cp : codepoints) {
			// One char per codepoint keeps indices aligned; names are plain ASCII so surrogates never match.
			plain.append(cp > 0xFFFF ? '�' : (char) cp);
		}
		String lower = plain.toString().toLowerCase(Locale.ROOT);
		List<String> names = names();
		boolean any = false;
		for (String name : names) {
			any |= lower.contains(name.toLowerCase(Locale.ROOT));
		}
		if (!any) {
			return sequence;
		}
		String replacement = replacement();
		List<Integer> outCp = new ArrayList<>(codepoints.size());
		List<Style> outStyle = new ArrayList<>(codepoints.size());
		int i = 0;
		while (i < codepoints.size()) {
			String hit = null;
			for (String name : names) {
				if (lower.startsWith(name.toLowerCase(Locale.ROOT), i)) {
					hit = name;
					break;
				}
			}
			if (hit != null) {
				Style style = styles.get(i);
				replacement.codePoints().forEach(cp -> {
					outCp.add(cp);
					outStyle.add(style);
				});
				i += hit.length();
			} else {
				outCp.add(codepoints.get(i));
				outStyle.add(styles.get(i));
				i++;
			}
		}
		return sink -> {
			for (int k = 0; k < outCp.size(); k++) {
				if (!sink.accept(k, outStyle.get(k), outCp.get(k))) {
					return false;
				}
			}
			return true;
		};
	}

	private static String replaceIgnoreCase(String text, String name, String with) {
		String lowerText = text.toLowerCase(Locale.ROOT);
		String lowerName = name.toLowerCase(Locale.ROOT);
		int at = lowerText.indexOf(lowerName);
		if (at < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		int from = 0;
		while (at >= 0) {
			out.append(text, from, at).append(with);
			from = at + name.length();
			at = lowerText.indexOf(lowerName, from);
		}
		return out.append(text, from, text.length()).toString();
	}
}
