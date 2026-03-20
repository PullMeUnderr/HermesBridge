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
- `hermesbridge-next.service` — production Next.js frontend unit
- `hermesbridge-next-test.service` — отдельный test-инстанс Next.js
- `hermesbridge-ngrok.service` — optional ngrok HTTPS tunnel
- `hermesbridge-ngrok.sh` — helper script for ngrok
- `deploy-vps.sh` — сборка и выкладка jar + Next standalone по SSH
- `hermesbridge-postgres-backup.sh` — backup локальной базы
- `hermesbridge-postgres-backup.service` — systemd service для backup
- `hermesbridge-postgres-backup.timer` — ежедневный timer для backup

## Базовый порядок

1. На сервере:
   - запустить `install-vps.sh`
2. На сервере:
   - скопировать `hermesbridge.env.example` в `/etc/hermesbridge/hermes.env`
   - заполнить `APP_DATASOURCE_PASSWORD` и Telegram-секреты
3. На сервере:
   - положить `hermesbridge.service` в `/etc/systemd/system/`
   - положить `hermesbridge-next.service` в `/etc/systemd/system/`
4. С локальной машины:
   - запустить `deploy-vps.sh root@SERVER_IP`
5. На сервере:
   - проверить `systemctl status hermesbridge`
   - проверить `systemctl status hermesbridge-next`
   - открыть фронт на `http://SERVER_IP:3000` или через reverse proxy на домене

## Frontend Production Model

Новый production frontend больше не должен браться из legacy-папки `src/main/resources/static`.

Текущая схема для ветки `next-js` и будущего `main`:

- Spring Boot backend работает как раньше
- Next.js frontend собирается из `frontend/`
- deploy-script отправляет standalone runtime в `/opt/hermesbridge-frontend/current`
- `hermesbridge-next.service` поднимает `server.js` на `3000/tcp`
- frontend ходит в backend через `BACKEND_ORIGIN=http://127.0.0.1:8080`

То есть legacy static UI остаётся в репозитории только как fallback/история, но не как production entrypoint.

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
