#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-hermes}"
APP_GROUP="${APP_GROUP:-$APP_USER}"
APP_HOME="${APP_HOME:-/opt/hermesbridge}"
APP_CURRENT_DIR="${APP_CURRENT_DIR:-$APP_HOME/current}"
APP_DATA_DIR="${APP_DATA_DIR:-/var/lib/hermesbridge}"
APP_LOG_DIR="${APP_LOG_DIR:-/var/log/hermesbridge}"
APP_ENV_DIR="${APP_ENV_DIR:-/etc/hermesbridge}"
DB_NAME="${DB_NAME:-hermesbridge}"
DB_USER="${DB_USER:-hermesbridge}"
DB_PASSWORD="${DB_PASSWORD:-change_me}"

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y openjdk-21-jre-headless postgresql postgresql-contrib

if ! id -u "$APP_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER"
fi

install -d -m 755 -o "$APP_USER" -g "$APP_GROUP" "$APP_HOME"
install -d -m 755 -o "$APP_USER" -g "$APP_GROUP" "$APP_CURRENT_DIR"
install -d -m 755 -o "$APP_USER" -g "$APP_GROUP" "$APP_DATA_DIR"
install -d -m 755 -o "$APP_USER" -g "$APP_GROUP" "$APP_DATA_DIR/media"
install -d -m 755 -o "$APP_USER" -g "$APP_GROUP" "$APP_LOG_DIR"
install -d -m 755 -o root -g root "$APP_ENV_DIR"

systemctl enable postgresql
systemctl start postgresql

runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 \
  || runuser -u postgres -- psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"

runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 \
  || runuser -u postgres -- createdb -O "${DB_USER}" "${DB_NAME}"

echo "Base packages and directories are ready."
echo "Next:"
echo "1. Copy deployment/hermesbridge.env.example to ${APP_ENV_DIR}/hermes.env and fill secrets."
echo "2. Copy deployment/hermesbridge.service to /etc/systemd/system/hermesbridge.service"
echo "3. Deploy the jar to ${APP_CURRENT_DIR}/hermesbridge.jar"
echo "4. Run: systemctl daemon-reload && systemctl enable --now hermesbridge"
