package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * A hat on your head (and friends', if you like), seen only on your screen. Simple shaded shapes in your colour rather
 * than fixed artwork: a crown, a top hat, a party cone or a beanie.
 */
public final class HatsFeature extends Feature {
	public enum Hat implements ChoiceSetting.Named {
		CROWN("Crown"), TOP_HAT("Top hat"), CONE("Party cone"), BEANIE("Beanie");

		private final String label;

		Hat(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final Vector3f LIGHT = new Vector3f(0.4f, 1f, 0.3f).normalize();
	private final ChoiceSetting<Hat> hat = choice("hat", "Hat", Hat.CROWN);
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final ColorSetting trim = color("trim", "Trim colour", "#FFFFFF");
	private final NumberSetting size = number("size", "Size", 1.0, 0.6, 1.4, 0.05, NumberSetting.percent());
	private final BoolSetting friends = bool("friends", "Show on friends", false);

	/** One face of a hat in hat space: blocks, y up from the top of the head, +z forwards. */
	private record Face(Vector3f a, Vector3f b, Vector3f c, Vector3f d, boolean trimmed) {
	}

	public HatsFeature() {
		super("hats", "Hats", "A hat only you can see: crown, top hat, cone or beanie", Category.WORLD, Icons.CROWN, false);
	}

	public static void init() {
		HatsFeature f = Features.get(HatsFeature.class);
		WorldFx.solidLayer(f, f::render);
	}

	private void render(WorldFx.Frame frame) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		List<Face> faces = build(hat.get(), size.f());
		int main = color.argb();
		int edge = trim.argb();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self ? !mc.gameRenderer.mainCamera().isDetached() : !friends.on() || !FriendsFeature.get().isFriend(player.getGameProfile().name())) {
				continue;
			}
			if (player.isInvisible()) {
				continue;
			}
			float pt = frame.partialTick;
			Vec3 pos = player.getPosition(pt);
			float yaw = Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot);
			float pitch = Mth.lerp(pt, player.xRotO, player.getXRot());
			// The neck pivot of the player model; the head is 8 model pixels (0.47 blocks) above it.
			double neck = pos.y + player.getBbHeight() - (player.isCrouching() ? 0.2 : 0.39);
			Matrix3f rot = new Matrix3f().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch));
			Vector3f top = rot.transform(new Vector3f(0f, 0.47f, 0f));
			Vec3 origin = new Vec3(pos.x + top.x, neck + top.y, pos.z + top.z);
			for (Face f : faces) {
				Vector3f a = rot.transform(new Vector3f(f.a));
				Vector3f b = rot.transform(new Vector3f(f.b));
				Vector3f c = rot.transform(new Vector3f(f.c));
				Vector3f d = rot.transform(new Vector3f(f.d));
				Vector3f normal = new Vector3f(c).sub(a).cross(new Vector3f(b).sub(a)).normalize();
				float light = 0.55f + 0.45f * Math.abs(normal.dot(LIGHT));
				int base = f.trimmed ? edge : main;
				int shaded = Colors.lerp(0xFF000000, base | 0xFF000000, light);
				frame.solidQuad(at(origin, a), at(origin, b), at(origin, c), at(origin, d), shaded);
			}
		}
	}

	private static Vec3 at(Vec3 origin, Vector3f v) {
		return new Vec3(origin.x + v.x, origin.y + v.y, origin.z + v.z);
	}

	private static List<Face> build(Hat hat, float s) {
		List<Face> out = new ArrayList<>();
		switch (hat) {
			case CROWN -> {
				// A band with five points.
				ring(out, 0.27f * s, 0.27f * s, -0.02f * s, 0.1f * s, 20, false);
				int points = 5;
				for (int i = 0; i < points; i++) {
					float a0 = (float) (i * Math.PI * 2 / points);
					float a1 = (float) ((i + 1) * Math.PI * 2 / points);
					float am = (a0 + a1) / 2f;
					Vector3f l = polar(0.27f * s, a0, 0.1f * s);
					Vector3f r = polar(0.27f * s, a1, 0.1f * s);
					Vector3f tip = polar(0.25f * s, am, 0.26f * s);
					out.add(new Face(l, r, tip, tip, false));
					// A jewel near each tip.
					Vector3f j = polar(0.272f * s, am, 0.15f * s);
					float js = 0.03f * s;
					out.add(new Face(new Vector3f(j).add(0, js, 0), polar(0.272f * s, am + 0.12f, 0.15f * s), new Vector3f(j).add(0, -js, 0),
						polar(0.272f * s, am - 0.12f, 0.15f * s), true));
				}
			}
			case TOP_HAT -> {
				disc(out, 0.4f * s, 0.0f, 24, false);
				ring(out, 0.4f * s, 0.4f * s, -0.02f * s, 0.0f, 24, false);
				ring(out, 0.24f * s, 0.24f * s, 0.0f, 0.06f * s, 24, true);
				ring(out, 0.24f * s, 0.25f * s, 0.06f * s, 0.42f * s, 24, false);
				disc(out, 0.25f * s, 0.42f * s, 24, false);
			}
			case CONE -> {
				ring(out, 0.24f * s, 0.0f, 0.0f, 0.5f * s, 24, false);
				disc(out, 0.24f * s, 0.0f, 24, true);
				// A bobble on top.
				sphere(out, new Vector3f(0, 0.52f * s, 0), 0.06f * s, true);
			}
			case BEANIE -> {
				ring(out, 0.27f * s, 0.27f * s, -0.12f * s, -0.02f * s, 24, true);
				dome(out, 0.27f * s, -0.02f * s, 0.16f * s, 24, false);
				sphere(out, new Vector3f(0, 0.16f * s, 0), 0.07f * s, true);
			}
		}
		return out;
	}

	private static Vector3f polar(float r, float angle, float y) {
		return new Vector3f((float) Math.sin(angle) * r, y, (float) Math.cos(angle) * r);
	}

	/** The side of a (possibly tapered) cylinder from y0 (radius r0) to y1 (radius r1). */
	private static void ring(List<Face> out, float r0, float r1, float y0, float y1, int n, boolean trim) {
		for (int i = 0; i < n; i++) {
			float a0 = (float) (i * Math.PI * 2 / n);
			float a1 = (float) ((i + 1) * Math.PI * 2 / n);
			out.add(new Face(polar(r0, a0, y0), polar(r0, a1, y0), polar(r1, a1, y1), polar(r1, a0, y1), trim));
		}
	}

	private static void disc(List<Face> out, float r, float y, int n, boolean trim) {
		Vector3f centre = new Vector3f(0, y, 0);
		for (int i = 0; i < n; i++) {
			float a0 = (float) (i * Math.PI * 2 / n);
			float a1 = (float) ((i + 1) * Math.PI * 2 / n);
			out.add(new Face(centre, polar(r, a0, y), polar(r, a1, y), polar(r, a1, y), trim));
		}
	}

	private static void dome(List<Face> out, float r, float y, float h, int n, boolean trim) {
		int rings = 5;
		for (int k = 0; k < rings; k++) {
			float t0 = (float) (k * Math.PI / 2 / rings);
			float t1 = (float) ((k + 1) * Math.PI / 2 / rings);
			ring(out, r * (float) Math.cos(t0), r * (float) Math.cos(t1), y + h * (float) Math.sin(t0), y + h * (float) Math.sin(t1), n, trim);
		}
	}

	private static void sphere(List<Face> out, Vector3f c, float r, boolean trim) {
		int n = 10;
		int rings = 6;
		for (int k = 0; k < rings; k++) {
			float t0 = (float) (-Math.PI / 2 + k * Math.PI / rings);
			float t1 = (float) (-Math.PI / 2 + (k + 1) * Math.PI / rings);
			float r0 = r * (float) Math.cos(t0);
			float r1 = r * (float) Math.cos(t1);
			float y0 = c.y + r * (float) Math.sin(t0);
			float y1 = c.y + r * (float) Math.sin(t1);
			for (int i = 0; i < n; i++) {
				float a0 = (float) (i * Math.PI * 2 / n);
				float a1 = (float) ((i + 1) * Math.PI * 2 / n);
				out.add(new Face(polar(r0, a0, y0).add(c.x, 0, c.z), polar(r0, a1, y0).add(c.x, 0, c.z), polar(r1, a1, y1).add(c.x, 0, c.z),
					polar(r1, a0, y1).add(c.x, 0, c.z), trim));
			}
		}
	}
}
