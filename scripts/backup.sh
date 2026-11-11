#!/usr/bin/env bash
# Nightly encrypted PostgreSQL backup. Run from the project directory (cron example in docs/DEPLOY_ORACLE.md).
# Output: backups/cvl-YYYYmmdd-HHMMSS.sql.gz.enc  (AES-256, key derived from BACKUP_PASSPHRASE). Keeps 14 days.
set -euo pipefail
cd "$(dirname "$0")/.."

set -a; . ./.env; set +a
: "${BACKUP_PASSPHRASE:?BACKUP_PASSPHRASE missing in .env}"

mkdir -p backups
umask 077
OUT="backups/cvl-$(date +%Y%m%d-%H%M%S).sql.gz.enc"

docker compose exec -T db pg_dump -U "$DB_USERNAME" -d cv_lievera_stock \
  | gzip -9 \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -out "$OUT"

# refuse to keep an empty/failed dump
[ "$(stat -c %s "$OUT")" -gt 200 ] || { rm -f "$OUT"; echo "backup failed (empty output)" >&2; exit 1; }

find backups -name 'cvl-*.sql.gz.enc' -mtime +14 -delete
echo "backup ok: $OUT"
