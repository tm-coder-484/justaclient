-- Justaclient feedback. Apply with: npx wrangler d1 execute justaclient --remote --file=schema.sql
CREATE TABLE IF NOT EXISTS feedback (
  id TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  kind TEXT NOT NULL,             -- bug | idea | other
  message TEXT NOT NULL,
  player TEXT,                    -- null when sent anonymously
  client_version TEXT,
  minecraft_version TEXT,
  os TEXT,
  mods TEXT,                      -- mod list, one per line
  settings TEXT,                  -- active profile JSON
  latest_log TEXT,                -- tail of logs/latest.log
  crash_report TEXT,              -- newest crash report, if any
  ip_hash TEXT                    -- for rate limiting only, never the address itself
);
CREATE INDEX IF NOT EXISTS feedback_created ON feedback (created_at);
CREATE INDEX IF NOT EXISTS feedback_ip ON feedback (ip_hash, created_at);
