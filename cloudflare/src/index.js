// Justaclient Worker: in-client feedback (stored in D1) and the messaging relay (a Durable Object, see relay.js).
// GET  /relay      WebSocket: friends, presence, profiles and direct messages
// POST /feedback   { kind, message, player?, clientVersion, minecraftVersion, os, mods?, settings?, latestLog?, crashReport? }
// GET  /feedback?token=ADMIN_TOKEN&limit=50   lists recent reports (only when the ADMIN_TOKEN secret is set)
// GET  /health
export { Relay } from "./relay.js";

const KINDS = new Set(["bug", "idea", "other"]);
const PER_HOUR = 10;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function clip(value, max) {
  if (typeof value !== "string") return null;
  const bytes = new TextEncoder().encode(value);
  if (bytes.length <= max) return value;
  // Keep the end of long logs: that is where errors are.
  return "[cut]\n" + new TextDecoder().decode(bytes.slice(bytes.length - max));
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") return json({ ok: true, relay: true });

    if (url.pathname === "/relay") {
      if (request.headers.get("Upgrade") !== "websocket") return json({ error: "Connect with a WebSocket." }, 426);
      const hub = env.RELAY.get(env.RELAY.idFromName("hub"));
      return hub.fetch(request);
    }

    if (url.pathname === "/feedback" && request.method === "POST") {
      let body;
      try {
        body = await request.json();
      } catch {
        return json({ error: "Send JSON." }, 400);
      }
      const kind = String(body.kind || "").toLowerCase();
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!KINDS.has(kind)) return json({ error: "kind must be bug, idea or other." }, 400);
      if (message.length < 3) return json({ error: "Write a little more first." }, 400);

      const ip = request.headers.get("cf-connecting-ip") || "unknown";
      const ipHash = await sha256(ip + (env.SALT || "justaclient"));
      const hourAgo = new Date(Date.now() - 3600_000).toISOString();
      const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM feedback WHERE ip_hash = ? AND created_at > ?").bind(ipHash, hourAgo).first();
      if (recent && recent.n >= PER_HOUR) return json({ error: "Too many reports this hour. Try again later." }, 429);

      const max = Number(env.MAX_FIELD_BYTES || 400000);
      const id = crypto.randomUUID();
      const created = new Date().toISOString();
      await env.DB.prepare(
        `INSERT INTO feedback (id, created_at, kind, message, player, client_version, minecraft_version, os, mods, settings, latest_log, crash_report, ip_hash)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        id, created, kind, clip(message, 20000), clip(body.player, 64), clip(body.clientVersion, 32), clip(body.minecraftVersion, 32),
        clip(body.os, 128), clip(body.mods, 64000), clip(body.settings, 200000), clip(body.latestLog, max), clip(body.crashReport, max), ipHash
      ).run();
      return json({ id, created });
    }

    if (url.pathname === "/feedback" && request.method === "GET") {
      if (!env.ADMIN_TOKEN || url.searchParams.get("token") !== env.ADMIN_TOKEN) return json({ error: "Not allowed." }, 403);
      const limit = Math.min(200, Number(url.searchParams.get("limit") || 50));
      const rows = await env.DB.prepare(
        "SELECT id, created_at, kind, message, player, client_version, minecraft_version, os FROM feedback ORDER BY created_at DESC LIMIT ?"
      ).bind(limit).all();
      return json(rows.results);
    }

    return json({ error: "Not found." }, 404);
  },
};
