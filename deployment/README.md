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
- `deploy-vps.sh` — сборка и выкладка jar по SSH

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
