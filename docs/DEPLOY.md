# HermesBridge Deployment Guide

This guide explains how to deploy HermesBridge on your own server from scratch.

It is written for the current project state and matches the deployment scripts included in the repository.

## What You Need

Minimum:

- Ubuntu 24.04 VPS
- root or sudo access
- Java 21
- PostgreSQL
- Telegram bot token

Optional but recommended:

- HTTPS through your own domain
- Backblaze B2 for media storage
- ngrok for quick temporary HTTPS

## Deployment Modes

HermesBridge currently supports these practical setups.

### Mode 1: Fast Private VPS

Use this when you just want the app online quickly.

- backend on the VPS
- PostgreSQL on the same VPS
- media on local disk
- access by IP or tunnel URL

This is the simplest path.

### Mode 2: VPS + B2 Media

Use this when you want local database but external media storage.

- backend on the VPS
- PostgreSQL on the same VPS
- media in Backblaze B2

This reduces pressure on local disk.

### Mode 3: Full Production

Use this when you move to a stronger server later.

- backend on the server
- PostgreSQL on the same server or managed service
- media in B2 or another S3-compatible object storage
- domain + HTTPS

## Required External Accounts

### Telegram

You need:

- a bot created in `@BotFather`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`

For group sync:

- disable `privacy mode`
- re-add the bot to the group after disabling privacy mode

### Backblaze B2

Optional, only if you want remote media storage.

You need:

- a private bucket
- `APP_MEDIA_S3_ENDPOINT`
- `APP_MEDIA_S3_BUCKET`
- `APP_MEDIA_S3_ACCESS_KEY_ID`
- `APP_MEDIA_S3_SECRET_ACCESS_KEY`
- `APP_MEDIA_S3_REGION`

### ngrok

Optional, only if you want temporary HTTPS without a domain.

You need:

- `NGROK_AUTHTOKEN`

## Server Requirements

Recommended for a small private deployment:

- `2 vCPU`
- `4 GB RAM`
- `40+ GB SSD`

Recommended with more media and longer uptime:

- `4 vCPU`
- `8 GB RAM`
- `80+ GB SSD`

## Install on a Fresh VPS

### 1. Upload or clone the repository locally

You deploy from your workstation using the provided script:

```bash
git clone https://github.com/PullMeUnderr/HermesBridge.git
cd HermesBridge
```

### 2. Prepare the server

On the VPS:

```bash
curl -fsSL https://raw.githubusercontent.com/PullMeUnderr/HermesBridge/main/deployment/install-vps.sh -o /root/install-vps.sh
bash /root/install-vps.sh
```

If you already have the repo locally, you can also copy and run [`deployment/install-vps.sh`](../deployment/install-vps.sh).

What it does:

- installs `openjdk-21-jre-headless`
- installs `postgresql`
- creates app user `hermes`
- creates directories:
  - `/opt/hermesbridge/current`
  - `/var/lib/hermesbridge`
  - `/var/lib/hermesbridge/media`
  - `/var/log/hermesbridge`
  - `/etc/hermesbridge`
- creates PostgreSQL user and database

### 3. Create environment file

Copy the template:

```bash
cp deployment/hermesbridge.env.example /etc/hermesbridge/hermes.env
chmod 600 /etc/hermesbridge/hermes.env
```

You may need to copy the file from your local machine first.

Required values:

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=0.0.0.0
SERVER_PORT=8080

APP_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/hermesbridge
APP_DATASOURCE_USERNAME=hermesbridge
APP_DATASOURCE_PASSWORD=change_me

TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=123456:replace_me
TELEGRAM_BOT_USERNAME=HermesBridgeBot
```

If you keep local media:

```bash
APP_MEDIA_STORAGE_PROVIDER=local
APP_MEDIA_STORAGE_ROOT=/var/lib/hermesbridge/media
```

If you use Backblaze B2:

```bash
APP_MEDIA_STORAGE_PROVIDER=backblaze-b2
APP_MEDIA_S3_ENDPOINT=https://s3.eu-central-003.backblazeb2.com
APP_MEDIA_S3_BUCKET=hermesbridge-media
APP_MEDIA_S3_ACCESS_KEY_ID=replace_me
APP_MEDIA_S3_SECRET_ACCESS_KEY=replace_me
APP_MEDIA_S3_REGION=eu-central-003
```

### 4. Install systemd service

Copy the service file:

```bash
cp deployment/hermesbridge.service /etc/systemd/system/hermesbridge.service
systemctl daemon-reload
systemctl enable hermesbridge
```

### 5. Deploy the application

From your local machine:

```bash
KEY_PATH=$HOME/.ssh/your_key ./deployment/deploy-vps.sh root@SERVER_IP
```

If you use password-only SSH, use your preferred SSH method or upload the jar manually.

The deploy script:

- builds a boot jar
- uploads it to `/opt/hermesbridge/current/hermesbridge.jar`
- updates the systemd unit
- restarts the service

### 6. Verify startup

On the server:

```bash
systemctl status hermesbridge --no-pager
journalctl -u hermesbridge -n 100 --no-pager
```

From your machine:

```bash
curl http://SERVER_IP:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

## HTTPS Options

### Option A: Domain + reverse proxy

Best long-term solution.

Recommended once you move to a stronger server:

- your own domain
- Nginx or Caddy
- TLS certificate

### Option B: ngrok

Good temporary option when you need secure browser APIs right now.

Files:

- [`deployment/hermesbridge-ngrok.sh`](../deployment/hermesbridge-ngrok.sh)
- [`deployment/hermesbridge-ngrok.service`](../deployment/hermesbridge-ngrok.service)

Create:

```bash
printf '%s\n' 'NGROK_AUTHTOKEN=replace_me' 'NGROK_URL=' > /etc/hermesbridge/ngrok.env
chmod 600 /etc/hermesbridge/ngrok.env
cp deployment/hermesbridge-ngrok.service /etc/systemd/system/hermesbridge-ngrok.service
cp deployment/hermesbridge-ngrok.sh /usr/local/bin/hermesbridge-ngrok.sh
chmod +x /usr/local/bin/hermesbridge-ngrok.sh
systemctl daemon-reload
systemctl enable --now hermesbridge-ngrok
```

What it gives you:

- HTTPS URL
- secure context for browser camera/microphone
- PWA installation on mobile

### Option C: Cloudflare quick tunnel

Supported by the repo, but intended as a temporary tunnel.

Files:

- [`deployment/hermesbridge-quick-tunnel.sh`](../deployment/hermesbridge-quick-tunnel.sh)
- [`deployment/hermesbridge-quick-tunnel.service`](../deployment/hermesbridge-quick-tunnel.service)

## Media Storage

### Local media

Simplest mode:

- files stored on server disk
- storage root from `APP_MEDIA_STORAGE_ROOT`

Good for:

- early testing
- private deployment
- one small server

Not ideal for:

- weak disks
- fast growth
- media-heavy usage

### Backblaze B2

Current external storage option already supported in the app.

Good for:

- moving media off the server
- preserving storage during migration
- S3-compatible API usage

Hermes materializes remote files temporarily when it needs to send them to Telegram.

## PostgreSQL

The app uses Flyway migrations.

Current migration set:

- `V1` base schema
- `V2` accounts and auth
- `V3` memberships
- `V4` attachments
- `V5` avatars
- `V6` message replies
- `V7` stable plaintext token support

You do not need to run SQL manually if the configured database is empty and reachable.

## Backups

The repo already includes local PostgreSQL backup automation.

Files:

- [`deployment/hermesbridge-postgres-backup.sh`](../deployment/hermesbridge-postgres-backup.sh)
- [`deployment/hermesbridge-postgres-backup.service`](../deployment/hermesbridge-postgres-backup.service)
- [`deployment/hermesbridge-postgres-backup.timer`](../deployment/hermesbridge-postgres-backup.timer)

Install:

```bash
cp deployment/hermesbridge-postgres-backup.sh /usr/local/bin/hermesbridge-postgres-backup.sh
chmod +x /usr/local/bin/hermesbridge-postgres-backup.sh
cp deployment/hermesbridge-postgres-backup.service /etc/systemd/system/hermesbridge-postgres-backup.service
cp deployment/hermesbridge-postgres-backup.timer /etc/systemd/system/hermesbridge-postgres-backup.timer
systemctl daemon-reload
systemctl enable --now hermesbridge-postgres-backup.timer
```

Manual test:

```bash
systemctl start hermesbridge-postgres-backup.service
systemctl status hermesbridge-postgres-backup.service --no-pager
ls -lah /var/backups/hermesbridge/postgres
```

## Updating Hermes

Each update is simple:

1. pull new code locally
2. deploy with `deployment/deploy-vps.sh`
3. verify `health`
4. verify ngrok or domain endpoint

Recommended after each update:

- `curl /actuator/health`
- open web UI
- send one Hermes -> Telegram message
- send one Telegram -> Hermes message

## Rollback Strategy

Minimum safe rollback:

1. keep the previous jar
2. keep PostgreSQL backups
3. revert the Git commit locally
4. redeploy previous build

If a migration changed the schema, rollback may require restoring a DB backup.

## PWA / Mobile Notes

To install Hermes as a web app:

- open the HTTPS URL in Safari or Chrome
- use “Add to Home Screen”

Important:

- recording voice messages and video notes requires `HTTPS` or `localhost`
- plain `http://SERVER_IP:8080` will not work for browser camera/microphone access

## Production Notes

When you move to a stronger server, the recommended next step is:

- keep Hermes on `systemd`
- add a domain
- add proper reverse proxy TLS
- keep PostgreSQL backups
- move media to B2

That will be much more stable than staying on a tunnel-based public URL.
