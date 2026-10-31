package dev.justaclient.feature;

import dev.justaclient.render.Icons;

public enum Category {
	HUD("HUD", Icons.LAYOUT_DASHBOARD),
	COMBAT("Combat feel", Icons.CROSSHAIR),
	FIRST_PERSON("First person", Icons.HAND),
	WORLD("World", Icons.MOUNTAIN),
	CAMERA("Camera", Icons.VIDEO),
	UTILITY("Utility", Icons.WRENCH);

	public final String label;
	public final Icons icon;

	Category(String label, Icons icon) {
		this.label = label;
		this.icon = icon;
	}
}
