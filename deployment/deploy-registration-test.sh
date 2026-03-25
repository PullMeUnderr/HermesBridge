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
BACKEND_DIR="$ROOT_DIR"
FRONTEND_DIR="$ROOT_DIR/frontend"
PUBLIC_API_BASE_URL="${NEXT_PUBLIC_API_BASE_URL:-http://193.163.170.8:8082}"
LOCAL_NODE_BIN="${LOCAL_NODE_BIN:-$HOME/.local/node-v20.18.3-darwin-arm64/bin}"
BACKEND_MAVEN_ARGS="${BACKEND_MAVEN_ARGS:--Ptdlight-native-linux-amd64-gnu-ssl1 -DskipTests package}"

if [[ -d "$LOCAL_NODE_BIN" ]]; then
  export PATH="$LOCAL_NODE_BIN:$PATH"
fi

REMOTE_BACKEND_DIR="/opt/hermesbridge-registration/backend"
REMOTE_FRONTEND_DIR="/opt/hermesbridge-registration/frontend-runtime"
REMOTE_BACKEND_SERVICE="hermesbridge-registration-backend.service"
REMOTE_FRONTEND_SERVICE="hermesbridge-registration-frontend.service"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cd "$BACKEND_DIR"
./mvnw $BACKEND_MAVEN_ARGS

JAR_PATH="$(ls -1 target/*.jar | grep -v '\.original$' | head -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  echo "Boot jar not found in target/"
  exit 1
fi

cd "$FRONTEND_DIR"
NEXT_PUBLIC_API_BASE_URL="$PUBLIC_API_BASE_URL" npm run build
mkdir -p .next/standalone/.next
rsync -a --delete .next/static/ .next/standalone/.next/static/
rsync -a --delete public/ .next/standalone/public/

FRONTEND_TGZ="$TMP_DIR/hermesbridge-registration-frontend.tgz"
tar -C .next/standalone -czf "$FRONTEND_TGZ" .

scp "${SSH_OPTS[@]}" "$BACKEND_DIR/$JAR_PATH" "$REMOTE:/root/hermesbridge-registration-backend.jar.new"
scp "${SSH_OPTS[@]}" "$FRONTEND_TGZ" "$REMOTE:/root/hermesbridge-registration-frontend.tgz.new"

ssh "${SSH_OPTS[@]}" "$REMOTE" "
  set -euo pipefail
  TS=\$(date +%Y%m%d%H%M%S)
  systemctl stop $REMOTE_FRONTEND_SERVICE
  systemctl stop $REMOTE_BACKEND_SERVICE

  mv /root/hermesbridge-registration-backend.jar.new $REMOTE_BACKEND_DIR/hermesbridge.jar
  chown hermes:hermes $REMOTE_BACKEND_DIR/hermesbridge.jar

  rm -rf ${REMOTE_FRONTEND_DIR}.deploy-\$TS
  if [ -d $REMOTE_FRONTEND_DIR ]; then
    mv $REMOTE_FRONTEND_DIR ${REMOTE_FRONTEND_DIR}.deploy-\$TS
  fi
  mkdir -p $REMOTE_FRONTEND_DIR
  tar -xzf /root/hermesbridge-registration-frontend.tgz.new -C $REMOTE_FRONTEND_DIR
  chown -R hermes:hermes $REMOTE_FRONTEND_DIR

  systemctl start $REMOTE_BACKEND_SERVICE
  systemctl start $REMOTE_FRONTEND_SERVICE

  for i in \$(seq 1 30); do
    if curl -fsS http://127.0.0.1:8082/actuator/health >/tmp/hermesbridge-registration-health.out 2>/dev/null; then
      break
    fi
    sleep 2
  done

  curl -fsS http://127.0.0.1:8082/actuator/health
  printf '\n---\n'
  curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3003/
  printf '%s\n' '---'
  systemctl status $REMOTE_BACKEND_SERVICE --no-pager -n 20
  printf '%s\n' '---'
  systemctl status $REMOTE_FRONTEND_SERVICE --no-pager -n 20
"
