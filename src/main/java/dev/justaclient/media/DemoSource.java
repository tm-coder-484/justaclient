package dev.justaclient.media;

/**
 * A pretend player for testing and screenshots on machines without one. Used only when the game is started with
 * {@code -Djustaclient.mediaDemo=true}; {@code -Djustaclient.mediaDemoArt=<image path>} gives it a cover.
 */
final class DemoSource implements MediaSource {
	private static final long DURATION = 243_000L;
	private long started = System.currentTimeMillis();
	private final String art = System.getProperty("justaclient.mediaDemoArt", "");
	private boolean paused;
	private long pausedAt;

	@Override
	public MediaState poll() {
		long pos = (paused ? pausedAt : System.currentTimeMillis() - started) % DURATION;
		return new MediaState(paused ? MediaState.Status.PAUSED : MediaState.Status.PLAYING, "Midnight City", "M83", "Hurry Up, We're Dreaming", pos,
			DURATION, art.isBlank() ? null : art, "Demo", System.nanoTime());
	}

	@Override
	public void send(Command command) {
		if (command == Command.TOGGLE) {
			if (paused) {
				started = System.currentTimeMillis() - pausedAt;
			} else {
				pausedAt = System.currentTimeMillis() - started;
			}
			paused = !paused;
		}
	}

	@Override
	public void close() {
	}
}
