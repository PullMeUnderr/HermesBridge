# Prompt для Codex коллеги

```text
Работаем только в проекте /Users/vladislav/Projects/HermesBridge-clean, ветка codex/server-ready-baseline.

Важно:
- не трогать production contour
- production frontend: 3002
- production backend: 8081
- не трогать nginx, боевой домен и main
- работать только с test contour на сервере 193.163.170.8
- test frontend: 3003
- test backend: 8082

Что уже есть:
- test contour уже разворачивается скриптом deployment/deploy-registration-test.sh
- backend env test contour лежит на сервере в /etc/hermesbridge/hermes-registration-backend.env
- backend logs:
  - /var/log/hermesbridge-registration/backend.log
  - /var/log/hermesbridge-registration/backend-error.log
- systemd services:
  - hermesbridge-registration-backend.service
  - hermesbridge-registration-frontend.service

Как деплоить test contour:
cd /Users/vladislav/Projects/HermesBridge-clean
PATH=/Users/vladislav/.local/node-v20.18.3-darwin-arm64/bin:$PATH KEY_PATH=/Users/vladislav/.ssh/afina_bot ./deployment/deploy-registration-test.sh root@193.163.170.8

Что надо понимать в проекте:
- frontend: Next.js app в frontend/
- backend: Spring Boot в src/main/java
- auth: access token + refresh cookie
- realtime: websocket/stomp endpoint /ws
- TDLight: QR login и импорт Telegram channels через personal Telegram account
- Telegram Bot bridge: Hermes <-> Telegram messages/media

Что сначала изучить:
- docs/FRONTEND_HANDOFF.md
- docs/WHAT_CHANGED_SINCE_OLD_VERSION.md
- frontend/src/components/AppShell/HermesClient.tsx
- frontend/src/components/DrawerPanel/DrawerPanel.tsx
- frontend/src/lib/api.ts
- frontend/src/lib/ws.ts
- frontend/src/lib/protectedMediaCache.ts
- src/main/java/com/vladislav/tgclone/security/AuthController.java
- src/main/java/com/vladislav/tgclone/config/WebSocketConfig.java
- src/main/java/com/vladislav/tgclone/config/StompAuthenticationChannelInterceptor.java
- src/main/java/com/vladislav/tgclone/config/tdlight/TdlightModuleConfiguration.java
- src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSubscriptionService.java

Правила работы:
- не выкатывать ничего на 3002/8081
- не менять nginx
- не использовать main
- не выводить пользователю секреты из server env
- если надо проверить backend состояние, используй только test contour

Если задача про фронт, сначала проверь:
1. как HermesClient восстанавливает сессию
2. как api.ts выбирает base URL
3. что делает refreshAccessToken() и /api/auth/refresh
4. как ws.ts поднимает websocket
5. как DrawerPanel работает с /api/tdlight/*
6. как protectedMediaCache и channelMessageCache влияют на UI

Если задача про деплой:
1. изучи deployment/deploy-registration-test.sh
2. убедись, что меняешь только test services
3. после деплоя проверь:
   - http://193.163.170.8:3003/ -> 200
   - http://127.0.0.1:8082/actuator/health на сервере -> UP

Если задача про регрессию:
- сначала воспроизведи на test contour
- потом посмотри backend.log
- потом проверь соответствующий frontend flow
- только потом меняй код
```
