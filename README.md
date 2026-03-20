# HermesBridge

HermesBridge is a self-hosted messenger with a built-in Telegram bridge.

The project lets you:

- register users through a Telegram bot
- create internal chats with roles and invite codes
- sync messages between Hermes and Telegram groups
- exchange text, photos, videos, voice messages, files, replies, mentions, and video notes
- use a web client and install it as a PWA on mobile

The current stack is Java/Spring Boot + PostgreSQL + Telegram Bot API + a Next.js frontend.

## What HermesBridge Is

HermesBridge is not a Telegram client replacement that logs in with your personal Telegram account.
It is a separate messenger with its own users, chats, tokens, and UI.

Telegram is connected through a bot:

- a Telegram group is linked to a Hermes conversation
- messages from Telegram are copied into Hermes
- messages from Hermes are delivered back to Telegram
- media and reply context are preserved where Bot API allows it

This makes Hermes useful when you want your own controlled communication layer and still need access to Telegram group traffic through a bridge.

## Main Features

- Telegram-first account registration
- stable bearer token per user
- chat roles: `OWNER`, `ADMIN`, `MEMBER`
- invite-based access flow
- group binding between Hermes conversations and Telegram chats
- message sync in both directions
- replies and `@mentions`
- media sync:
  - photos
  - files/documents
  - videos
  - voice messages
  - video notes
  - media groups/albums
- editable Hermes profile with avatar and username
- web UI with mobile PWA mode
- optional Backblaze B2 storage for media
- ready-to-use VPS deployment scripts

## Documentation

Start here:

- [Deployment guide](./docs/DEPLOY.md)
- [Architecture guide](./docs/ARCHITECTURE.md)
- [FAQ and troubleshooting](./docs/FAQ.md)
- [Deployment scripts overview](./deployment/README.md)

## Quick Start

### 1. Requirements

- Java 21
- PostgreSQL for persistent environments
- Telegram bot token from `@BotFather`

For local development only, the app can run with in-memory H2 by default.

### 2. Configure environment

Minimal local Telegram setup:

```bash
export TELEGRAM_ENABLED=true
export TELEGRAM_BOT_TOKEN=123456:replace_me
export TELEGRAM_BOT_USERNAME=HermesBridgeBot
export APP_DEFAULT_TENANT_KEY=main
```

Persistent database:

```bash
export APP_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/hermesbridge
export APP_DATASOURCE_USERNAME=hermesbridge
export APP_DATASOURCE_PASSWORD=change_me
```

Optional Backblaze B2 storage:

```bash
export APP_MEDIA_STORAGE_PROVIDER=backblaze-b2
export APP_MEDIA_S3_ENDPOINT=https://s3.eu-central-003.backblazeb2.com
export APP_MEDIA_S3_BUCKET=hermesbridge-media
export APP_MEDIA_S3_ACCESS_KEY_ID=replace_me
export APP_MEDIA_S3_SECRET_ACCESS_KEY=replace_me
export APP_MEDIA_S3_REGION=eu-central-003
```

### 3. Run locally

```bash
./mvnw spring-boot:run
```

Open:

- web UI: [http://localhost:8080](http://localhost:8080)
- health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 4. Register through the bot

Open a private chat with your Telegram bot and send:

```text
/start
```

The bot creates a Hermes account and returns your bearer token.

### 5. Use the web app

Open the web UI, paste the token, and you can:

- create chats
- join by invite code
- read and send messages
- upload media
- record voice messages and video notes in secure contexts

## Repo Structure

Top-level structure:

- [`src/main/java/com/vladislav/tgclone/account`](./src/main/java/com/vladislav/tgclone/account) — accounts, tokens, Telegram identity binding
- [`src/main/java/com/vladislav/tgclone/bridge`](./src/main/java/com/vladislav/tgclone/bridge) — relay, bindings, delivery records, Telegram delivery
- [`src/main/java/com/vladislav/tgclone/conversation`](./src/main/java/com/vladislav/tgclone/conversation) — chats, roles, invites, messages, attachments, replies
- [`src/main/java/com/vladislav/tgclone/media`](./src/main/java/com/vladislav/tgclone/media) — local and S3-compatible media storage
- [`src/main/java/com/vladislav/tgclone/security`](./src/main/java/com/vladislav/tgclone/security) — bearer auth
- [`src/main/java/com/vladislav/tgclone/telegram`](./src/main/java/com/vladislav/tgclone/telegram) — polling, bot client, dialog state
- [`frontend`](./frontend) — React + Next.js web UI with SCSS modules
- [`src/main/resources/static`](./src/main/resources/static) — legacy static web UI / PWA
- [`deployment`](./deployment) — install, deploy, service, backup, tunnel scripts
- [`docs`](./docs) — self-hosting documentation

## Practical Notes

- Telegram group sync requires the bot to be present in the group.
- For normal group messages, Telegram `privacy mode` must be disabled in `@BotFather`.
- Voice recording and video-note recording in the browser require `HTTPS` or `localhost`.
- Media limits are controlled by:
  - `APP_MEDIA_MAX_FILE_SIZE_BYTES`
  - `APP_MULTIPART_MAX_FILE_SIZE`
  - `APP_MULTIPART_MAX_REQUEST_SIZE`

## Frontend Development

The frontend was moved to a standalone Next.js app in [`frontend`](./frontend).

Run it locally:

```bash
cd frontend
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

Open:

- Next.js frontend: [http://localhost:3000](http://localhost:3000)
- Spring Boot API: [http://localhost:8080](http://localhost:8080)

## Server Recommendations

For a small private deployment:

- `2 vCPU`
- `4 GB RAM`
- `40+ GB SSD`

Comfortable setup:

- `4 vCPU`
- `8 GB RAM`
- `80+ GB SSD`

If you keep PostgreSQL, Java, media, and HTTPS on the same host, the second option is much more comfortable.

## Current Deployment Model

The project already includes a working self-hosted path:

- Spring Boot as a `systemd` service
- local PostgreSQL
- optional ngrok HTTPS tunnel
- optional Backblaze B2 media storage
- PostgreSQL backup automation

See the full setup in [docs/DEPLOY.md](./docs/DEPLOY.md).

## License / Usage

There is no explicit open-source license file in the repository yet.
If you want third parties to legally reuse and redistribute the project, add a license before broad publication.
