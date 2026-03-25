# HermesBridge VPS Deployment

Временный production-сценарий для текущего VPS:

- Ubuntu 24.04
- Hermes запускается как `systemd`-сервис
- локальный `Postgres`
- локальное хранилище медиа
- отдельный порт без вмешательства в VPN-контур

## Что не трогаем

- Docker/VPN-контейнеры
- `443/tcp`
- `8588/tcp`
- UDP-порты VPN

## Файлы

- `install-vps.sh` — ставит `OpenJDK 21` и `Postgres`, создает пользователя и каталоги
- `hermesbridge.env.example` — шаблон env-файла
- `hermesbridge.service` — systemd unit
- `hermesbridge-ngrok.service` — optional ngrok HTTPS tunnel
- `hermesbridge-ngrok.sh` — helper script for ngrok
- `deploy-vps.sh` — сборка и выкладка jar по SSH
- `deploy-registration-test.sh` — выкладка test contour на `3003/8082` без затрагивания `3002/8081`
- `hermes-registration-local.env.example` — безопасный шаблон env для локального backend
- `hermes-registration-backend.env.example` — безопасный шаблон env для backend test contour
- `hermesbridge-postgres-backup.sh` — backup локальной базы
- `hermesbridge-postgres-backup.service` — systemd service для backup
- `hermesbridge-postgres-backup.timer` — ежедневный timer для backup

Для передачи работы фронтендеру см.:

- [`docs/FRONTEND_HANDOFF.md`](../docs/FRONTEND_HANDOFF.md)
- [`docs/CODEX_FRONTEND_HANDOFF_PROMPT.md`](../docs/CODEX_FRONTEND_HANDOFF_PROMPT.md)

## Test contour `3003/8082`

Для отдельного test contour на сервере используем:

- backend service: `hermesbridge-registration-backend.service`
- frontend service: `hermesbridge-registration-frontend.service`
- backend port: `8082`
- frontend port: `3003`

С локальной машины запуск:

```bash
KEY_PATH=/path/to/private_key ./deployment/deploy-registration-test.sh root@SERVER_IP
```

Если нужен другой публичный адрес backend для клиентского build:

```bash
KEY_PATH=/path/to/private_key \
NEXT_PUBLIC_API_BASE_URL=http://SERVER_IP:8082 \
./deployment/deploy-registration-test.sh root@SERVER_IP
```

Скрипт:

- собирает backend jar
- собирает frontend standalone runtime
- заливает только jar и frontend runtime tarball
- перезапускает только `3003/8082`
- не трогает `3002/8081`, nginx и production domain

## Базовый порядок

1. На сервере:
   - запустить `install-vps.sh`
2. На сервере:
   - скопировать `hermesbridge.env.example` в `/etc/hermesbridge/hermes.env`
   - заполнить `APP_DATASOURCE_PASSWORD` и Telegram-секреты
3. На сервере:
   - положить `hermesbridge.service` в `/etc/systemd/system/`
4. С локальной машины:
   - запустить `deploy-vps.sh root@SERVER_IP`
5. На сервере:
   - проверить `systemctl status hermesbridge`
   - открыть `http://SERVER_IP:8080`

## Временный HTTPS через ngrok

Если нужен стабильный dev-domain на `ngrok`, добавляем:

- `/usr/local/bin/hermesbridge-ngrok.sh`
- `/etc/systemd/system/hermesbridge-ngrok.service`
- `/etc/hermesbridge/ngrok.env`

Пример `/etc/hermesbridge/ngrok.env`:

```bash
NGROK_AUTHTOKEN=replace_me
NGROK_URL=
```

Для free-плана `NGROK_URL` можно оставить пустым: agent возьмет assigned dev domain,
привязанный к authtoken. Если у тебя есть конкретный reserved/custom domain, впиши его туда.

## PostgreSQL backup

Локальная база на VPS должна регулярно бэкапиться.

Что ставим:

- `/usr/local/bin/hermesbridge-postgres-backup.sh`
- `/etc/systemd/system/hermesbridge-postgres-backup.service`
- `/etc/systemd/system/hermesbridge-postgres-backup.timer`

Поведение:

- backup каждый день в `02:30 UTC`
- хранение последних `7` дней
- формат дампа:
  - `${DB_NAME}.dump` — custom dump для `pg_restore`
  - `globals.sql` — роли и глобальные объекты

Проверка:

```bash
systemctl start hermesbridge-postgres-backup.service
systemctl status hermesbridge-postgres-backup.service
systemctl list-timers hermesbridge-postgres-backup.timer
ls -lah /var/backups/hermesbridge/postgres
```

Восстановление:

```bash
runuser -u postgres -- psql -c "DROP DATABASE hermesbridge;"
runuser -u postgres -- createdb -O hermesbridge hermesbridge
runuser -u postgres -- psql -f /path/to/globals.sql
runuser -u postgres -- pg_restore --clean --if-exists --no-owner --dbname=hermesbridge /path/to/hermesbridge.dump
```
