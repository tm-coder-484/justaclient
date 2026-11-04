package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudLayouts;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;

/** How the HUD editor behaves: snapping, the gap it keeps, anchors, resize handles and where new widgets go. */
public final class HudEditorFeature extends Feature {
	private final BoolSetting snap = bool("snap", "Snap to edges and widgets", true);
	private final NumberSetting gap = number("gap", "Gap between widgets", 3, 0, 12, 1, NumberSetting.unit(" px"));
	private final BoolSetting centres = bool("centres", "Snap to centre lines", true);
	private final BoolSetting autoAnchor = bool("auto_anchor", "Anchor to the nearest corner when dropped", true);
	private final BoolSetting handles = bool("handles", "Resize from the corners", true);
	private final BoolSetting placeNew = bool("place_new", "Put new widgets in a free spot", true);
	private final KeySetting nextLayout = add(new KeySetting("next_layout", "Next layout key", KeySetting.NONE));
	private boolean wasDown;

	public HudEditorFeature() {
		super("hud_editor", "HUD editor", "Snapping with a gap, anchors, corner resizing, layouts and free-spot placement", Category.HUD, Icons.MOVE,
			true);
	}

	public boolean snaps() {
		return isEnabled() && snap.on();
	}

	public float gap() {
		return gap.f();
	}

	public boolean snapsToCentres() {
		return centres.on();
	}

	public boolean autoAnchors() {
		return isEnabled() && autoAnchor.on();
	}

	public boolean resizeHandles() {
		return isEnabled() && handles.on();
	}

	public boolean placesNew() {
		return isEnabled() && placeNew.on();
	}

	@Override
	public void tick() {
		boolean down = Minecraft.getInstance().screen == null && nextLayout.isDown();
		if (down && !wasDown) {
			HudLayouts.cycle();
		}
		wasDown = down;
	}
}
