package dev.justaclient.common.spatial;

import org.jspecify.annotations.Nullable;

/**
 * A menu drawn on a panel in front of the camera (Spatial menus). Everything is in camera space: the eye at the origin
 * looking down -Z, +Y up, +X right. The panel can lean back, turn and bend around a vertical axis towards the viewer.
 * <p>
 * The same maths runs twice: in the shader that draws the panel (per pixel, see post/spatial.fsh) and here, to turn the
 * mouse position on the window back into a point on the menu. Keeping both in one place is what keeps clicks landing
 * where the eye sees them.
 */
public final class SpatialPanel {
	/** At full bend the panel wraps this far around the viewer, in radians (150 degrees). */
	public static final double MAX_BEND = Math.toRadians(150);

	public final double[] center;
	public final double[] right;
	public final double[] up;
	/** Points from the panel towards the viewer. */
	public final double[] normal;
	public final double halfWidth;
	public final double halfHeight;
	/** Radius of the bend; 0 means flat. */
	public final double radius;

	public SpatialPanel(double[] center, double[] right, double[] up, double[] normal, double halfWidth, double halfHeight, double radius) {
		this.center = center;
		this.right = right;
		this.up = up;
		this.normal = normal;
		this.halfWidth = halfWidth;
		this.halfHeight = halfHeight;
		this.radius = radius;
	}

	/**
	 * A panel straight ahead at {@code distance}, raised by {@code lift}, leaning back by {@code leanDeg} (top away from
	 * you), turned by {@code turnDeg} (right edge away from you) and bent by {@code bend} (0 flat, 1 {@link #MAX_BEND}).
	 */
	public static SpatialPanel place(double distance, double lift, double halfWidth, double halfHeight, double leanDeg, double turnDeg, double bend) {
		double[] right = {1, 0, 0};
		double[] up = {0, 1, 0};
		double[] normal = {0, 0, 1};
		double turn = Math.toRadians(turnDeg);
		right = rotate(right, up, turn);
		normal = rotate(normal, up, turn);
		double lean = Math.toRadians(leanDeg);
		up = rotate(up, right, -lean);
		normal = rotate(normal, right, -lean);
		double angle = Math.max(0, Math.min(1, bend)) * MAX_BEND;
		double radius = angle < 1e-4 ? 0 : halfWidth / (angle / 2);
		return new SpatialPanel(new double[] {0, lift, -distance}, right, up, normal, halfWidth, halfHeight, radius);
	}

	/**
	 * Where a ray from the eye along {@code dir} meets the panel, as (u, v) with -1..1 spanning it (u right, v up), or
	 * null if it misses. With {@code extend}, a ray that misses the panel still gets the point on the panel's flat plane,
	 * so a drag that leaves the edge keeps moving.
	 */
	public double @Nullable [] hit(double dx, double dy, double dz, boolean extend) {
		double[] d = {dx, dy, dz};
		if (radius > 0) {
			double[] axis = add(center, scale(normal, radius));
			double oa = -dot(axis, right);
			double ob = -dot(axis, normal);
			double oy = -dot(axis, up);
			double da = dot(d, right);
			double db = dot(d, normal);
			double dyy = dot(d, up);
			double qa = da * da + db * db;
			double qb = 2 * (oa * da + ob * db);
			double qc = oa * oa + ob * ob - radius * radius;
			double disc = qb * qb - 4 * qa * qc;
			if (qa > 1e-12 && disc >= 0) {
				double s = Math.sqrt(disc);
				double[] roots = {(-qb - s) / (2 * qa), (-qb + s) / (2 * qa)};
				double @Nullable [] fallback = null;
				for (double t : roots) {
					if (t <= 0) {
						continue;
					}
					double a = oa + t * da;
					double b = ob + t * db;
					double u = radius * Math.atan2(a, -b) / halfWidth;
					double v = (oy + t * dyy) / halfHeight;
					if (Math.abs(u) <= 1 && Math.abs(v) <= 1) {
						return new double[] {u, v};
					}
					if (fallback == null && b < 0) {
						// The far side of the bend, facing the viewer: off the panel, but the right way round.
						fallback = new double[] {u, v};
					}
				}
				if (extend && fallback != null) {
					return fallback;
				}
			}
			if (!extend) {
				return null;
			}
		}
		double denom = dot(d, normal);
		if (Math.abs(denom) < 1e-9) {
			return null;
		}
		double t = dot(center, normal) / denom;
		if (t <= 0) {
			return null;
		}
		double[] local = {d[0] * t - center[0], d[1] * t - center[1], d[2] * t - center[2]};
		double u = dot(local, right) / halfWidth;
		double v = dot(local, up) / halfHeight;
		if (!extend && (Math.abs(u) > 1 || Math.abs(v) > 1)) {
			return null;
		}
		return new double[] {u, v};
	}

	/** The point on the panel at (u, v), in camera space; the inverse of {@link #hit}. */
	public double[] point(double u, double v) {
		double y = v * halfHeight;
		if (radius <= 0) {
			return add(add(center, scale(right, u * halfWidth)), scale(up, y));
		}
		double theta = u * halfWidth / radius;
		double[] axis = add(center, scale(normal, radius));
		return add(add(add(axis, scale(right, radius * Math.sin(theta))), scale(normal, -radius * Math.cos(theta))), scale(up, y));
	}

	/** Moves a GUI pixel ({@code gx}, {@code gy}, y down) on a GUI of {@code w} x {@code h} to panel (u, v). */
	public static double[] guiToPanel(double gx, double gy, double w, double h) {
		return new double[] {gx / w * 2 - 1, 1 - gy / h * 2};
	}

	/** Panel (u, v) back to a GUI pixel, y down. */
	public static double[] panelToGui(double u, double v, double w, double h) {
		return new double[] {(u + 1) / 2 * w, (1 - v) / 2 * h};
	}

	/** Rotates {@code v} around the unit {@code axis} by {@code angle} radians (right hand rule). */
	public static double[] rotate(double[] v, double[] axis, double angle) {
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double[] cross = cross(axis, v);
		double k = dot(axis, v) * (1 - c);
		return new double[] {v[0] * c + cross[0] * s + axis[0] * k, v[1] * c + cross[1] * s + axis[1] * k, v[2] * c + cross[2] * s + axis[2] * k};
	}

	public static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	public static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	static double[] add(double[] a, double[] b) {
		return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
	}

	static double[] scale(double[] a, double s) {
		return new double[] {a[0] * s, a[1] * s, a[2] * s};
	}
}
