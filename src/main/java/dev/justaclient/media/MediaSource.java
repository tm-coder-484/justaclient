package dev.justaclient.media;

import java.util.Locale;

/** Reads the system's music player. One implementation per operating system. */
public interface MediaSource extends AutoCloseable {
	enum Command {
		TOGGLE,
		NEXT,
		PREVIOUS
	}

	/** The latest reading. Called from the poll thread only. */
	MediaState poll();

	/** Sends a media key to the player. Called from the poll thread only. */
	void send(Command command);

	@Override
	void close();

	/** The source for this operating system. {@code spotifyOnly} ignores other players such as browsers. */
	static MediaSource create(boolean spotifyOnly) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return new WindowsSource(spotifyOnly);
		}
		if (os.contains("mac")) {
			return new MacSource();
		}
		return new LinuxSource(spotifyOnly);
	}
}
