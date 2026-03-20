#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 root@server-ip"
  exit 1
fi

REMOTE="$1"
KEY_PATH="${KEY_PATH:-}"
SSH_OPTS=(-o StrictHostKeyChecking=no)

if [[ -n "$KEY_PATH" ]]; then
  SSH_OPTS+=(-i "$KEY_PATH" -o IdentitiesOnly=yes)
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./mvnw -q -DskipTests package
(
  cd frontend
  npm ci
  BACKEND_ORIGIN=http://127.0.0.1:8080 npm run build
)

JAR_PATH="$(ls -1 target/*.jar | grep -v '\.original$' | head -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  echo "Boot jar not found in target/"
  exit 1
fi

FRONTEND_STANDALONE_DIR="frontend/.next/standalone"
FRONTEND_STATIC_DIR="frontend/.next/static"
FRONTEND_PUBLIC_DIR="frontend/public"

if [[ ! -f "${FRONTEND_STANDALONE_DIR}/server.js" ]]; then
  echo "Next standalone build not found in ${FRONTEND_STANDALONE_DIR}"
  exit 1
fi

ssh "${SSH_OPTS[@]}" "$REMOTE" 'systemctl stop hermesbridge 2>/dev/null || true'
ssh "${SSH_OPTS[@]}" "$REMOTE" 'systemctl stop hermesbridge-next 2>/dev/null || true'
scp "${SSH_OPTS[@]}" "$JAR_PATH" "$REMOTE:/opt/hermesbridge/current/hermesbridge.jar.new"
scp "${SSH_OPTS[@]}" deployment/hermesbridge.service "$REMOTE:/root/hermesbridge.service.new"
scp "${SSH_OPTS[@]}" deployment/hermesbridge-next.service "$REMOTE:/root/hermesbridge-next.service.new"

ssh "${SSH_OPTS[@]}" "$REMOTE" '
  rm -rf /opt/hermesbridge-frontend/current.new &&
  mkdir -p /opt/hermesbridge-frontend/current.new
'
scp -r "${SSH_OPTS[@]}" "${FRONTEND_STANDALONE_DIR}/." "$REMOTE:/opt/hermesbridge-frontend/current.new/"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'mkdir -p /opt/hermesbridge-frontend/current.new/.next /opt/hermesbridge-frontend/current.new/public'
scp -r "${SSH_OPTS[@]}" "${FRONTEND_STATIC_DIR}" "$REMOTE:/opt/hermesbridge-frontend/current.new/.next/"
scp -r "${SSH_OPTS[@]}" "${FRONTEND_PUBLIC_DIR}/." "$REMOTE:/opt/hermesbridge-frontend/current.new/public/"

ssh "${SSH_OPTS[@]}" "$REMOTE" '
  mv /opt/hermesbridge/current/hermesbridge.jar.new /opt/hermesbridge/current/hermesbridge.jar &&
  chown hermes:hermes /opt/hermesbridge/current/hermesbridge.jar &&
  mv /root/hermesbridge.service.new /etc/systemd/system/hermesbridge.service &&
  rm -rf /opt/hermesbridge-frontend/current &&
  mv /opt/hermesbridge-frontend/current.new /opt/hermesbridge-frontend/current &&
  chown -R hermes:hermes /opt/hermesbridge-frontend/current &&
  mv /root/hermesbridge-next.service.new /etc/systemd/system/hermesbridge-next.service &&
  systemctl daemon-reload &&
  systemctl restart hermesbridge &&
  systemctl restart hermesbridge-next &&
  systemctl status hermesbridge --no-pager -n 30 &&
  systemctl status hermesbridge-next --no-pager -n 30
'
