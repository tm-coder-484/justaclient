# Justaclient Worker

One Cloudflare Worker for the client's online services:

- **Feedback**: bug reports and ideas sent from inside the client, with logs, stored in a **D1** database.
- **Messaging relay**: friends, online status, short profiles and direct messages between Justaclient players, through
  one **Durable Object** with WebSockets. Both fit in Cloudflare's free plan.

## Deploy (once)

1. Install [Node.js](https://nodejs.org) 20 or newer.
2. Double-click `deploy.bat` (Windows) or run `./deploy.sh`. It signs you in to Cloudflare, creates the `justaclient`
   D1 database, creates the table from `schema.sql` and deploys the Worker.
3. Copy the `https://justaclient.<your-subdomain>.workers.dev` address it prints into the client:
   **Right Shift > Utility > Feedback > Server address**. Messages uses the same address unless you set another one in
   **Social > Messages > Relay address**.

Already deployed it for feedback in v0.10? Run the same script again: it adds the relay to the same Worker.

## Messaging relay

- **Off by default.** The client connects only after you switch on **Social > Messages**.
- **Sign-in through Mojang.** The relay sends a random id, the client tells Mojang's session server it joined that id
  (your access token only ever goes to Mojang, never to the relay) and the relay asks Mojang who did. A name is only
  trusted after that, so nobody can pretend to be you.
- **Friends only.** Two players can message each other only when each has the other in their Friends list. Someone who
  adds you but whom you have not added sees you as "has not added you back" and learns nothing else.
- **No message storage beyond delivery.** A message to a friend who is offline waits in the Durable Object (up to 100
  per player, at most 7 days) and is deleted the moment it is delivered. History stays on each player's computer
  (`config/justaclient/messages.json`, can be switched off).
- **Limits.** 500 characters per message, about one message a second per connection.
- What it does keep: each player's friend list, status line, profile text, the server they chose to share (off by
  default) and when they were last online, so friends see it.

### Trying it locally

```
npm install ws
npx wrangler@3 dev --var ALLOW_OFFLINE:true
node test/relay-test.mjs
```

`ALLOW_OFFLINE` lets offline (cracked or dev) accounts in without Mojang, for testing only. Keep it `"false"` in
`wrangler.toml` for the deployed Worker.

## Reading reports

- Cloudflare dashboard: Workers & Pages > D1 > `justaclient` > Console: `SELECT created_at, kind, message FROM feedback ORDER BY created_at DESC;`
- Or set a token with `npx wrangler secret put ADMIN_TOKEN` and open `https://justaclient.<you>.workers.dev/feedback?token=<token>`.

Each IP can send 10 reports an hour. IP addresses are never stored, only a salted hash used for that limit.
