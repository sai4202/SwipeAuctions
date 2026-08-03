#!/bin/sh
# Daily pg_dump of the self-hosted DB, run inside the db container via cron on the host — replaces the
# automatic backups Supabase used to provide. Install with a crontab entry like:
#   0 3 * * * cd /path/to/deploy && ./backup.sh
#
# Also archives the uploads/ Docker volume (user-submitted images: banners, chat attachments, KYC docs,
# etc.) — added after discovering the old Railway host's local disk was ephemeral, silently losing every
# upload on redeploy. This volume is persistent here, but only a backup protects against a bad `docker
# volume rm`/host failure/etc.
set -eu
cd "$(dirname "$0")"
set -a; . ./.env; set +a
KEEP_DAYS=14
STAMP="$(date +%Y%m%d-%H%M%S)"

docker compose exec -T db pg_dump -U "${DB_USER}" -Fc swipeauctions > "backups/swipeauctions-${STAMP}.dump"
docker compose exec -T app tar czf - -C /app uploads > "backups/uploads-${STAMP}.tar.gz"
find backups -name '*.dump' -mtime "+${KEEP_DAYS}" -delete
find backups -name '*.tar.gz' -mtime "+${KEEP_DAYS}" -delete
