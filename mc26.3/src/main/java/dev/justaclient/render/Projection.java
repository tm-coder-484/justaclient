package dev.justaclient.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Remembers the last world camera so the HUD can place things over world positions, such as waypoint labels. Captured
 * at the start of each world frame.
 */
public final class Projection {
	private static final Matrix4f MODEL_VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean valid;

	private Projection() {
	}

	public static void capture(Vec3 cameraPos, Matrix4f modelView, Matrix4f projection) {
		camera = cameraPos;
		MODEL_VIEW.set(modelView);
		PROJECTION.set(projection);
		valid = true;
	}

	public static Vec3 camera() {
		return camera;
	}

	/** Where {@code world} lands on a GUI of the given size, or null when it is behind the camera. */
	public static @Nullable Vector2f toGui(Vec3 world, float guiWidth, float guiHeight) {
		if (!valid) {
			return null;
		}
		Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
		MODEL_VIEW.transform(v);
		PROJECTION.transform(v);
		if (v.w <= 0.05f) {
			return null;
		}
		return new Vector2f((v.x / v.w * 0.5f + 0.5f) * guiWidth, (0.5f - v.y / v.w * 0.5f) * guiHeight);
	}
}
