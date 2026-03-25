# What Changed Since Old Version

Ниже краткое сравнение текущего состояния проекта с ранней версией репозитория.

Для ориентира:

- ранняя база: `14e9402 initial commit`
- важные вехи:
  - `5927305 Add protected media cache`
  - `680cb9f New front/new back`
  - `ed4143a Add realtime chat updates and recorder polish`
  - `8d6ea6a Add Telegram-first Hermes registration completion flow`
  - `64f21ec Add TDLight real baseline and import lab`

По `git diff --stat 14e9402..HEAD` в репозитории добавлено больше `14k` строк и сильно расширены backend и frontend.

## Что добавили нового

### 1. Next.js frontend как основной web client

Раньше проект был заметно проще по web-слою. Сейчас:

- основной frontend живёт в `frontend/`
- используется standalone build
- есть отдельный deploy frontend runtime на test contour
- появились lab-страницы для shell/sidebar/TDLight flows

### 2. Access + Refresh auth session model

Раньше auth был ближе к “долгоживущий bearer token”. Сейчас:

- backend выдаёт access token и refresh token
- refresh token хранится в cookie
- frontend умеет автоматически вызывать `/api/auth/refresh`
- появились login/register/session/logout flows для Hermes account
- session можно восстанавливать после reload

Ключевые файлы:

- [src/main/java/com/vladislav/tgclone/security/AuthController.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/security/AuthController.java)
- [src/main/java/com/vladislav/tgclone/account/ApiTokenService.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/account/ApiTokenService.java)
- [frontend/src/lib/api.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/api.ts)
- [frontend/src/components/AppShell/HermesClient.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/AppShell/HermesClient.tsx)

### 3. Realtime через WebSocket/STOMP

Добавлен realtime слой поверх обычного polling:

- endpoint `/ws`
- realtime messages
- realtime conversation summary updates
- typing indicators
- read events

Ключевые файлы:

- [src/main/java/com/vladislav/tgclone/config/WebSocketConfig.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/WebSocketConfig.java)
- [src/main/java/com/vladislav/tgclone/config/StompAuthenticationChannelInterceptor.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/StompAuthenticationChannelInterceptor.java)
- [src/main/java/com/vladislav/tgclone/conversation/ConversationSocketController.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/conversation/ConversationSocketController.java)
- [frontend/src/lib/ws.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/ws.ts)

### 4. TDLight real mode и QR login

Это один из самых больших новых блоков. Добавлены:

- TDLight runtime abstraction
- real TDLight mode
- session persistence
- account binding между Hermes user и TDLight connection
- QR login прямо из профиля
- TDLight diagnostics и dev endpoints

Ключевые файлы:

- [src/main/java/com/vladislav/tgclone/config/tdlight/TdlightModuleConfiguration.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/tdlight/TdlightModuleConfiguration.java)
- [src/main/java/com/vladislav/tgclone/config/tdlight/TdlightRealConfiguration.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/config/tdlight/TdlightRealConfiguration.java)
- [src/main/java/com/vladislav/tgclone/tdlight/migration/TdlightQrAuthorizationService.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/TdlightQrAuthorizationService.java)
- [src/main/java/com/vladislav/tgclone/tdlight/connection/TdlightAccountBindingService.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/connection/TdlightAccountBindingService.java)
- [frontend/src/components/DrawerPanel/DrawerPanel.tsx](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/components/DrawerPanel/DrawerPanel.tsx)

### 5. Telegram channel import и TDLight channel subscriptions

Это уже не просто bridge через bot API, а отдельный personal Telegram account flow:

- список доступных public channels из TDLight
- подключение channel в Hermes
- хранение `tdlight_channel_subscriptions`
- scheduler, который синкает новые посты
- импорт media и текста из channel posts

Ключевые файлы:

- [src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSubscriptionService.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSubscriptionService.java)
- [src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSyncCoordinator.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/DefaultTdlightChannelSyncCoordinator.java)
- [src/main/java/com/vladislav/tgclone/tdlight/migration/TdlightAvailableChannelSummary.java](/Users/vladislav/Projects/HermesBridge-clean/src/main/java/com/vladislav/tgclone/tdlight/migration/TdlightAvailableChannelSummary.java)
- [src/main/resources/db/migration/V15__tdlight_channel_subscriptions.sql](/Users/vladislav/Projects/HermesBridge-clean/src/main/resources/db/migration/V15__tdlight_channel_subscriptions.sql)

### 6. Media cache и локальный cache messages

Появились client-side кэши:

- protected media cache
- локальный cache сообщений channel/chat
- статистика cache и TTL UI

Ключевые файлы:

- [frontend/src/lib/protectedMediaCache.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/protectedMediaCache.ts)
- [frontend/src/lib/channelMessageCache.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/channelMessageCache.ts)
- [frontend/src/lib/cacheIdentity.ts](/Users/vladislav/Projects/HermesBridge-clean/frontend/src/lib/cacheIdentity.ts)

### 7. Улучшенный Hermes <-> Telegram bridge

Сильно расширен bridge:

- ответы и reply context
- media delivery
- видео, voice, video notes
- аватарки и summary
- более богатая conversation sidebar

## Краткий итог

Если очень коротко, по сравнению со старой версией проект стал:

- с полноценным Next.js frontend
- с session auth через access/refresh
- с realtime websocket-обновлениями
- с TDLight QR login
- с личным Telegram account flow для channel import
- с отдельным test contour deploy-потоком
