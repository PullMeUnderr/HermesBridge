package com.vladislav.tgclone.telegram;

import com.vladislav.tgclone.account.TelegramRegistrationResult;
import com.vladislav.tgclone.account.TelegramRegistrationService;
import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentKind;
import com.vladislav.tgclone.bridge.MessageRelayService;
import com.vladislav.tgclone.bridge.TelegramInboundEnvelope;
import com.vladislav.tgclone.bridge.TransportBinding;
import com.vladislav.tgclone.bridge.TransportBindingService;
import com.vladislav.tgclone.common.ForbiddenException;
import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.conversation.ConversationInviteAcceptanceResult;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.conversation.IssuedConversationInvite;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelegramPollingService {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);
    private static final String CURSOR_KEY = "telegram-updates";
    private static final String BUTTON_CREATE_CHAT = "Создать чат";
    private static final String BUTTON_JOIN = "Вход";
    private static final String BUTTON_MY_CHATS = "Мои чаты";
    private static final String BUTTON_CREATE_INVITE = "Создать инвайт";
    private static final String BUTTON_REFRESH_TOKEN = "Получить токен";
    private static final String BUTTON_INSTRUCTIONS = "Инструкция";
    private static final String BUTTON_CANCEL = "Отмена";
    private static final String CALLBACK_PRIVATE_INVITE_PREFIX = "private:invite:";
    private static final String CALLBACK_GROUP_CREATE = "group:create";
    private static final String CALLBACK_GROUP_BIND_LIST = "group:bind:list";
    private static final String CALLBACK_GROUP_BIND_PREFIX = "group:bind:";
    private static final String CALLBACK_GROUP_HELP = "group:help";
    private static final long GROUP_HINT_INTERVAL_SECONDS = 600;

    private final TelegramProperties telegramProperties;
    private final TelegramBotClient telegramBotClient;
    private final SyncCursorRepository syncCursorRepository;
    private final MessageRelayService messageRelayService;
    private final TelegramRegistrationService telegramRegistrationService;
    private final UserAccountService userAccountService;
    private final ConversationService conversationService;
    private final TransportBindingService transportBindingService;
    private final TelegramPrivateDialogStateService dialogStateService;
    private final Map<String, Instant> groupHintSentAt = new ConcurrentHashMap<>();

    public TelegramPollingService(
        TelegramProperties telegramProperties,
        TelegramBotClient telegramBotClient,
        SyncCursorRepository syncCursorRepository,
        MessageRelayService messageRelayService,
        TelegramRegistrationService telegramRegistrationService,
        UserAccountService userAccountService,
        ConversationService conversationService,
        TransportBindingService transportBindingService,
        TelegramPrivateDialogStateService dialogStateService
    ) {
        this.telegramProperties = telegramProperties;
        this.telegramBotClient = telegramBotClient;
        this.syncCursorRepository = syncCursorRepository;
        this.messageRelayService = messageRelayService;
        this.telegramRegistrationService = telegramRegistrationService;
        this.userAccountService = userAccountService;
        this.conversationService = conversationService;
        this.transportBindingService = transportBindingService;
        this.dialogStateService = dialogStateService;
    }

    @Scheduled(fixedDelayString = "${telegram.scheduler-fixed-delay-ms:5000}")
    public void poll() {
        if (!telegramProperties.enabled()) {
            return;
        }

        long nextOffset = syncCursorRepository.findById(CURSOR_KEY)
            .map(SyncCursor::getNextOffset)
            .orElse(0L);

        try {
            List<TelegramUpdateDto> updates = telegramBotClient.fetchUpdates(nextOffset);
            long updatedOffset = nextOffset;

            for (TelegramUpdateDto update : updates) {
                updatedOffset = Math.max(updatedOffset, update.updateId() + 1);
                try {
                    processUpdate(update);
                } catch (Exception ex) {
                    log.warn("Telegram update {} failed: {}", update.updateId(), ex.getMessage());
                }
            }

            if (updatedOffset != nextOffset) {
                syncCursorRepository.save(new SyncCursor(CURSOR_KEY, updatedOffset));
            }
        } catch (Exception ex) {
            log.warn("Telegram polling failed: {}", ex.getMessage());
        }
    }

    private void processUpdate(TelegramUpdateDto update) {
        if (update.callbackQuery() != null) {
            handleCallbackQuery(update.callbackQuery());
            return;
        }
        if (update.message() == null || update.message().chat() == null) {
            return;
        }

        if ("private".equalsIgnoreCase(update.message().chat().type())) {
            if (update.message().text() == null || update.message().text().isBlank()) {
                return;
            }
            handlePrivateChat(update.message());
            return;
        }

        if (!hasSupportedGroupPayload(update.message())) {
            return;
        }

        handleGroupMessage(update.message());
    }

    private void handlePrivateChat(TelegramMessageDto message) {
        TelegramUserDto telegramUser = message.from();
        String privateChatId = String.valueOf(message.chat().id());
        if (telegramUser == null || telegramUser.id() == null) {
            sendPrivateMessage(privateChatId, "Не удалось определить твой Telegram аккаунт.");
            return;
        }

        String text = normalizeText(message.text());
        if (handlePrivateButtonAction(text, telegramUser, privateChatId)) {
            return;
        }

        String command = normalizeCommand(text);
        switch (command) {
            case "/start", "/register", "/token", "/login" -> handleRegistrationCommand(telegramUser, privateChatId);
            case "/newchat" -> {
                dialogStateService.beginCreateChat(privateChatId);
                sendPrivateMessage(privateChatId, "Пришли название нового чата.");
            }
            case "/mychats" -> handleMyChats(telegramUser, privateChatId);
            case "/invite" -> handleInviteCommand(text, telegramUser, privateChatId);
            case "/join" -> handleJoinCommand(text, telegramUser, privateChatId);
            case "/help", "/menu", "/me" -> sendPrivateMessage(privateChatId, buildInstructionMessage());
            default -> {
                if (handlePendingPrivateInput(text, telegramUser, privateChatId)) {
                    return;
                }
                sendPrivateMessage(privateChatId, "Выбери действие кнопками ниже.");
            }
        }
    }

    private void handleGroupMessage(TelegramMessageDto message) {
        if (isCommand(message.text())) {
            handleLegacyGroupCommand(message);
            return;
        }

        TelegramUserDto telegramUser = message.from();
        TelegramInboundEnvelope envelope = new TelegramInboundEnvelope(
            String.valueOf(message.chat().id()),
            String.valueOf(message.messageId()),
            telegramUser == null || telegramUser.id() == null ? null : String.valueOf(telegramUser.id()),
            telegramUser == null ? "Telegram User" : telegramUser.displayName(),
            extractInboundBody(message),
            message.sentAt(),
            extractInboundAttachments(message)
        );

        try {
            messageRelayService.processTelegramInbound(envelope);
        } catch (NotFoundException ex) {
            log.info("Telegram chat {} is not bound yet", envelope.externalChatId());
            maybeSendGroupOnboarding(message);
        }
    }

    private void handleLegacyGroupCommand(TelegramMessageDto message) {
        TelegramUserDto telegramUser = message.from();
        String groupChatId = String.valueOf(message.chat().id());
        if (telegramUser == null || telegramUser.id() == null) {
            telegramBotClient.sendMessage(groupChatId, "Не удалось определить Telegram пользователя.");
            return;
        }

        String command = normalizeCommand(message.text());
        switch (command) {
            case "/registerchat" -> handleRegisterGroupChat(message.chat(), telegramUser, groupChatId);
            case "/bind" -> handleBindGroupCommand(message.text(), telegramUser, groupChatId);
            case "/help", "/start", "/menu" -> sendGroupOnboarding(groupChatId);
            default -> sendGroupOnboarding(groupChatId);
        }
    }

    private void handleCallbackQuery(TelegramCallbackQueryDto callbackQuery) {
        try {
            if (callbackQuery.message() == null || callbackQuery.message().chat() == null) {
                return;
            }
            if ("private".equalsIgnoreCase(callbackQuery.message().chat().type())) {
                handlePrivateCallbackQuery(callbackQuery);
            } else {
                handleGroupCallbackQuery(callbackQuery);
            }
        } finally {
            telegramBotClient.answerCallbackQuery(callbackQuery.id());
        }
    }

    private void handlePrivateCallbackQuery(TelegramCallbackQueryDto callbackQuery) {
        String data = callbackQuery.data();
        String privateChatId = String.valueOf(callbackQuery.message().chat().id());
        TelegramUserDto telegramUser = callbackQuery.from();
        if (data == null || telegramUser == null || telegramUser.id() == null) {
            return;
        }

        if (data.startsWith(CALLBACK_PRIVATE_INVITE_PREFIX)) {
            UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
            if (userAccount == null) {
                return;
            }

            Long conversationId = parseLong(data.substring(CALLBACK_PRIVATE_INVITE_PREFIX.length()));
            if (conversationId == null) {
                sendPrivateMessage(privateChatId, "Не удалось определить чат для invite.");
                return;
            }

            try {
                IssuedConversationInvite invite = conversationService.createInvite(
                    AuthenticatedUser.from(userAccount),
                    conversationId
                );
                sendPrivateMessage(privateChatId, buildInviteMessage(invite));
            } catch (NotFoundException | IllegalArgumentException | ForbiddenException ex) {
                sendPrivateMessage(privateChatId, ex.getMessage());
            }
        }
    }

    private void handleGroupCallbackQuery(TelegramCallbackQueryDto callbackQuery) {
        String data = callbackQuery.data();
        String groupChatId = String.valueOf(callbackQuery.message().chat().id());
        TelegramUserDto telegramUser = callbackQuery.from();
        if (data == null || telegramUser == null || telegramUser.id() == null) {
            return;
        }

        switch (data) {
            case CALLBACK_GROUP_CREATE -> handleRegisterGroupChat(callbackQuery.message().chat(), telegramUser, groupChatId);
            case CALLBACK_GROUP_BIND_LIST -> handleGroupBindChooser(telegramUser, groupChatId);
            case CALLBACK_GROUP_HELP -> telegramBotClient.sendMessage(groupChatId, buildGroupInstructionMessage());
            default -> {
                if (data.startsWith(CALLBACK_GROUP_BIND_PREFIX)) {
                    handleBindGroupCallback(data, telegramUser, groupChatId);
                }
            }
        }
    }

    private boolean handlePrivateButtonAction(String text, TelegramUserDto telegramUser, String privateChatId) {
        switch (text) {
            case BUTTON_CREATE_CHAT -> {
                dialogStateService.beginCreateChat(privateChatId);
                sendPrivateMessage(privateChatId, "Пришли название нового чата.");
                return true;
            }
            case BUTTON_JOIN -> {
                dialogStateService.beginJoinInvite(privateChatId);
                sendPrivateMessage(privateChatId, "Пришли invite-код, чтобы войти в чат.");
                return true;
            }
            case BUTTON_MY_CHATS -> {
                handleMyChats(telegramUser, privateChatId);
                return true;
            }
            case BUTTON_CREATE_INVITE -> {
                handleInviteChooser(telegramUser, privateChatId);
                return true;
            }
            case BUTTON_REFRESH_TOKEN -> {
                handleRegistrationCommand(telegramUser, privateChatId);
                return true;
            }
            case BUTTON_INSTRUCTIONS -> {
                sendPrivateMessage(privateChatId, buildInstructionMessage());
                return true;
            }
            case BUTTON_CANCEL -> {
                dialogStateService.clear(privateChatId);
                sendPrivateMessage(privateChatId, "Ок, отменил. Выбери следующее действие.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handlePendingPrivateInput(String text, TelegramUserDto telegramUser, String privateChatId) {
        PendingPrivateAction pendingAction = dialogStateService.get(privateChatId);
        if (pendingAction == null) {
            return false;
        }

        if (pendingAction == PendingPrivateAction.CREATE_CHAT_TITLE) {
            UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
            if (userAccount == null) {
                return true;
            }

            dialogStateService.clear(privateChatId);
            try {
                ConversationMember membership = conversationService.createConversation(userAccount, text);
                sendPrivateMessage(privateChatId, buildCreateChatMessage(membership));
            } catch (IllegalArgumentException ex) {
                dialogStateService.beginCreateChat(privateChatId);
                sendPrivateMessage(privateChatId, ex.getMessage() + "\n\nПришли другое название или нажми 'Отмена'.");
            }
            return true;
        }

        if (pendingAction == PendingPrivateAction.JOIN_INVITE_CODE) {
            UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
            if (userAccount == null) {
                return true;
            }

            dialogStateService.clear(privateChatId);
            try {
                ConversationInviteAcceptanceResult result = conversationService.acceptInvite(userAccount, text);
                sendPrivateMessage(privateChatId, buildJoinMessage(result));
            } catch (NotFoundException | IllegalArgumentException | ForbiddenException ex) {
                dialogStateService.beginJoinInvite(privateChatId);
                sendPrivateMessage(privateChatId, ex.getMessage() + "\n\nПришли другой invite-код или нажми 'Отмена'.");
            }
            return true;
        }

        return false;
    }

    private void handleRegistrationCommand(TelegramUserDto telegramUser, String privateChatId) {
        TelegramRegistrationResult result = telegramRegistrationService.registerOrRefresh(
            String.valueOf(telegramUser.id()),
            telegramUser.username(),
            telegramUser.displayName(),
            privateChatId
        );
        sendPrivateMessage(privateChatId, buildRegistrationMessage(result));
    }

    private void handleJoinCommand(String text, TelegramUserDto telegramUser, String privateChatId) {
        String inviteCode = extractArgument(text);
        if (inviteCode == null) {
            sendPrivateMessage(privateChatId, "Пришли invite в формате /join CODE или нажми кнопку 'Инструкция'.");
            return;
        }

        UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
        if (userAccount == null) {
            return;
        }

        try {
            ConversationInviteAcceptanceResult result = conversationService.acceptInvite(userAccount, inviteCode);
            sendPrivateMessage(privateChatId, buildJoinMessage(result));
        } catch (NotFoundException | IllegalArgumentException | ForbiddenException ex) {
            sendPrivateMessage(privateChatId, ex.getMessage());
        }
    }

    private void handleMyChats(TelegramUserDto telegramUser, String privateChatId) {
        UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
        if (userAccount == null) {
            return;
        }

        List<ConversationMember> memberships = conversationService.listMemberships(AuthenticatedUser.from(userAccount));
        sendPrivateMessage(privateChatId, buildMyChatsMessage(memberships));
    }

    private void handleInviteChooser(TelegramUserDto telegramUser, String privateChatId) {
        UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
        if (userAccount == null) {
            return;
        }

        List<ConversationMember> memberships = conversationService.listMemberships(AuthenticatedUser.from(userAccount));
        if (memberships.isEmpty()) {
            sendPrivateMessage(privateChatId, "У тебя пока нет чатов. Нажми кнопку 'Создать чат'.");
            return;
        }

        sendPrivateInlineMessage(
            privateChatId,
            "Выбери чат, для которого создать invite.",
            buildConversationInlineKeyboard(memberships, CALLBACK_PRIVATE_INVITE_PREFIX)
        );
    }

    private void handleInviteCommand(String text, TelegramUserDto telegramUser, String privateChatId) {
        Long conversationId = parseLong(extractArgument(text));
        if (conversationId == null) {
            handleInviteChooser(telegramUser, privateChatId);
            return;
        }

        UserAccount userAccount = requireRegisteredUser(telegramUser, privateChatId, true);
        if (userAccount == null) {
            return;
        }

        try {
            IssuedConversationInvite invite = conversationService.createInvite(
                AuthenticatedUser.from(userAccount),
                conversationId
            );
            sendPrivateMessage(privateChatId, buildInviteMessage(invite));
        } catch (NotFoundException | IllegalArgumentException | ForbiddenException ex) {
            sendPrivateMessage(privateChatId, ex.getMessage());
        }
    }

    private void handleRegisterGroupChat(TelegramChatDto chat, TelegramUserDto telegramUser, String groupChatId) {
        UserAccount userAccount = requireRegisteredUser(telegramUser, groupChatId, false);
        if (userAccount == null) {
            return;
        }

        try {
            ConversationMember membership = transportBindingService.createConversationWithTelegramBinding(
                userAccount,
                defaultConversationTitle(chat),
                groupChatId
            );
            telegramBotClient.sendMessage(groupChatId, buildGroupRegistrationMessage(membership));
        } catch (IllegalArgumentException | NotFoundException | ForbiddenException ex) {
            telegramBotClient.sendMessage(groupChatId, ex.getMessage());
        }
    }

    private void handleBindGroupCommand(String text, TelegramUserDto telegramUser, String groupChatId) {
        Long conversationId = parseLong(extractArgument(text));
        if (conversationId == null) {
            handleGroupBindChooser(telegramUser, groupChatId);
            return;
        }

        bindGroupToConversation(telegramUser, groupChatId, conversationId);
    }

    private void handleGroupBindChooser(TelegramUserDto telegramUser, String groupChatId) {
        UserAccount userAccount = requireRegisteredUser(telegramUser, groupChatId, false);
        if (userAccount == null) {
            return;
        }

        List<ConversationMember> memberships = conversationService.listMemberships(AuthenticatedUser.from(userAccount));
        if (memberships.isEmpty()) {
            telegramBotClient.sendMessage(
                groupChatId,
                "У тебя пока нет чатов. Создай его в личке через кнопку 'Создать чат'."
            );
            return;
        }

        telegramBotClient.sendMessage(
            groupChatId,
            "Выбери чат, к которому нужно привязать эту группу.",
            buildConversationInlineKeyboard(memberships, CALLBACK_GROUP_BIND_PREFIX)
        );
    }

    private void handleBindGroupCallback(String data, TelegramUserDto telegramUser, String groupChatId) {
        Long conversationId = parseLong(data.substring(CALLBACK_GROUP_BIND_PREFIX.length()));
        if (conversationId == null) {
            telegramBotClient.sendMessage(groupChatId, "Не удалось определить чат для привязки.");
            return;
        }

        bindGroupToConversation(telegramUser, groupChatId, conversationId);
    }

    private void bindGroupToConversation(TelegramUserDto telegramUser, String groupChatId, Long conversationId) {
        UserAccount userAccount = requireRegisteredUser(telegramUser, groupChatId, false);
        if (userAccount == null) {
            return;
        }

        try {
            TransportBinding binding = transportBindingService.createTelegramBinding(
                userAccount,
                conversationId,
                groupChatId
            );
            telegramBotClient.sendMessage(groupChatId, buildBindMessage(binding));
        } catch (IllegalArgumentException | NotFoundException | ForbiddenException ex) {
            telegramBotClient.sendMessage(groupChatId, ex.getMessage());
        }
    }

    private void maybeSendGroupOnboarding(TelegramMessageDto message) {
        String groupChatId = String.valueOf(message.chat().id());
        Instant now = Instant.now();
        Instant lastSentAt = groupHintSentAt.get(groupChatId);
        if (lastSentAt != null && lastSentAt.plusSeconds(GROUP_HINT_INTERVAL_SECONDS).isAfter(now)) {
            return;
        }
        groupHintSentAt.put(groupChatId, now);
        sendGroupOnboarding(groupChatId);
    }

    private void sendGroupOnboarding(String groupChatId) {
        telegramBotClient.sendMessage(
            groupChatId,
            """
            Эта группа пока не подключена к Hermes.

            Нажми кнопку ниже:
            1. Создать новый чат прямо для этой группы.
            2. Привязать группу к уже существующему чату.
            3. Открыть короткую инструкцию.
            """.stripIndent().trim(),
            buildGroupOnboardingKeyboard()
        );
    }

    private String normalizeCommand(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String firstToken = text.trim().split("\\s+", 2)[0];
        int mentionIndex = firstToken.indexOf('@');
        return mentionIndex >= 0 ? firstToken.substring(0, mentionIndex) : firstToken;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean isCommand(String text) {
        return text != null && text.stripLeading().startsWith("/");
    }

    private boolean hasSupportedGroupPayload(TelegramMessageDto message) {
        return (message.text() != null && !message.text().isBlank())
            || (message.caption() != null && !message.caption().isBlank())
            || (message.photo() != null && !message.photo().isEmpty())
            || message.document() != null
            || message.video() != null
            || message.voice() != null;
    }

    private String extractInboundBody(TelegramMessageDto message) {
        if (message.text() != null && !message.text().isBlank()) {
            return message.text();
        }
        if (message.caption() != null && !message.caption().isBlank()) {
            return message.caption();
        }
        return "";
    }

    private List<ConversationAttachmentDraft> extractInboundAttachments(TelegramMessageDto message) {
        List<ConversationAttachmentDraft> attachments = new ArrayList<>();

        TelegramPhotoSizeDto photo = largestPhoto(message.photo());
        if (photo != null && photo.fileId() != null && !photo.fileId().isBlank()) {
            attachments.add(telegramBotClient.downloadAttachment(
                photo.fileId(),
                ConversationAttachmentKind.PHOTO,
                "telegram-photo-" + message.messageId() + ".jpg",
                "image/jpeg",
                photo.fileSize()
            ));
        }

        TelegramDocumentDto document = message.document();
        if (document != null && document.fileId() != null && !document.fileId().isBlank()) {
            attachments.add(telegramBotClient.downloadAttachment(
                document.fileId(),
                ConversationAttachmentKind.DOCUMENT,
                document.fileName(),
                document.mimeType(),
                document.fileSize()
            ));
        }

        TelegramVideoDto video = message.video();
        if (video != null && video.fileId() != null && !video.fileId().isBlank()) {
            attachments.add(telegramBotClient.downloadAttachment(
                video.fileId(),
                ConversationAttachmentKind.VIDEO,
                video.fileName(),
                video.mimeType(),
                video.fileSize()
            ));
        }

        TelegramVoiceDto voice = message.voice();
        if (voice != null && voice.fileId() != null && !voice.fileId().isBlank()) {
            attachments.add(telegramBotClient.downloadAttachment(
                voice.fileId(),
                ConversationAttachmentKind.VOICE,
                "telegram-voice-" + message.messageId() + ".ogg",
                voice.mimeType(),
                voice.fileSize()
            ));
        }

        return attachments;
    }

    private TelegramPhotoSizeDto largestPhoto(List<TelegramPhotoSizeDto> photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }

        return photos.stream()
            .max(
                Comparator.comparingLong(photo -> {
                    if (photo.fileSize() != null && photo.fileSize() > 0) {
                        return photo.fileSize();
                    }
                    long width = photo.width() == null ? 0 : photo.width();
                    long height = photo.height() == null ? 0 : photo.height();
                    return width * height;
                })
            )
            .orElse(null);
    }

    private String extractArgument(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return null;
        }
        return parts[1].trim();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private UserAccount requireRegisteredUser(TelegramUserDto telegramUser, String replyChatId, boolean privateChat) {
        UserAccount userAccount = userAccountService.findByTelegramUserId(String.valueOf(telegramUser.id())).orElse(null);
        if (userAccount == null) {
            if (privateChat) {
                sendPrivateMessage(replyChatId, "Сначала нажми 'Получить токен' или используй /start.");
            } else {
                telegramBotClient.sendMessage(replyChatId, "Сначала открой личку с ботом и нажми 'Получить токен'.");
            }
        }
        return userAccount;
    }

    private String defaultConversationTitle(TelegramChatDto chat) {
        if (chat.title() != null && !chat.title().isBlank()) {
            return chat.title().trim();
        }
        return "Telegram Chat " + chat.id();
    }

    private void sendPrivateMessage(String chatId, String text) {
        telegramBotClient.sendMessage(chatId, text, buildPrivateMainMenuKeyboard());
    }

    private void sendPrivateInlineMessage(String chatId, String text, Map<String, Object> markup) {
        telegramBotClient.sendMessage(chatId, text, markup);
    }

    private Map<String, Object> buildPrivateMainMenuKeyboard() {
        return Map.of(
            "keyboard", List.of(
                keyboardRow(BUTTON_CREATE_CHAT, BUTTON_JOIN),
                keyboardRow(BUTTON_MY_CHATS, BUTTON_CREATE_INVITE),
                keyboardRow(BUTTON_REFRESH_TOKEN, BUTTON_INSTRUCTIONS),
                keyboardRow(BUTTON_CANCEL)
            ),
            "resize_keyboard", true,
            "one_time_keyboard", false
        );
    }

    private Map<String, Object> buildConversationInlineKeyboard(
        List<ConversationMember> memberships,
        String callbackPrefix
    ) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (ConversationMember membership : memberships) {
            rows.add(List.of(Map.of(
                "text", "#%s | %s | %s".formatted(
                    membership.getConversation().getId(),
                    membership.getRole().name(),
                    membership.getConversation().getTitle()
                ),
                "callback_data", callbackPrefix + membership.getConversation().getId()
            )));
        }
        return Map.of("inline_keyboard", rows);
    }

    private Map<String, Object> buildGroupOnboardingKeyboard() {
        return Map.of(
            "inline_keyboard",
            List.of(
                List.of(Map.of("text", "Создать чат для группы", "callback_data", CALLBACK_GROUP_CREATE)),
                List.of(Map.of("text", "Привязать к существующему", "callback_data", CALLBACK_GROUP_BIND_LIST)),
                List.of(Map.of("text", "Инструкция", "callback_data", CALLBACK_GROUP_HELP))
            )
        );
    }

    private List<Map<String, String>> keyboardRow(String... labels) {
        List<Map<String, String>> row = new ArrayList<>(labels.length);
        for (String label : labels) {
            row.add(Map.of("text", label));
        }
        return row;
    }

    private String buildRegistrationMessage(TelegramRegistrationResult result) {
        String action = result.created() ? "Аккаунт создан." : "Аккаунт найден, token перевыпущен.";
        String expiresAt = result.tokenExpiresAt() == null
            ? "не истекает"
            : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(result.tokenExpiresAt().atOffset(ZoneOffset.UTC));

        return """
            %s

            ID: %s
            Username: %s
            Tenant: %s

            Bearer token:
            %s

            Срок действия: %s

            Дальше можно пользоваться кнопками ниже:
            - Создать чат
            - Вход
            - Мои чаты
            - Создать инвайт
            - Инструкция
            """.formatted(
            action,
            result.userId(),
            result.username(),
            result.tenantKey(),
            result.plainTextToken(),
            expiresAt
        ).stripIndent().trim();
    }

    private String buildInstructionMessage() {
        return """
            Как пользоваться Hermes:

            1. Нажми 'Создать чат', чтобы создать новый внутренний чат.
            2. Нажми 'Вход', чтобы вставить invite-код и войти в чат без команды /join.
            3. Нажми 'Мои чаты', чтобы увидеть список своих чатов и их ID.
            4. Нажми 'Создать инвайт', чтобы выбрать чат и получить код приглашения.
            5. Отправь invite другому человеку, а он в личке с ботом нажмет 'Вход' или использует /join CODE.
            6. Чтобы подключить Telegram-группу, просто добавь туда бота. Если группа еще не подключена, бот сам покажет кнопки подключения.

            Для групп:
            - 'Создать чат для группы' создаст чат и сразу привяжет текущую группу.
            - 'Привязать к существующему' подключит группу к уже созданному чату.
            """.stripIndent().trim();
    }

    private String buildGroupInstructionMessage() {
        return """
            Как подключить эту группу:

            1. Убедись, что ты уже зарегистрирован у бота в личке.
            2. Нажми 'Создать чат для группы', если нужен новый чат.
            3. Или нажми 'Привязать к существующему', если чат уже создан.

            После привязки сообщения начнут синхронизироваться автоматически.
            """.stripIndent().trim();
    }

    private String buildJoinMessage(ConversationInviteAcceptanceResult result) {
        if (result.alreadyMember()) {
            return """
                Ты уже участник этого чата.

                Чат: %s
                ID: %s
                """.formatted(
                result.membership().getConversation().getTitle(),
                result.membership().getConversation().getId()
            ).stripIndent().trim();
        }

        return """
            Готово, ты вступил в чат.

            Чат: %s
            ID: %s
            Роль: %s
            """.formatted(
            result.membership().getConversation().getTitle(),
            result.membership().getConversation().getId(),
            result.membership().getRole().name()
        ).stripIndent().trim();
    }

    private String buildCreateChatMessage(ConversationMember membership) {
        return """
            Чат создан.

            Чат: %s
            ID: %s
            Роль: %s
            """.formatted(
            membership.getConversation().getTitle(),
            membership.getConversation().getId(),
            membership.getRole().name()
        ).stripIndent().trim();
    }

    private String buildMyChatsMessage(List<ConversationMember> memberships) {
        if (memberships.isEmpty()) {
            return "У тебя пока нет чатов. Нажми кнопку 'Создать чат' или подключи группу.";
        }

        StringBuilder builder = new StringBuilder("Твои чаты:\n\n");
        for (ConversationMember membership : memberships) {
            builder.append("ID: ")
                .append(membership.getConversation().getId())
                .append(" | ")
                .append(membership.getRole().name())
                .append(" | ")
                .append(membership.getConversation().getTitle())
                .append('\n');
        }
        builder.append("\nДля invite нажми кнопку 'Создать инвайт'.");
        return builder.toString().trim();
    }

    private String buildInviteMessage(IssuedConversationInvite invite) {
        return """
            Invite создан.

            Чат: %s
            ID: %s
            Код:
            %s
            """.formatted(
            invite.invite().getConversation().getTitle(),
            invite.invite().getConversation().getId(),
            invite.inviteCode()
        ).stripIndent().trim();
    }

    private String buildGroupRegistrationMessage(ConversationMember membership) {
        return """
            Готово, группа зарегистрирована.

            Чат: %s
            ID: %s
            Роль создателя: %s
            """.formatted(
            membership.getConversation().getTitle(),
            membership.getConversation().getId(),
            membership.getRole().name()
        ).stripIndent().trim();
    }

    private String buildBindMessage(TransportBinding binding) {
        return """
            Группа привязана.

            Чат ID: %s
            Telegram chat ID: %s
            """.formatted(
            binding.getConversation().getId(),
            binding.getExternalChatId()
        ).stripIndent().trim();
    }
}
