package dev.justaclient.hud.widgets;

import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.render.Icons;
import java.util.List;

/** Which keys do what: every bound toggle key and feature key, e.g. "Zoom  C". */
public final class KeybindsHud extends RowsHud {
	public KeybindsHud() {
		super("keybinds", "Hotkeys", "Lists the keys bound to features", Icons.KEYBOARD, false, 0f, 0.5f);
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		if (preview) {
			rows.add(new Row("Zoom", "C"));
			rows.add(new Row("Inspect", "I"));
			rows.add(new Row("Trails", "T"));
			return;
		}
		for (Feature f : Features.all()) {
			if (f.toggleKey.isBound()) {
				rows.add(new Row(f.name, f.toggleKey.keyName()));
			}
			if (!f.isEnabled()) {
				continue;
			}
			for (Setting<?> s : f.settings()) {
				if (s instanceof KeySetting key && key.isBound()) {
					rows.add(new Row(label(f, key), key.keyName()));
				}
			}
		}
	}

	private static String label(Feature f, KeySetting key) {
		String name = key.name.endsWith(" key") ? key.name.substring(0, key.name.length() - 4) : key.name;
		return name.equalsIgnoreCase(f.name) ? f.name : name;
	}
}
