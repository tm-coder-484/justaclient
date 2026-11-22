package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.render.Icons;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Redraws the vanilla widget sprites (buttons, sliders, text boxes, check boxes, scroll bars, list panels) in the client's
 * style on every screen that uses them, including other mods' screens such as Sodium's and Distant Horizons'. Screens can
 * be left alone one by one.
 */
public final class ScreenStyleFeature extends Feature {
	private final BoolSetting buttons = bool("buttons", "Buttons", true);
	private final BoolSetting sliders = bool("sliders", "Sliders", true);
	private final BoolSetting fields = bool("fields", "Text boxes", true);
	private final BoolSetting checkboxes = bool("checkboxes", "Check boxes", true);
	private final BoolSetting scrollbars = bool("scrollbars", "Scroll bars", true);
	private final BoolSetting tabs = bool("tabs", "Tabs", true);
	private final BoolSetting lists = bool("lists", "List panels and dividers", true);
	private final BoolSetting rounded = bool("rounded", "Rounded corners", true);
	private final ListSetting skip = add(new ListSetting("skip", "Leave these screens alone", List.of(), 100, 96, "Screen class name"));

	/** Screens opened this session, newest last, for the opt-out list in the Dev Console. */
	private static final Set<String> SEEN = new LinkedHashSet<>();
	private @Nullable Class<?> cachedClass;
	private boolean cachedStyled;
	private List<String> cachedSkip = List.of();

	public ScreenStyleFeature() {
		super("screen_style", "Styled screens", "Draws vanilla and modded screens' buttons, sliders and lists in the client's style",
			Category.SCREENS, Icons.MAXIMIZE, true);
	}

	public boolean buttons() {
		return buttons.on();
	}

	public boolean sliders() {
		return sliders.on();
	}

	public boolean fields() {
		return fields.on();
	}

	public boolean checkboxes() {
		return checkboxes.on();
	}

	public boolean scrollbars() {
		return scrollbars.on();
	}

	public boolean tabs() {
		return tabs.on();
	}

	public boolean lists() {
		return lists.on();
	}

	public boolean rounded() {
		return rounded.on();
	}

	/** True when the open screen should be drawn in the client's style. */
	public boolean styling() {
		if (!isEnabled()) {
			return false;
		}
		Screen screen = Minecraft.getInstance().screen;
		if (screen == null) {
			return false;
		}
		return styles(screen.getClass());
	}

	/** True if screens of this class are styled: the client's own screens draw themselves, and skipped screens stay vanilla. */
	public boolean styles(Class<?> type) {
		if (type == cachedClass && skip.get().equals(cachedSkip)) {
			return cachedStyled;
		}
		cachedClass = type;
		cachedSkip = skip.get();
		cachedStyled = !type.getName().startsWith("dev.justaclient.") && !skipped(type);
		return cachedStyled;
	}

	private boolean skipped(Class<?> type) {
		String simple = type.getSimpleName().toLowerCase(Locale.ROOT);
		String full = type.getName().toLowerCase(Locale.ROOT);
		for (String part : skip.get()) {
			String name = part.trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && (simple.equals(name) || full.equals(name) || (name.contains(".") && full.startsWith(name)))) {
				return true;
			}
		}
		return false;
	}

	public boolean isSkipped(String screenName) {
		for (String part : skip.get()) {
			if (part.trim().equalsIgnoreCase(screenName)) {
				return true;
			}
		}
		return false;
	}

	/** Adds or removes a screen (by simple class name) from the skip list. */
	public void setSkipped(String screenName, boolean skipped) {
		List<String> names = new ArrayList<>();
		for (String part : skip.get()) {
			String name = part.trim();
			if (!name.isEmpty() && !name.equalsIgnoreCase(screenName)) {
				names.add(name);
			}
		}
		if (skipped) {
			names.add(screenName);
		}
		skip.set(names);
		cachedClass = null;
	}

	/** Called whenever a screen opens. */
	public static void seen(@Nullable Screen screen) {
		if (screen == null || screen.getClass().getName().startsWith("dev.justaclient.")) {
			return;
		}
		String name = screen.getClass().getSimpleName();
		if (name.isEmpty()) {
			return;
		}
		SEEN.remove(name);
		SEEN.add(name);
		if (SEEN.size() > 40) {
			SEEN.remove(SEEN.iterator().next());
		}
	}

	public static List<String> seenScreens() {
		return new ArrayList<>(SEEN);
	}
}
