package dev.justaclient.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** Runs short helper commands and returns their output. */
final class Processes {
	private Processes() {
	}

	/** Standard output of the command, or null if it could not start, failed or took longer than the timeout. */
	static @Nullable String run(List<String> command, long timeoutMs) {
		Process process;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(false).start();
		} catch (IOException e) {
			return null;
		}
		try (InputStream in = process.getInputStream()) {
			process.getOutputStream().close();
			if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return null;
			}
			String out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return process.exitValue() == 0 ? out : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** True if the program can be started, e.g. {@code playerctl --version}. */
	static boolean exists(List<String> command) {
		return run(command, 3000) != null;
	}
}
