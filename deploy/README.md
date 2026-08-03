# Deploying to the Hostinger VPS

This box also runs an unrelated app (`vyntra-lms`) directly on port 8080 — everything here is scoped to
avoid it: the app container publishes on **8081**, the db container publishes nothing to the host at all,
and this adds a new nginx site rather than touching the existing ones.

## One-time setup
```
git clone https://github.com/sai4202/SwipeAuctions.git
cd SwipeAuctions/deploy
cp .env.example .env   # fill in real values
mkdir -p backups
docker compose up -d db
```

## Database migration (from Supabase, one-shot — see the plan for the full dump/restore/verify steps)
```
pg_restore -h db -U "$DB_USER" -d swipeauctions /path/to/supabase.dump
```

## Start the app (only after the DB is populated and verified)
```
docker compose up -d app
```

## nginx + TLS
```
cp nginx-api.swipeauctions.in.conf /etc/nginx/sites-available/api.swipeauctions.in
ln -s /etc/nginx/sites-available/api.swipeauctions.in /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
certbot --nginx -d api.swipeauctions.in   # once DNS points here
```

## Backups
```
crontab -e
# add: 0 3 * * * cd /root/SwipeAuctions/deploy && ./backup.sh
```
