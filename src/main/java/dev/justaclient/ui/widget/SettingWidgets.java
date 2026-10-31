package dev.justaclient.ui.widget;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.Setting;

/** Builds the control for a setting. */
public final class SettingWidgets {
	private SettingWidgets() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Widget create(Setting<?> setting, float x, float y, float width) {
		return switch (setting) {
			case BoolSetting b -> new Toggle(x, y, width, b.name, b::get, b::set);
			case NumberSetting n -> new Slider(x, y, width, n.name, n.min, n.max, n.step, n::get, n::set, n.format);
			case ChoiceSetting c -> new Segmented(x, y, width, c.name, c.options, c.labels(), c::get, c::set);
			case ColorSetting c -> new ColorPicker(x, y, width, c);
			case KeySetting k -> new KeyBinder(x, y, width, k);
			default -> throw new IllegalArgumentException("No widget for " + setting.getClass());
		};
	}
}
