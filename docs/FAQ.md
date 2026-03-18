# HermesBridge FAQ

This FAQ is written for people who want to deploy HermesBridge from GitHub and understand how it works in practice.

## General

### What is HermesBridge?

HermesBridge is a self-hosted messenger with a Telegram bridge.

It gives you:

- your own users
- your own chats
- your own web client
- your own media storage choices
- Telegram group synchronization through a bot

It is not a Telegram login client.

### Why build it this way?

Because it gives you an independent communication layer while keeping access to Telegram group traffic through a controlled bot-based bridge.

### Does HermesBridge work without a VPN?

Hermes itself is your own web application and can be hosted anywhere you choose.
Telegram connectivity depends on the bot bridge and your hosting/network conditions, but Hermes as an application is independent from the Telegram mobile app.

### Is HermesBridge multi-user?

Yes.

It supports:

- multiple accounts
- multiple chats
- roles
- invite codes
- Telegram-linked identities

## Telegram Integration

### Does the bot read all Telegram messages?

Only where Telegram allows it.

The bot can read:

- messages in groups where the bot is present
- private chat messages sent directly to the bot

It cannot read unrelated private Telegram chats between other users.

### Why does the bot not see normal group messages?

Usually because `privacy mode` is still enabled.

You need to:

1. open `@BotFather`
2. disable privacy mode for your bot
3. remove and re-add the bot to the group

### Can HermesBridge sync Telegram channels?

Not as a first-class feature right now.
The current bridge is built around group-style bot updates and conversation binding.

### Why do Telegram replies not always map perfectly?

Reply mapping depends on whether Hermes knows the corresponding external Telegram message id.
When the mapping exists, replies work well.
When Telegram or media-group semantics hide the exact message target, reply accuracy can be limited.

### Why does Hermes send a text line before some Telegram video notes?

Because a plain Telegram `video_note` does not show sender identity clearly in the group by itself.

Hermes now sends:

1. a short context message with the sender name
2. the actual `video_note` as a reply to that context

This keeps the note visually clean while making the author clear.

## Accounts and Tokens

### How are users created?

Through the Telegram bot.

User flow:

1. open a private chat with the bot
2. send `/start`
3. Hermes creates or resolves the account
4. the bot returns the user token

### Does Hermes generate a new token every time?

No.

Current behavior:

- the user receives a stable token
- pressing “Get token” again returns the same token if it already exists

### Where are tokens stored?

In the Hermes database.

### Are plaintext tokens stored?

The project now preserves stable token retrieval for bot UX.
Token handling is implemented in the account/auth layer and backed by migration `V7`.

## Media

### What media types are supported?

Current supported types:

- photos
- documents/files
- videos
- voice messages
- video notes
- media groups/albums

### Why do browser voice/video-note recording require HTTPS?

Because camera and microphone APIs are blocked in insecure contexts.

These work:

- `https://...`
- `http://localhost`

This does not work:

- `http://public-server-ip`

### Where are media files stored?

Depending on your configuration:

- local disk
- Backblaze B2 / S3-compatible storage

### Can I keep media on the server for now?

Yes.

Use:

```bash
APP_MEDIA_STORAGE_PROVIDER=local
APP_MEDIA_STORAGE_ROOT=/var/lib/hermesbridge/media
```

### Why would I use Backblaze B2?

Because it lets you:

- reduce pressure on local disk
- keep media outside your VPS
- use S3-compatible storage
- migrate servers more easily later

### Does Hermes support huge files?

There are multiple limits:

- Spring multipart limits
- `APP_MEDIA_MAX_FILE_SIZE_BYTES`
- Telegram Bot API limits
- browser/device recording behavior

The current default config is conservative:

- `20 MB` file size
- `40 MB` request size

## Web Client / PWA

### Is there a mobile web app?

Yes.

Hermes has:

- mobile web layout
- PWA manifest
- service worker
- installable home-screen mode

### How do I install it on a phone?

Open the HTTPS URL in Safari or Chrome and use “Add to Home Screen”.

### Why is my old UI still visible after deploy?

Because PWA/service-worker caching may still hold old assets.

Fix:

1. fully close the app/tab
2. reopen the latest HTTPS URL
3. refresh once
4. if needed, remove the old home-screen shortcut and add it again

### Why do I still see an outdated tunnel URL?

Because a PWA icon may still point to an older tunnel address.
Delete the old shortcut and add the current HTTPS address again.

## Storage and Database

### What database does Hermes use?

For persistent deployments: PostgreSQL.

For local quick development: H2 is available by default.

### Should I use H2 in production?

No.

Use PostgreSQL for anything real.

### Are migrations automatic?

Yes.

Flyway runs on application startup.

### What if I redeploy and the server restarts?

If you use PostgreSQL and persistent storage:

- users remain
- chats remain
- tokens remain
- messages remain

If you use in-memory H2:

- everything disappears on restart

## Server and Hosting

### Can I deploy Hermes by IP only?

Yes.

That is enough for:

- basic browser access
- internal testing
- Telegram bridge

But you lose:

- proper HTTPS UX
- browser camera/microphone recording
- nicer PWA installation flow

### What server size is enough?

A practical small start:

- `2 vCPU`
- `4 GB RAM`
- `40+ GB SSD`

### Can Hermes share a server with a VPN?

Yes, if you isolate ports and do not touch the VPN stack.

That is exactly how the current deployment flow was handled.

### Do I need Docker?

No.

Hermes already supports a simple `systemd` deployment model.

### Do I need a domain?

Not at first.

You can start with:

- server IP
- ngrok
- a temporary tunnel

But a proper domain is better long-term.

## HTTPS and Tunnels

### Why use ngrok?

Because it provides:

- public HTTPS
- stable free assigned dev domain
- secure browser context

That is enough for:

- camera
- mic
- video-note recording
- PWA installation

### Is the ngrok free domain stable?

Yes, when using the same account/authtoken and assigned dev domain behavior.

### Why not only use Cloudflare quick tunnels?

They are fine for temporary testing, but the URL can change on restart.
ngrok free assigned dev domains are often more convenient for repeated testing.

## Deployment Scripts

### What files are important in `deployment/`?

- `install-vps.sh` — server bootstrap
- `deploy-vps.sh` — build and upload new jar
- `hermesbridge.service` — app service
- `hermesbridge-ngrok.service` — optional HTTPS tunnel
- `hermesbridge-postgres-backup.*` — DB backups
- `hermesbridge.env.example` — environment template

### How do I update Hermes on the server?

From your workstation:

```bash
KEY_PATH=$HOME/.ssh/your_key ./deployment/deploy-vps.sh root@SERVER_IP
```

Then verify:

- `systemctl status hermesbridge`
- `/actuator/health`
- frontend loads
- Telegram bridge still works

## Security

### Where should secrets live?

Never in Git.

Store them in:

- `/etc/hermesbridge/hermes.env`

Recommended permissions:

```bash
chmod 600 /etc/hermesbridge/hermes.env
```

### What secrets matter?

- Telegram bot token
- PostgreSQL password
- B2 access key id
- B2 secret access key
- ngrok authtoken

### Is bearer token auth enough?

For a private deployment, it is a practical start.
For broader public use, you may later want:

- refresh-token model
- session management
- device tracking
- stronger admin controls

## Backup and Recovery

### Is database backup already supported?

Yes.

The repo includes:

- backup script
- systemd service
- systemd timer

### What gets backed up?

- PostgreSQL database dump
- PostgreSQL globals dump

### Does media backup also exist?

Not as a bundled automation yet.

If you use:

- local media: back up `/var/lib/hermesbridge/media`
- B2 media: object storage becomes your main media store

## Frontend / UX

### Why does the UI sometimes change shape after upgrades?

Because Hermes is still evolving quickly and the PWA cache may preserve older assets until refreshed.

### Is the design final?

No.

The UI is already usable, but it is still being iterated actively, especially on mobile.

### Is the Telegram-like look exact?

No, but Hermes can be tuned close to Telegram’s message density and interaction patterns.
The goal is familiar usability, not literal asset-for-asset cloning.

## Troubleshooting

### The bot does not answer

Check:

- `TELEGRAM_ENABLED=true`
- valid bot token
- service logs
- health endpoint

### Telegram media does not arrive

Check:

- file size limits
- Telegram Bot API constraints
- media storage configuration
- backend logs

### Browser says recording is unsupported

Usually because:

- page is not on HTTPS
- browser permissions are blocked
- insecure public IP origin is used

### Messages are not syncing

Check:

- transport binding exists
- bot is in the Telegram group
- privacy mode is disabled
- group chat id is correct

### Old frontend still appears

Close the old tab or PWA, reopen the latest URL, and refresh once.

### I want to move to another server later

Recommended order:

1. export or back up PostgreSQL
2. preserve `/etc/hermesbridge/hermes.env`
3. preserve media or switch to B2 before migration
4. deploy Hermes on the new host
5. restore DB
6. point users to the new HTTPS URL

## Open-Source Self-Hosting Goal

Yes, HermesBridge is already structured toward self-hosting by other people.

The intended path for a new operator is:

1. clone repo
2. read `README`
3. follow `docs/DEPLOY.md`
4. use `deployment/` scripts
5. consult this FAQ for edge cases

That means the repository is being documented not just for one private server, but for reproducible community deployment.
