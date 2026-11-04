#!/bin/sh
# Deploys the Justaclient Worker (feedback and the messaging relay) to your Cloudflare account. Needs Node.js 20+.
set -e
cd "$(dirname "$0")"
npx --yes wrangler@3 login
if grep -q REPLACE_WITH_DATABASE_ID wrangler.toml; then
  id=$(npx --yes wrangler@3 d1 create justaclient | grep database_id | sed 's/.*= *"\(.*\)"/\1/')
  sed -i.bak "s/REPLACE_WITH_DATABASE_ID/$id/" wrangler.toml && rm -f wrangler.toml.bak
fi
npx --yes wrangler@3 d1 execute justaclient --remote --file=schema.sql
npx --yes wrangler@3 deploy
echo "Done. Put the https://justaclient.<you>.workers.dev address into Justaclient: Utility > Feedback > Server address."
