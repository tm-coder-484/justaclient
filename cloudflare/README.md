# Justaclient Worker

One Cloudflare Worker for the client's online services. Right now it takes **feedback** (bug reports and ideas sent
from inside the client, with logs) and stores it in a **D1** database. The messaging relay joins it later.

## Deploy (once)

1. Install [Node.js](https://nodejs.org) 20 or newer.
2. Double-click `deploy.bat` (Windows) or run `./deploy.sh`. It signs you in to Cloudflare, creates the `justaclient`
   D1 database, creates the table from `schema.sql` and deploys the Worker.
3. Copy the `https://justaclient.<your-subdomain>.workers.dev` address it prints into the client:
   **Right Shift > Utility > Feedback > Server address**.

## Reading reports

- Cloudflare dashboard: Workers & Pages > D1 > `justaclient` > Console: `SELECT created_at, kind, message FROM feedback ORDER BY created_at DESC;`
- Or set a token with `npx wrangler secret put ADMIN_TOKEN` and open `https://justaclient.<you>.workers.dev/feedback?token=<token>`.

Each IP can send 10 reports an hour. IP addresses are never stored, only a salted hash used for that limit.
