package dev.justaclient.common.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelayProtocolTest {
	@Test
	void parseRejectsJunk() {
		for (String frame : new String[] {"", "   ", "not json", "{", "[1,2]", "null", "42", "\"t\"", "{\"x\":1}", "{\"t\":1"}) {
			assertNull(RelayProtocol.parse(frame), frame);
		}
	}

	@Test
	void parseAcceptsAnObjectWithAType() {
		JsonObject o = RelayProtocol.parse("{\"t\":\"pong\"}");
		assertNotNull(o);
		assertEquals("pong", RelayProtocol.type(o));
		assertEquals("", RelayProtocol.type(RelayProtocol.parse("{\"t\":{\"nested\":true}}")));
	}

	@Test
	void strIsEmptyForMissingNullOrNonPrimitiveFields() {
		JsonObject o = RelayProtocol.parse("{\"t\":\"x\",\"n\":null,\"a\":[1],\"o\":{},\"num\":5,\"b\":true}");
		assertEquals("", RelayProtocol.str(o, "missing"));
		assertEquals("", RelayProtocol.str(o, "n"));
		assertEquals("", RelayProtocol.str(o, "a"));
		assertEquals("", RelayProtocol.str(o, "o"));
		assertEquals("5", RelayProtocol.str(o, "num"));
		assertEquals("true", RelayProtocol.str(o, "b"));
	}

	@Test
	void presenceReadsEveryFieldAndDefaultsTheRest() {
		JsonObject full = RelayProtocol.parse("""
			{"t":"presence","name":"Alex","online":true,"pending":false,"status":"afk","bio":"hi","server":"mc.net","lastSeen":1700000000000}""");
		assertEquals(new RelayProtocol.Presence("Alex", true, false, "afk", "hi", "mc.net", 1_700_000_000_000L), RelayProtocol.presence(full));

		JsonObject odd = RelayProtocol.parse("{\"t\":\"presence\",\"name\":\"Bo\",\"online\":\"true\",\"pending\":1,\"lastSeen\":\"soon\"}");
		assertEquals(new RelayProtocol.Presence("Bo", false, false, "", "", "", 0L), RelayProtocol.presence(odd));
	}

	@Test
	void incomingDmAndAck() {
		JsonObject dm = RelayProtocol.parse("{\"t\":\"dm\",\"id\":\"m1\",\"from\":\"Alex\",\"text\":\"yo\",\"at\":\"123\",\"queued\":true}");
		assertEquals(new RelayProtocol.Dm("m1", "Alex", "yo", 123L, true), RelayProtocol.dmIn(dm));
		JsonObject ack = RelayProtocol.parse("{\"t\":\"ack\",\"id\":\"m1\",\"state\":\"queued\"}");
		assertEquals(new RelayProtocol.Ack("m1", "queued"), RelayProtocol.ack(ack));
		assertEquals(new RelayProtocol.Ack("", ""), RelayProtocol.ack(RelayProtocol.parse("{\"t\":\"ack\"}")));
	}

	@Test
	void outgoingDmIsCutToTheLimit() {
		JsonObject o = RelayProtocol.parse(RelayProtocol.dm("a1", "Bob", "x".repeat(700)));
		assertEquals("dm", RelayProtocol.type(o));
		assertEquals("Bob", RelayProtocol.str(o, "to"));
		assertEquals(RelayProtocol.MAX_TEXT, RelayProtocol.str(o, "text").length());
		String tricky = "say \"hi\"\n\\ \u00e9";
		assertEquals(tricky, RelayProtocol.str(RelayProtocol.parse(RelayProtocol.dm("a2", "Bob", tricky)), "text"));
	}

	@Test
	void outgoingFramesHaveTheirTypes() {
		JsonObject hello = RelayProtocol.parse(RelayProtocol.hello("Steve", "1.4.0"));
		assertEquals("hello", RelayProtocol.type(hello));
		assertEquals("1.4.0", RelayProtocol.str(hello, "version"));
		JsonObject friends = RelayProtocol.parse(RelayProtocol.friends(List.of("A", "B")));
		assertEquals(2, friends.getAsJsonArray("names").size());
		assertEquals("ping", RelayProtocol.type(RelayProtocol.parse(RelayProtocol.ping())));
		assertEquals("mc.net", RelayProtocol.str(RelayProtocol.parse(RelayProtocol.profile("on", "bio", "mc.net")), "server"));
	}

	@Test
	void socketUrlFromWorkerAddress() {
		assertEquals("wss://relay.example.workers.dev/relay", RelayProtocol.socketUrl("https://relay.example.workers.dev/"));
		assertEquals("ws://localhost:8787/relay", RelayProtocol.socketUrl("HTTP://localhost:8787"));
		assertEquals("wss://x.dev/relay", RelayProtocol.socketUrl(" x.dev// "));
		assertEquals("wss://x.dev/relay", RelayProtocol.socketUrl("wss://x.dev/relay"));
		assertNull(RelayProtocol.socketUrl("  /// "));
		assertNull(RelayProtocol.socketUrl(null));
	}

	@Test
	void describeAndAgo() {
		assertEquals("Delivered", RelayProtocol.describe("delivered"));
		assertEquals("weird_state", RelayProtocol.describe("weird_state"));
		long now = 10_000_000_000L;
		assertEquals("just now", RelayProtocol.ago(now + 5000, now));
		assertEquals("5 min ago", RelayProtocol.ago(now - 5 * 60_000, now));
		assertEquals("3 h ago", RelayProtocol.ago(now - 3 * 3_600_000, now));
		assertEquals("yesterday", RelayProtocol.ago(now - 86_400_000, now));
		assertEquals("2 days ago", RelayProtocol.ago(now - 2 * 86_400_000, now));
	}

	@Test
	void boolIsOnlyTrueForARealTrue() {
		JsonObject o = RelayProtocol.parse("{\"t\":\"x\",\"a\":true,\"b\":false,\"c\":\"true\",\"d\":1}");
		assertTrue(RelayProtocol.bool(o, "a"));
		assertFalse(RelayProtocol.bool(o, "b"));
		assertFalse(RelayProtocol.bool(o, "c"));
		assertFalse(RelayProtocol.bool(o, "d"));
		assertFalse(RelayProtocol.bool(o, "e"));
	}
}
