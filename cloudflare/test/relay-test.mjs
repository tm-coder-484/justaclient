// Checks the messaging relay end to end with three pretend players. Run the Worker locally in test mode first:
//   npx wrangler@3 dev --var ALLOW_OFFLINE:true
// then: node test/relay-test.mjs [ws://localhost:8787/relay]
// Needs the ws package: npm install ws
import WebSocket from "ws";
const URL = process.argv[2] || "ws://localhost:8787/relay";
let pass = 0, fail = 0;
const check = (name, ok, detail = "") => { ok ? pass++ : fail++; console.log(`${ok ? "PASS" : "FAIL"} ${name}${ok ? "" : " :: " + detail}`); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function client(name) {
  const ws = new WebSocket(URL);
  const inbox = [];
  ws.on("message", (d) => inbox.push(JSON.parse(d.toString())));
  const c = {
    ws, inbox,
    send: (m) => ws.send(JSON.stringify(m)),
    async wait(pred, ms = 3000) { const end = Date.now() + ms; while (Date.now() < end) { const i = inbox.findIndex(pred); if (i >= 0) return inbox.splice(i, 1)[0]; await sleep(30); } return null; },
    async hello() { if (ws.readyState !== 1) await new Promise((r) => ws.on("open", r)); const ch = await c.wait((m) => m.t === "challenge"); c.send({ t: "hello", name }); return [ch, await c.wait((m) => m.t === "welcome" || m.t === "error")]; },
    close: () => new Promise((r) => { ws.on("close", r); ws.close(); }),
  };
  return c;
}
const run = Date.now().toString(36).slice(-4);
const A = "Alice" + run, B = "Bob" + run, C = "Cara" + run;
const a = client(A), b = client(B), c = client(C);
const [ch, wa] = await a.hello();
check("relay sends a 40-character challenge", ch && /^[0-9a-f]{40}$/.test(ch.serverId), JSON.stringify(ch));
check("welcome with the name (offline test mode, not verified)", wa && wa.t === "welcome" && wa.name === A && wa.verified === false, JSON.stringify(wa));
await b.hello(); await c.hello();
a.send({ t: "dm", id: "x0", to: B, text: "hi early" });
check("no messages before both are friends", (await a.wait((m) => m.t === "ack" && m.id === "x0"))?.state === "not_friends");
a.send({ t: "friends", names: [B] });
const pend = await a.wait((m) => m.t === "presence" && m.name.toLowerCase() === B.toLowerCase());
check("a one-sided friend shows as pending, no presence leaked", pend && pend.pending === true && pend.online === false, JSON.stringify(pend));
b.send({ t: "friends", names: [A] });
const pb = await b.wait((m) => m.t === "presence" && m.name === A && !m.pending);
check("mutual friends see each other online", pb && pb.online === true, JSON.stringify(pb));
const pa = await a.wait((m) => m.t === "presence" && m.name === B && m.online);
check("the other side hears it too", !!pa, JSON.stringify(a.inbox));
a.send({ t: "profile", status: "Building a castle", bio: "hi there", server: "" });
const prof = await b.wait((m) => m.t === "presence" && m.name === A && m.status === "Building a castle");
check("profile changes reach friends", prof && prof.bio === "hi there", JSON.stringify(prof));
a.send({ t: "dm", id: "m1", to: B, text: "hello bob" });
const got = await b.wait((m) => m.t === "dm");
check("a message is delivered live", got && got.from === A && got.text === "hello bob" && got.id === "m1", JSON.stringify(got));
check("sender gets delivered", (await a.wait((m) => m.t === "ack" && m.id === "m1"))?.state === "delivered");
c.send({ t: "friends", names: [A] });
c.send({ t: "dm", id: "c1", to: A, text: "spam" });
check("a stranger who added you cannot message you", (await c.wait((m) => m.t === "ack" && m.id === "c1"))?.state === "not_friends");
check("and you did not get it", (await a.wait((m) => m.t === "dm", 500)) === null);
await b.close();
const off = await a.wait((m) => m.t === "presence" && m.name === B && m.online === false);
check("friends see you go offline", !!off && off.lastSeen > 0, JSON.stringify(off));
a.send({ t: "dm", id: "q1", to: B, text: "while you were away" });
check("a message to an offline friend is queued", (await a.wait((m) => m.t === "ack" && m.id === "q1"))?.state === "queued");
const b2 = client(B); await b2.hello(); b2.send({ t: "friends", names: [A] });
const late = await b2.wait((m) => m.t === "dm");
check("the queued message arrives on sign-in", late && late.text === "while you were away" && late.queued === true, JSON.stringify(late));
await b2.close();
const b3 = client(B); await b3.hello(); b3.send({ t: "friends", names: [A] });
check("and it is deleted once delivered", (await b3.wait((m) => m.t === "dm", 800)) === null);
a.send({ t: "dm", id: "long", to: B, text: "x".repeat(900) });
const longMsg = await b3.wait((m) => m.t === "dm");
check("long messages are cut to 500 characters", longMsg && longMsg.text.length === 500, longMsg && longMsg.text.length);
const bad = client("not a name!"); const [, wb] = await bad.hello();
check("a bad name is refused", wb && wb.t === "error", JSON.stringify(wb));
for (let i = 0; i < 15; i++) a.send({ t: "dm", id: "r" + i, to: B, text: "flood " + i });
let slowed = false; for (let i = 0; i < 15; i++) { const ack = await a.wait((m) => m.t === "ack" && m.id === "r" + i); if (ack && ack.state === "slow_down") slowed = true; }
check("flooding is slowed down", slowed);
a.send({ t: "ping" });
check("ping gets pong", !!(await a.wait((m) => m.t === "pong")));
console.log(`${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
