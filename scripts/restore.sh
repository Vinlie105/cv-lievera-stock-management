#!/usr/bin/env bash
# Restore a backup made by backup.sh INTO A FRESH DATABASE. Usage: scripts/restore.sh backups/cvl-....sql.gz.enc
# This replaces all current data. Stop the app first:  docker compose stop app
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || { echo "usage: $0 <backup-file>" >&2; exit 1; }

set -a; . ./.env; set +a
read -r -p "This DROPS and replaces database cv_lievera_stock. Type YES to continue: " ok
[ "$ok" = "YES" ] || { echo aborted; exit 1; }

docker compose exec -T db psql -U "$DB_USERNAME" -d postgres -c "DROP DATABASE IF EXISTS cv_lievera_stock WITH (FORCE);"
docker compose exec -T db psql -U "$DB_USERNAME" -d postgres -c "CREATE DATABASE cv_lievera_stock;"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$1" \
  | gunzip \
  | docker compose exec -T db psql -U "$DB_USERNAME" -d cv_lievera_stock -v ON_ERROR_STOP=1
echo "restored. Start the app:  docker compose start app"
