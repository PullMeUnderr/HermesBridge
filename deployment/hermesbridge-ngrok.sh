#!/usr/bin/env bash
set -euo pipefail

: "${NGROK_AUTHTOKEN:?NGROK_AUTHTOKEN is required}"

STATE_DIR="/var/lib/hermesbridge"
URL_FILE="$STATE_DIR/ngrok-url"
CONFIG_FILE="$STATE_DIR/ngrok.yml"
NGROK_BIN="$(command -v ngrok)"

if [[ -z "$NGROK_BIN" ]]; then
  echo "ngrok binary not found in PATH" >&2
  exit 127
fi

mkdir -p "$STATE_DIR"
: > "$URL_FILE"

cat >"$CONFIG_FILE" <<EOF
version: 3
agent:
  authtoken: ${NGROK_AUTHTOKEN}
EOF

if [[ -n "${NGROK_URL:-}" ]]; then
  NGROK_ARGS=(http --config "$CONFIG_FILE" --log stdout --url "$NGROK_URL" http://127.0.0.1:8080)
else
  NGROK_ARGS=(http --config "$CONFIG_FILE" --log stdout http://127.0.0.1:8080)
fi

"$NGROK_BIN" "${NGROK_ARGS[@]}" 2>&1 | while IFS= read -r line; do
  printf '%s\n' "$line"
  if [[ "$line" =~ https://[-a-z0-9.]+\.(ngrok-free\.dev|ngrok-free\.app|ngrok\.app|ngrok\.dev|ngrok\.io|ngrok\.pizza) ]]; then
    printf '%s\n' "${BASH_REMATCH[0]}" > "$URL_FILE"
  fi
done
