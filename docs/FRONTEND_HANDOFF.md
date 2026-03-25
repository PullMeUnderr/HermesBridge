# Frontend Handoff

Этот документ нужен для передачи проекта фронтендеру, который будет работать с текущим test contour и локальной разработкой.

## Что важно не трогать

- Не трогать production contour.
- Production frontend: `3002`
- Production backend: `8081`
- Не трогать `nginx`, боевой домен и ветку `main`.

## Актуальный test contour

- Server: `193.163.170.8`
- Test frontend: `3003`
- Test backend: `8082`
- Backend service: `hermesbridge-registration-backend.service`
- Frontend service: `hermesbridge-registration-frontend.service`
- Backend env: `/etc/hermesbridge/hermes-registration-backend.env`
- Backend logs:
  - `/var/log/hermesbridge-registration/backend.log`
  - `/var/log/hermesbridge-registration/backend-error.log`

## Быстрый обзор текущей архитектуры

- Backend: Spring Boot + PostgreSQL + Flyway + TDLight + Telegram Bot API
- Frontend: Next.js standalone build
- Auth: `access token` + `refresh cookie`
- Realtime: WebSocket/STOMP endpoint `/ws`
- Telegram account login для UI/каналов: TDLight QR login
- Импорт Telegram channels: отдельный TDLight channel subscription flow

## Локальный запуск

### 1. Backend локально

Подготовить env по шаблону:

- [deployment/hermes-registration-local.env.example](/Users/vladislav/Projects/HermesBridge-clean/deployment/hermes-registration-local.env.example)

Пример запуска:

```bash
cd /Users/vladislav/Projects/HermesBridge-clean
set -a
source deployment/hermes-registration-local.env.example
set +a
./mvnw spring-boot:run
```

Если нужен именно локальный TDLight real mode, надо заполнить реальные значения:

- `APP_TDLIGHT_API_ID`
- `APP_TDLIGHT_API_HASH`
- `APP_TDLIGHT_SESSION_ENCRYPTION_KEY`

И убедиться, что есть рабочий PostgreSQL.

Health-check:

```bash
curl http://127.0.0.1:8082/actuator/health
```

### 2. Frontend локально

Подготовить env по шаблону:

- [frontend/frontend.env.local.example](/Users/vladislav/Projects/HermesBridge-clean/frontend/frontend.env.local.example)

Запуск:

```bash
cd /Users/vladislav/Projects/HermesBridge-clean/frontend
npm install
cp frontend.env.local.example .env.local
npm run dev -- --hostname 0.0.0.0 --port 3003
```

Открыть:

- Frontend: [http://127.0.0.1:3003](http://127.0.0.1:3003)
- Backend: [http://127.0.0.1:8082](http://127.0.0.1:8082)

Примечание:

- `frontend/src/lib/api.ts` уже умеет работать с `3003 -> 8082`.
- Все auth-запросы идут с `credentials: include`.
- Session model: access token в `localStorage`, refresh token в `HttpOnly` cookie.

## Деплой на test contour

Использовать только:

- [deployment/deploy-registration-test.sh](/Users/vladislav/Projects/HermesBridge-clean/deployment/deploy-registration-test.sh)

Команда:

```bash
cd /Users/vladislav/Projects/HermesBridge-clean
PATH=/Users/vladislav/.local/node-v20.18.3-darwin-arm64/bin:$PATH \
KEY_PATH=/Users/vladislav/.ssh/afina_bot \
./deployment/deploy-registration-test.sh root@193.163.170.8
```

Что делает скрипт:

- собирает backend jar c профилем `tdlight-native-linux-amd64-gnu-ssl1`
- собирает frontend standalone runtime
- заливает backend в `/opt/hermesbridge-registration/backend/hermesbridge.jar`
- заливает frontend в `/opt/hermesbridge-registration/frontend-runtime`
- перезапускает только test services
- проверяет `8082/actuator/health`
- проверяет `3003`

## Проверки после деплоя

На сервере:

```bash
ssh -i /Users/vladislav/.ssh/afina_bot root@193.163.170.8 'curl -s http://127.0.0.1:8082/actuator/health && echo && systemctl is-active hermesbridge-registration-backend.service && systemctl is-active hermesbridge-registration-frontend.service'
```

Снаружи:

```bash
curl -I http://193.163.170.8:3003/
```

## Полезные файлы для фронтендера

- [frontend/src/components/AppShell/HermesClient.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/AppShell/HermesClient.tsx)
- [frontend/src/components/DrawerPanel/DrawerPanel.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/DrawerPanel/DrawerPanel.tsx)
- [frontend/src/components/Sidebar/Sidebar.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/Sidebar/Sidebar.tsx)
- [frontend/src/components/MessagesList/MessagesList.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/MessagesList/MessagesList.tsx)
- [frontend/src/components/MessageComposer/MessageComposer.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/MessageComposer/MessageComposer.tsx)
- [frontend/src/lib/api.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/api.ts)
- [frontend/src/lib/ws.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/ws.ts)
- [frontend/src/lib/protectedMediaCache.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/protectedMediaCache.ts)
- [frontend/src/lib/channelMessageCache.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/channelMessageCache.ts)
- [src/main/java/com/vladislav/tgclone/security/AuthController.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/security/AuthController.java)
- [src/main/java/com/vladislav/tgclone/config/WebSocketConfig.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/WebSocketConfig.java)
- [src/main/java/com/vladislav/tgclone/config/StompAuthenticationChannelInterceptor.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/StompAuthenticationChannelInterceptor.java)
- [src/main/java/com/vladislav/tgclone/config/tdlight/TdlightModuleConfiguration.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/tdlight/TdlightModuleConfiguration.java)
- [src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSubscriptionService.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSubscriptionService.java)
- [src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSyncCoordinator.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSyncCoordinator.java)

## Текущее рабочее поведение

- Hermes login работает через `register/login/session/refresh/logout`.
- Web client держит access token локально и умеет его обновлять через refresh cookie.
- WebSocket обновляет сообщения, summary, typing и read state.
- QR login через TDLight работает в profile drawer.
- После TDLight login можно загружать список доступных Telegram channels и подключать их в Hermes.
- Подключённые channels синкаются в Hermes scheduler-ом.

## Что проверить в первую очередь после любого фронтового изменения

1. Hermes login и refresh session.
2. Список чатов и восстановление выбранного `conversationId` после reload.
3. WebSocket после открытия чата.
4. Отправку текста и медиа из Hermes.
5. QR login в Drawer.
6. Список доступных TDLight channels.
7. Подключение и отключение channel.
8. Импорт новых постов из подключённого channel.

## Важные ограничения

- Не читать и не коммитить реальные секреты из `/etc/hermesbridge/hermes-registration-backend.env`.
- Не деплоить в production contour.
- Не редактировать `nginx` ради test contour.
