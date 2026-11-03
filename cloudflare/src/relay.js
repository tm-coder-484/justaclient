// Justaclient messaging relay: friends, presence, profiles and direct messages between players who have each other as
// friends. One Durable Object ("hub") holds every connection over hibernating WebSockets.
//
// Sign-in: the relay sends a random serverId, the client tells Mojang it "joined" that id (the access token only ever
// goes to Mojang), and the relay asks Mojang's session server who did. Only then is a name trusted.
//
// Storage: friend lists and short profiles, so presence works; a message is kept only while its reader is offline
// (at most 7 days, 100 per reader) and is deleted the moment it is delivered. Nothing else is stored.

const MAX_TEXT = 500;
const MAX_FRIENDS = 200;
const MAX_QUEUE = 100;
const QUEUE_DAYS = 7;
const WINDOW_MS = 10_000;
const MAX_FRAMES = 40; // per socket per window
const MAX_DMS = 12; // per socket per window
const NAME = /^[A-Za-z0-9_]{2,16}$/;

function clip(value, max) {
  if (typeof value !== "string") return "";
  return value.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, max);
}

function randomHex(bytes) {
  const b = crypto.getRandomValues(new Uint8Array(bytes));
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

export class Relay {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS friends (owner TEXT NOT NULL, friend TEXT NOT NULL, PRIMARY KEY (owner, friend))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS profiles (name TEXT PRIMARY KEY, display TEXT, status TEXT, bio TEXT, server TEXT, last_seen INTEGER)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS queue (id TEXT PRIMARY KEY, to_name TEXT NOT NULL, from_name TEXT NOT NULL, text TEXT NOT NULL, sent_at INTEGER NOT NULL)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS queue_to ON queue (to_name, sent_at)`);
  }

  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected a WebSocket", { status: 426 });
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    const serverId = randomHex(20);
    server.serializeAttachment({ serverId, name: null, display: null, verified: false, windowStart: Date.now(), frames: 0, dms: 0 });
    server.send(JSON.stringify({ t: "challenge", serverId }));
    return new Response(null, { status: 101, webSocket: client });
  }

  // Who is connected and signed in, by lower-case name.
  sockets(name) {
    return this.ctx.getWebSockets().filter((ws) => {
      const a = ws.deserializeAttachment();
      return a && a.name === name;
    });
  }

  online(name) {
    return this.sockets(name).length > 0;
  }

  send(ws, msg) {
    try {
      ws.send(JSON.stringify(msg));
    } catch {
      // The socket closed; its close handler tidies up.
    }
  }

  sendTo(name, msg) {
    for (const ws of this.sockets(name)) this.send(ws, msg);
  }

  friendsOf(name) {
    return this.sql.exec(`SELECT friend FROM friends WHERE owner = ?`, name).toArray().map((r) => r.friend);
  }

  mutual(a, b) {
    return this.sql.exec(`SELECT 1 FROM friends WHERE owner = ? AND friend = ?`, a, b).toArray().length > 0
      && this.sql.exec(`SELECT 1 FROM friends WHERE owner = ? AND friend = ?`, b, a).toArray().length > 0;
  }

  mutualFriends(name) {
    return this.friendsOf(name).filter((f) => this.mutual(name, f));
  }

  presence(name) {
    const p = this.sql.exec(`SELECT * FROM profiles WHERE name = ?`, name).toArray()[0] || {};
    return { t: "presence", name: p.display || name, online: this.online(name), status: p.status || "", bio: p.bio || "",
      server: p.server || "", lastSeen: p.last_seen || 0 };
  }

  // Tells each mutual friend who is online about {@code name}'s presence.
  announce(name) {
    const msg = this.presence(name);
    for (const f of this.mutualFriends(name)) this.sendTo(f, msg);
  }

  async verify(name, serverId) {
    const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${serverId}`;
    try {
      const res = await fetch(url);
      if (res.status !== 200) return null;
      const body = await res.json();
      return typeof body.name === "string" ? body.name : null;
    } catch {
      return null;
    }
  }

  async webSocketMessage(ws, data) {
    const a = ws.deserializeAttachment();
    const now = Date.now();
    if (now - a.windowStart > WINDOW_MS) {
      a.windowStart = now;
      a.frames = 0;
      a.dms = 0;
    }
    a.frames++;
    if (a.frames > MAX_FRAMES || typeof data !== "string" || data.length > 4096) {
      this.send(ws, { t: "error", message: "Slow down." });
      ws.serializeAttachment(a);
      return;
    }
    let msg;
    try {
      msg = JSON.parse(data);
    } catch {
      return;
    }
    if (msg.t === "ping") {
      this.send(ws, { t: "pong" });
      ws.serializeAttachment(a);
      return;
    }
    if (msg.t === "hello") {
      const claimed = clip(msg.name, 16);
      if (!NAME.test(claimed)) {
        this.send(ws, { t: "error", message: "That is not a Minecraft name." });
        return;
      }
      let display = await this.verify(claimed, a.serverId);
      let verified = display !== null;
      if (!verified && this.env.ALLOW_OFFLINE === "true") {
        display = claimed; // Testing only: trusts the name without Mojang.
      }
      if (display === null) {
        this.send(ws, { t: "error", message: "Mojang did not confirm your sign-in. Restart the game and try again." });
        ws.close(4001, "not signed in");
        return;
      }
      a.name = display.toLowerCase();
      a.display = display;
      a.verified = verified;
      ws.serializeAttachment(a);
      this.sql.exec(`INSERT INTO profiles (name, display, status, bio, server, last_seen) VALUES (?, ?, '', '', '', ?)
        ON CONFLICT(name) DO UPDATE SET display = excluded.display, last_seen = excluded.last_seen`, a.name, display, now);
      this.send(ws, { t: "welcome", name: display, verified });
      this.sql.exec(`DELETE FROM queue WHERE sent_at < ?`, now - QUEUE_DAYS * 86400_000);
      return;
    }
    if (!a.name) {
      this.send(ws, { t: "error", message: "Say hello first." });
      return;
    }
    switch (msg.t) {
      case "friends": {
        const list = Array.isArray(msg.names) ? msg.names : [];
        const names = [...new Set(list.map((n) => clip(n, 16).toLowerCase()).filter((n) => NAME.test(n) && n !== a.name))].slice(0, MAX_FRIENDS);
        const before = new Set(this.mutualFriends(a.name));
        this.sql.exec(`DELETE FROM friends WHERE owner = ?`, a.name);
        for (const n of names) this.sql.exec(`INSERT OR IGNORE INTO friends (owner, friend) VALUES (?, ?)`, a.name, n);
        const after = this.mutualFriends(a.name);
        // Your view of every friend, including the ones who have not added you yet.
        for (const n of names) {
          const p = this.presence(n);
          const both = after.includes(n);
          this.send(ws, both ? p : { t: "presence", name: p.name, online: false, pending: true, status: "", bio: "", server: "", lastSeen: 0 });
        }
        // Friends who just became mutual (or stopped being) hear about you.
        const mine = this.presence(a.name);
        for (const n of after) if (!before.has(n)) this.sendTo(n, mine);
        for (const n of before) if (!after.includes(n)) this.sendTo(n, { t: "presence", name: mine.name, online: false, pending: true, status: "", bio: "", server: "", lastSeen: 0 });
        // Messages that waited for you.
        for (const row of this.sql.exec(`SELECT * FROM queue WHERE to_name = ? ORDER BY sent_at`, a.name).toArray()) {
          this.send(ws, { t: "dm", id: row.id, from: this.presence(row.from_name).name, text: row.text, at: row.sent_at, queued: true });
          this.sql.exec(`DELETE FROM queue WHERE id = ?`, row.id);
        }
        this.announce(a.name);
        break;
      }
      case "profile": {
        this.sql.exec(`UPDATE profiles SET status = ?, bio = ?, server = ?, last_seen = ? WHERE name = ?`,
          clip(msg.status, 64), clip(msg.bio, 200), clip(msg.server, 64), now, a.name);
        this.announce(a.name);
        break;
      }
      case "dm": {
        a.dms++;
        ws.serializeAttachment(a);
        const id = clip(msg.id, 40) || randomHex(8);
        const to = clip(msg.to, 16).toLowerCase();
        const text = clip(msg.text, MAX_TEXT);
        if (a.dms > MAX_DMS) return this.send(ws, { t: "ack", id, state: "slow_down" });
        if (!NAME.test(to) || !text) return this.send(ws, { t: "ack", id, state: "invalid" });
        if (!this.mutual(a.name, to)) return this.send(ws, { t: "ack", id, state: "not_friends" });
        const out = { t: "dm", id, from: a.display, text, at: now };
        if (this.online(to)) {
          this.sendTo(to, out);
          this.send(ws, { t: "ack", id, state: "delivered" });
        } else {
          const waiting = this.sql.exec(`SELECT COUNT(*) AS n FROM queue WHERE to_name = ?`, to).toArray()[0].n;
          if (waiting >= MAX_QUEUE) return this.send(ws, { t: "ack", id, state: "inbox_full" });
          this.sql.exec(`INSERT OR IGNORE INTO queue (id, to_name, from_name, text, sent_at) VALUES (?, ?, ?, ?, ?)`, id, to, a.name, text, now);
          this.send(ws, { t: "ack", id, state: "queued" });
        }
        break;
      }
      default:
        this.send(ws, { t: "error", message: "Unknown message." });
    }
    ws.serializeAttachment(a);
  }

  async webSocketClose(ws) {
    const a = ws.deserializeAttachment();
    if (a && a.name) {
      this.sql.exec(`UPDATE profiles SET last_seen = ? WHERE name = ?`, Date.now(), a.name);
      // Other sockets of the same player keep them online.
      const msg = this.presence(a.name);
      msg.online = this.ctx.getWebSockets().some((s) => s !== ws && s.deserializeAttachment()?.name === a.name);
      for (const f of this.mutualFriends(a.name)) this.sendTo(f, msg);
    }
  }

  async webSocketError(ws) {
    await this.webSocketClose(ws);
  }
}
