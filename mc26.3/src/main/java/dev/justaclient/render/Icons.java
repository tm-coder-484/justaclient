package dev.justaclient.render;

/**
 * Lucide icon glyphs in the {@code lucide} atlas, drawn with {@link dev.justaclient.render.text.JacText#icon}. The atlas
 * only holds the icons listed in {@code tools/fonts/icons.txt}; add a name there and regenerate to use a new one.
 */
public enum Icons {
	LAYOUT_DASHBOARD(0xe1c1),
	CROSSHAIR(0xe0b0),
	HAND(0xe1d7),
	MOUNTAIN(0xe231),
	VIDEO(0xe1a5),
	WRENCH(0xe1b1),
	PALETTE(0xe1dd),
	SEARCH(0xe155),
	CHEVRON_DOWN(0xe071),
	CHEVRON_RIGHT(0xe073),
	MOVE(0xe125),
	PLUS(0xe141),
	TRASH(0xe18e),
	CHECK(0xe070),
	X(0xe1b2),
	KEYBOARD(0xe284),
	SPARKLES(0xe417),
	GAUGE(0xe1bf),
	MAP_PIN(0xe115),
	SHIELD(0xe15c),
	FLASK(0xe0da),
	TIMER(0xe1e0),
	FLAME(0xe0d7),
	ZOOM_IN(0xe1b6),
	SQUARE_DASHED(0xe1cb),
	BELL(0xe05d),
	COMPASS(0xe09f),
	LAYERS(0xe104),
	MOUSE_CLICK(0xe124),
	EYE(0xe0be),
	SLIDERS(0xe29a),
	USER(0xe19f),
	COPY(0xe0a2),
	PENCIL(0xe1f9),
	ROTATE_CCW(0xe14c),
	SUN(0xe17c),
	INFO(0xe0fe),
	MAGNET(0xe2b5),
	MAXIMIZE(0xe117),
	HEART(0xe0f5),
	SWORD(0xe2b3),
	USERS(0xe1a4),
	GLOBE(0xe0eb),
	LOG_OUT(0xe112),
	SETTINGS(0xe158),
	ACTIVITY(0xe038),
	CLOUD_FOG(0xe214),
	MOON(0xe122),
	STAR(0xe17a),
	ZAP(0xe1b4),
	DOWNLOAD(0xe0b6),
	UPLOAD(0xe19e),
	CLIPBOARD_PASTE(0xe3ec),
	FOOTPRINTS(0xe3bd),
	CIRCLE_DOT(0xe349),
	SKULL(0xe221),
	TARGET(0xe184),
	SWORDS(0xe2b4),
	ORBIT(0xe3eb),
	CPU(0xe0ad),
	NAVIGATION(0xe128);

	public final String glyph;

	Icons(int codepoint) {
		this.glyph = Character.toString(codepoint);
	}
}
