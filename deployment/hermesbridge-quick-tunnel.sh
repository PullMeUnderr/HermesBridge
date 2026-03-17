#!/usr/bin/env bash
set -euo pipefail

URL_FILE="/var/lib/hermesbridge/quick-tunnel-url"
mkdir -p "$(dirname "$URL_FILE")"
: > "$URL_FILE"

/usr/bin/cloudflared tunnel --no-autoupdate --protocol http2 --url http://127.0.0.1:8080 2>&1 | while IFS= read -r line; do
  printf '%s\n' "$line"
  if [[ "$line" =~ https://[-a-z0-9]+\.trycloudflare\.com ]]; then
    printf '%s\n' "${BASH_REMATCH[0]}" > "$URL_FILE"
  fi
done
