package dev.justaclient.common.inventory;

/** The anvil's level rules the helper needs, kept free of game classes so the self-test can check them. */
public final class AnvilMath {
	/** Survival anvils refuse jobs that cost this many levels or more ("Too Expensive!"). */
	public static final int TOO_EXPENSIVE = 40;

	private AnvilMath() {
	}

	/**
	 * The level an enchantment ends up at when {@code add} is put onto {@code have}: two equal levels go up by one,
	 * otherwise the higher one wins, and the result is capped at {@code max} the way the anvil caps it.
	 */
	public static int merged(int have, int add, int max) {
		int level = have == add ? have + 1 : Math.max(have, add);
		return Math.min(level, max);
	}

	/** True if putting level {@code add} onto level {@code have} makes the item better. */
	public static boolean improves(int have, int add, int max) {
		return merged(have, add, max) > have;
	}

	/**
	 * The levels both items' past anvil work adds to any job on them. The anvil adds each item's own penalty, so two
	 * items worked on before cost more even for a small change.
	 */
	public static int priorWork(int leftPenalty, int rightPenalty) {
		return Math.max(0, leftPenalty) + Math.max(0, rightPenalty);
	}

	/** True if the past work alone already reaches the survival limit. */
	public static boolean tooExpensive(int priorWork) {
		return priorWork >= TOO_EXPENSIVE;
	}
}
