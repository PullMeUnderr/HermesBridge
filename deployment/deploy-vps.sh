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

JAR_PATH="$(ls -1 target/*.jar | grep -v '\.original$' | head -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  echo "Boot jar not found in target/"
  exit 1
fi

ssh "${SSH_OPTS[@]}" "$REMOTE" 'systemctl stop hermesbridge 2>/dev/null || true'
scp "${SSH_OPTS[@]}" "$JAR_PATH" "$REMOTE:/opt/hermesbridge/current/hermesbridge.jar.new"
scp "${SSH_OPTS[@]}" deployment/hermesbridge.service "$REMOTE:/root/hermesbridge.service.new"

ssh "${SSH_OPTS[@]}" "$REMOTE" '
  mv /opt/hermesbridge/current/hermesbridge.jar.new /opt/hermesbridge/current/hermesbridge.jar &&
  chown hermes:hermes /opt/hermesbridge/current/hermesbridge.jar &&
  mv /root/hermesbridge.service.new /etc/systemd/system/hermesbridge.service &&
  systemctl daemon-reload &&
  systemctl restart hermesbridge &&
  systemctl status hermesbridge --no-pager -n 30
'
