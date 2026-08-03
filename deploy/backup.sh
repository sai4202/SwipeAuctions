#!/bin/sh
# Daily pg_dump of the self-hosted DB, run inside the db container via cron on the host — replaces the
# automatic backups Supabase used to provide. Install with a crontab entry like:
#   0 3 * * * cd /path/to/deploy && ./backup.sh
set -eu
cd "$(dirname "$0")"
set -a; . ./.env; set +a
KEEP_DAYS=14
STAMP="$(date +%Y%m%d-%H%M%S)"

docker compose exec -T db pg_dump -U "${DB_USER}" -Fc swipeauctions > "backups/swipeauctions-${STAMP}.dump"
find backups -name '*.dump' -mtime "+${KEEP_DAYS}" -delete
