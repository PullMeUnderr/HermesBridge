#!/usr/bin/env bash
set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/hermesbridge/postgres}"
DB_NAME="${DB_NAME:-hermesbridge}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET_DIR="${BACKUP_ROOT}/${STAMP}"

install -d -m 700 "$TARGET_DIR"

runuser -u postgres -- pg_dump --format=custom --no-owner --no-privileges "$DB_NAME" > "${TARGET_DIR}/${DB_NAME}.dump"
runuser -u postgres -- pg_dumpall --globals-only > "${TARGET_DIR}/globals.sql"
runuser -u postgres -- psql -d "$DB_NAME" -tAc "SELECT now();" > "${TARGET_DIR}/created_at.txt"

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -exec rm -rf {} +

echo "Backup created at ${TARGET_DIR}"
