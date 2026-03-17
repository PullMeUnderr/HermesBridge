# TGClone Bridge

Java backend для собственного мессенджера с мостом в Telegram.

## Насколько идея реализуема

Да, это реализуемо, но с несколькими важными ограничениями Telegram Bot API:

- Бот должен быть добавлен в чат, который мы хотим синхронизировать.
- Для групп нужно отключить `privacy mode` через `@BotFather`, иначе бот не увидит обычные сообщения.
- Бот не может читать чужие личные переписки между двумя пользователями Telegram, если он не участник диалога.
- В текущем MVP синхронизируются текстовые сообщения. Файлы, реакции, редактирование и удаление сообщений можно добавить следующим этапом.

## Что уже заложено

- `Conversation` как внутренняя сущность чата.
- `TransportBinding` как привязка внутреннего чата к внешнему транспорту.
- `MessageRelayService` как единая точка синхронизации и дедупликации.
- WebSocket-канал для realtime-доставки сообщений в твой UI.
- Telegram polling через обычный Bot API без тяжелых SDK.
- Подготовка под multi-tenant сценарий через `tenantKey`.
- Регистрация аккаунта через Telegram-бота и bearer-аутентификация в API.
- Membership-модель чатов с ролями `OWNER`/`ADMIN`/`MEMBER`.
- Одноразовые инвайты для вступления в чат через API или через Telegram-бота.
- Bot-first управление чатами: создание чата, invite и binding группы без терминала.
- Кнопочное меню в личке и inline-кнопки для непривязанных Telegram-групп.

## Быстрый старт

### 1. Переменные окружения

```bash
export TELEGRAM_ENABLED=true
export TELEGRAM_BOT_TOKEN=123456:token
export TELEGRAM_BOT_USERNAME=my_bridge_bot
export APP_DEFAULT_TENANT_KEY=main
```

По умолчанию приложение стартует на in-memory H2. Для production лучше сразу переключить на Postgres:

```bash
export APP_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tgclone
export APP_DATASOURCE_USERNAME=postgres
export APP_DATASOURCE_PASSWORD=postgres
```

Для локального хранения медиа этого достаточно. Если захочешь вынести медиа в Backblaze B2, задай:

```bash
export APP_MEDIA_STORAGE_PROVIDER=backblaze-b2
export APP_MEDIA_S3_ENDPOINT=https://s3.eu-central-003.backblazeb2.com
export APP_MEDIA_S3_BUCKET=hermesbridge-media
export APP_MEDIA_S3_ACCESS_KEY_ID=...
export APP_MEDIA_S3_SECRET_ACCESS_KEY=...
export APP_MEDIA_S3_REGION=eu-central-003
```

`APP_MEDIA_S3_REGION` можно не задавать, если endpoint уже указывает регион в формате `s3.<region>.backblazeb2.com`.

### 2. Запуск

```bash
./mvnw spring-boot:run
```

## Временный deploy на VPS

Если нужно быстро поднять Hermes на собственном VPS рядом с уже работающим VPN-контуром:

- используем `systemd`, а не Docker
- держим `Postgres` локально на сервере
- медиа складываем локально на диск сервера
- не трогаем `443/tcp` и другие VPN-порты

Готовые файлы лежат в:

- `deployment/install-vps.sh`
- `deployment/hermesbridge.env.example`
- `deployment/hermesbridge.service`
- `deployment/deploy-vps.sh`
- `deployment/README.md`
- `deployment/hermesbridge-postgres-backup.sh`
- `deployment/hermesbridge-postgres-backup.service`
- `deployment/hermesbridge-postgres-backup.timer`

### 3. Зарегистрироваться через Telegram-бота

Открой личку с ботом и отправь:

```text
/start
```

Бот создаст аккаунт в новом мессенджере и пришлет bearer token.

### 4. Создать и привязать чат без терминала

После `/start` бот показывает постоянное меню в личке:

- `Создать чат`
- `Вход`
- `Мои чаты`
- `Создать инвайт`
- `Получить токен`
- `Инструкция`
- `Отмена`

Основной flow теперь такой:

1. В личке нажми `Создать чат`.
2. Если тебе прислали invite-код, нажми `Вход` и просто вставь код без команды `/join`.
3. Нажми `Мои чаты`, чтобы увидеть список и `ID`.
4. Нажми `Создать инвайт`, чтобы выбрать чат кнопкой и получить invite-код.
5. Нажми `Инструкция`, чтобы бот прислал пошаговый гайд.

### 5. Подключить Telegram-группу тоже можно кнопками

Если бот добавлен в группу и она еще не подключена, он сам присылает onboarding-сообщение с inline-кнопками:

- `Создать чат для группы`
- `Привязать к существующему`
- `Инструкция`

То есть типичный сценарий такой:

1. Добавь бота в группу.
2. Напиши в группе любое сообщение.
3. Бот покажет кнопки подключения.
4. Выбери:
   - создать новый чат прямо для этой группы;
   - или привязать группу к уже существующему чату.

### 6. Если нужен прямой доступ к API

Дальше для запросов в API:

```bash
export API_TOKEN='tgc_...'
```

### 7. Простой web UI для теста

После запуска backend открой в браузере:

```text
http://localhost:8080
```

Там есть минимальный клиент для тестов:

- вход по bearer token
- создание чата
- вход по invite-коду
- список чатов
- чтение сообщений
- отправка сообщений
- просмотр участников
- создание invite кнопкой

Это пока не финальный дизайн, а тестовый интерфейс, чтобы пользоваться Hermes уже не через `curl`.

### 8. Проверить свой аккаунт

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $API_TOKEN"
```

### 9. Создать внутренний чат

```bash
curl -X POST http://localhost:8080/api/conversations \
  -H "Authorization: Bearer $API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Main Chat"}'
```

### 10. Привязать Telegram-чат

Важно: создавать и менять bridge binding сейчас может только `OWNER` чата.

```bash
curl -X POST http://localhost:8080/api/bindings \
  -H "Authorization: Bearer $API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":1,"transport":"TELEGRAM","externalChatId":"-1001234567890"}'
```

### 11. Создать инвайт для второго участника

```bash
curl -X POST http://localhost:8080/api/conversations/1/invites \
  -H "Authorization: Bearer $API_TOKEN"
```

В ответе придет `inviteCode`, например `join_xxxxx`.

### 12. Второй пользователь вступает через Telegram-бота

Сначала он пишет боту:

```text
/start
```

Потом:

```text
/join join_xxxxx
```

После этого чат появится у него в доступе как `MEMBER`.

Либо он может в личке у бота нажать кнопку `Вход` и просто вставить код.

### 13. Владелец может повысить участника до администратора

```bash
curl -X PATCH http://localhost:8080/api/conversations/1/members/2 \
  -H "Authorization: Bearer $API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"ADMIN"}'
```

`OWNER` может менять роли участников между `MEMBER` и `ADMIN`.
`ADMIN` после этого может создавать инвайты и управлять Telegram binding.

То же самое можно сделать через API:

```bash
curl -X POST http://localhost:8080/api/invites/join_xxxxx/accept \
  -H "Authorization: Bearer $SECOND_USER_API_TOKEN"
```

### 14. Отправить сообщение из внутреннего API

```bash
curl -X POST http://localhost:8080/api/conversations/1/messages \
  -H "Authorization: Bearer $API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"body":"Привет из своего мессенджера"}'
```

Если бот есть в Telegram-чате и имеет доступ к сообщениям, они пойдут в обе стороны.

Если пользователь Telegram заранее зарегистрировался у бота в личке, его сообщения в bridge тоже будут маппиться на тот же аккаунт мессенджера.

## WebSocket

- Endpoint для подключения SockJS: `/ws`
- Topic для сообщений чата: `/topic/conversations/{conversationId}`

## Команды бота

- Основной UX теперь кнопочный: команды ниже остаются как fallback и для отладки.
- `/start` - создать аккаунт или перевыпустить токен
- `/register` - то же самое
- `/token` - получить новый токен
- `/newchat TITLE` - создать внутренний чат
- `/join CODE` - вступить в чат по инвайту
- `/mychats` - показать свои чаты
- `/invite ID` - создать invite для чата
- `/registerchat [TITLE]` - в группе создать чат и сразу привязать эту группу
- `/bind ID` - в группе привязать эту группу к существующему чату

## Что логично делать следующим этапом

- Сохранение данных в Postgres вместо in-memory H2.
- Файлы, reply/thread, edits, deletes.
- Удаление участников, leave flow и роли уровня `ADMIN`.
- Улучшение дизайна и UX у тестового web UI.
- Outbox/queue для надежной доставки.
- Горизонтальное масштабирование polling/websocket слоя.
- Подключение других транспортов по тому же контракту `DeliveryGateway`.
