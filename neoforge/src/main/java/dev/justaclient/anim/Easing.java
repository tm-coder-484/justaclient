package dev.justaclient.anim;

/**
 * Easing curves. Each takes progress t in [0, 1] and returns eased progress (may overshoot for back/elastic).
 */
public enum Easing {
	LINEAR {
		@Override
		public float apply(float t) {
			return t;
		}
	},
	QUAD_OUT {
		@Override
		public float apply(float t) {
			return 1f - (1f - t) * (1f - t);
		}
	},
	CUBIC_OUT {
		@Override
		public float apply(float t) {
			float u = 1f - t;
			return 1f - u * u * u;
		}
	},
	CUBIC_IN_OUT {
		@Override
		public float apply(float t) {
			return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
		}
	},
	QUINT_OUT {
		@Override
		public float apply(float t) {
			float u = 1f - t;
			return 1f - u * u * u * u * u;
		}
	},
	EXPO_OUT {
		@Override
		public float apply(float t) {
			return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
		}
	},
	BACK_OUT {
		@Override
		public float apply(float t) {
			float c1 = 1.70158f;
			float c3 = c1 + 1f;
			float u = t - 1f;
			return 1f + c3 * u * u * u + c1 * u * u;
		}
	},
	ELASTIC_OUT {
		@Override
		public float apply(float t) {
			if (t <= 0f || t >= 1f) {
				return t <= 0f ? 0f : 1f;
			}
			float c4 = (float) (2 * Math.PI / 3);
			return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75f) * c4) + 1);
		}
	};

	public abstract float apply(float t);
}
