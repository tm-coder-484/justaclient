package dev.justaclient.common.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GgGateTest {
	private static final List<String> TRIGGERS = List.of("won the game", " Winner: ", "  ");

	private static String trigger(String line) {
		return GgGate.trigger(line, ChatText.sender(line), TRIGGERS);
	}

	@Test
	void serverLinesTrigger() {
		assertEquals("won the game", trigger("Red team won the game!"));
		assertEquals("Winner:", trigger("Winner: Steve"));
		assertEquals("won the game", GgGate.trigger("RED WON THE GAME", TRIGGERS));
	}

	@Test
	void playersTypingTheTriggerDoNot() {
		assertNull(trigger("[MVP+] Bob: won the game"));
		assertNull(trigger("<Bob> I won the game"));
		assertNull(trigger("Bob » Winner: me"));
		assertNull(trigger("Nothing to see"));
	}

	@Test
	void oneMessagePerWorldAndCooldown() {
		GgGate gate = new GgGate();
		Object world = new Object();
		assertTrue(gate.allow(0, world, 5_000));
		gate.sent(0, world);
		assertFalse(gate.allow(60_000, world, 5_000));
		Object next = new Object();
		assertFalse(gate.allow(4_999, next, 5_000));
		assertTrue(gate.allow(5_000, next, 5_000));
	}

	@Test
	void serverList() {
		assertTrue(GgGate.serverAllowed(null, List.of(" ")));
		assertFalse(GgGate.serverAllowed(null, List.of("hypixel")));
		assertTrue(GgGate.serverAllowed("mc.Hypixel.net", List.of("hypixel")));
		assertFalse(GgGate.serverAllowed("play.example.org", List.of("hypixel")));
	}
}
