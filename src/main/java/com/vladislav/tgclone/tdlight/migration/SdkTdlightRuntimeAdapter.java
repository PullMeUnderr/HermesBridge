package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightAccountBindingService;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.ClientInteraction;
import it.tdlight.client.GenericResultHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SdkTdlightRuntimeAdapter implements TdlightRuntimeAdapter {

    private static final int CLIENT_TIMEOUT_SECONDS = 20;
    private static final int DOWNLOAD_PRIORITY = 32;
    private static final int HISTORY_PAGE_SIZE = 100;
    private static final int CHAT_LIST_FETCH_LIMIT = 1000;

    private final TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap;
    private final TdlightSessionFactory tdlightSessionFactory;
    private final TdlightAccountBindingService tdlightAccountBindingService;
    private final Map<String, ActiveRuntimeSession> activeSessions = new ConcurrentHashMap<>();

    public SdkTdlightRuntimeAdapter(
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap,
        TdlightSessionFactory tdlightSessionFactory,
        TdlightAccountBindingService tdlightAccountBindingService
    ) {
        this.tdlightNativeLibraryBootstrap = tdlightNativeLibraryBootstrap;
        this.tdlightSessionFactory = tdlightSessionFactory;
        this.tdlightAccountBindingService = tdlightAccountBindingService;
    }

    @Override
    public TdlightRuntimeSessionContext openSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration
    ) {
        TdlightNativeLibraryBootstrap.NativeRuntimeDescriptor nativeRuntimeDescriptor =
            tdlightNativeLibraryBootstrap.ensureLoaded(runtimeConfiguration);
        TdlightSessionFactory.TdlightRuntimeSession runtimeSession = tdlightSessionFactory.createSession(
            connection,
            runtimeConfiguration,
            nativeRuntimeDescriptor
        );

        try {
            Files.createDirectories(Path.of(runtimeSession.sessionBinding().databaseDirectory()));
            Files.createDirectories(Path.of(runtimeSession.sessionBinding().filesDirectory()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare TDLight session directories", exception);
        }

        TDLibSettings settings = TDLibSettings.create(new it.tdlight.client.APIToken(
            runtimeConfiguration.apiId(),
            runtimeConfiguration.apiHash()
        ));
        settings.setDatabaseDirectoryPath(Path.of(runtimeSession.sessionBinding().databaseDirectory()));
        settings.setDownloadedFilesDirectoryPath(Path.of(runtimeSession.sessionBinding().filesDirectory()));
        settings.setUseTestDatacenter(false);
        settings.setSystemLanguageCode(runtimeConfiguration.systemLanguageCode());
        settings.setDeviceModel(runtimeConfiguration.deviceModel());
        settings.setApplicationVersion(runtimeConfiguration.applicationVersion());

        SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory();
        CompletableFuture<Void> readySignal = new CompletableFuture<>();
        SimpleTelegramClientBuilder builder = factory.builder(settings);
        builder.setClientInteraction(new FailingClientInteraction());
        builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update -> {
            if (update != null && update.authorizationState instanceof TdApi.AuthorizationStateReady) {
                readySignal.complete(null);
            }
        });
        SimpleTelegramClient client = builder.build(AuthenticationSupplier.qrCode());

        try {
            readySignal.get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            TdApi.User me = client.getMeAsync().get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            tdlightAccountBindingService.syncAuthorizedAccount(connection, toAuthorizedUser(me));
        } catch (Exception exception) {
            closeQuietly(client);
            closeQuietly(factory);
            throw new IllegalStateException(
                "TDLight runtime session is not authorized yet. Complete QR/code login for this connection before importing channels.",
                exception
            );
        }

        TdlightRuntimeSessionContext sessionContext = new TdlightRuntimeSessionContext(
            runtimeSession.sessionHandle(),
            runtimeSession.sessionBinding(),
            runtimeSession.sessionEnvelope(),
            runtimeSession.restoredFromExistingSession()
        );
        activeSessions.put(
            sessionContext.sessionHandle().sessionKey(),
            new ActiveRuntimeSession(factory, client, runtimeSession)
        );
        return sessionContext;
    }

    @Override
    public void closeSession(TdlightRuntimeSessionContext sessionContext) {
        if (sessionContext == null || sessionContext.sessionHandle() == null) {
            return;
        }
        ActiveRuntimeSession runtimeSession = activeSessions.remove(sessionContext.sessionHandle().sessionKey());
        if (runtimeSession == null) {
            return;
        }
        closeQuietly(runtimeSession.client());
        closeQuietly(runtimeSession.factory());
        closeQuietly(runtimeSession.runtimeSession());
    }

    @Override
    public TdlightPublicChannelClient.TdlightResolvedChannel resolvePublicChannel(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightChannelReference reference
    ) {
        SimpleTelegramClient client = requireClient(sessionContext);
        TdApi.Chat chat = resolveChat(client, reference);
        if (!(chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) || !supergroupType.isChannel) {
            return new TdlightPublicChannelClient.TdlightResolvedChannel(
                String.valueOf(chat.id),
                null,
                chat.title,
                reference.sourceChannelHandle() != null ? "@" + reference.sourceChannelHandle() : String.valueOf(chat.id),
                classifyReferenceKind(reference),
                false,
                TdlightPublicChannelClient.TdlightChannelEligibility.NOT_PUBLIC_CHANNEL,
                "Telegram entity is not a public channel"
            );
        }

        TdApi.Supergroup supergroup = await(client.send(new TdApi.GetSupergroup(supergroupType.supergroupId)));
        String handle = firstActiveUsername(supergroup.usernames);
        String normalizedReference = handle != null ? "@" + handle : String.valueOf(chat.id);
        return new TdlightPublicChannelClient.TdlightResolvedChannel(
            String.valueOf(chat.id),
            handle,
            chat.title,
            normalizedReference,
            classifyReferenceKind(reference),
            true,
            TdlightPublicChannelClient.TdlightChannelEligibility.ELIGIBLE,
            null
        );
    }

    @Override
    public List<TdlightPublicChannelClient.TdlightAvailableChannel> listAvailablePublicChannels(
        TdlightRuntimeSessionContext sessionContext
    ) {
        SimpleTelegramClient client = requireClient(sessionContext);
        TdApi.Chats chats;
        try {
            await(client.send(new TdApi.LoadChats(new TdApi.ChatListMain(), CHAT_LIST_FETCH_LIMIT)));
            chats = await(client.send(new TdApi.GetChats(new TdApi.ChatListMain(), CHAT_LIST_FETCH_LIMIT)));
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (chats == null || chats.chatIds == null || chats.chatIds.length == 0) {
            return List.of();
        }

        List<TdlightPublicChannelClient.TdlightAvailableChannel> channels = new ArrayList<>();
        for (long chatId : chats.chatIds) {
            try {
                TdApi.Chat chat = await(client.send(new TdApi.GetChat(chatId)));
                if (chat == null || !(chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) || !supergroupType.isChannel) {
                    continue;
                }
                TdApi.Supergroup supergroup = await(client.send(new TdApi.GetSupergroup(supergroupType.supergroupId)));
                String handle = firstActiveUsername(supergroup.usernames);
                channels.add(new TdlightPublicChannelClient.TdlightAvailableChannel(
                    String.valueOf(chat.id),
                    handle,
                    trimToNull(chat.title),
                    buildChannelAvatarDataUrl(client, chat)
                ));
            } catch (RuntimeException exception) {
                // Some chats from the main list can disappear or become inaccessible
                // between LoadChats/GetChats and the follow-up metadata fetches.
                // Skip those entries instead of failing the whole listing request.
                continue;
            }
        }
        return channels;
    }

    @Override
    public List<TdlightPublicChannelClient.TdlightFetchedPost> fetchNewPosts(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchCursor cursor,
        int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        SimpleTelegramClient client = requireClient(sessionContext);
        long chatId = parseChatId(channel.sourceChannelId());
        Long lastSeenMessageId = parseNullableLong(cursor.lastSeenRemoteMessageId());
        long activatedAtEpochSecond = cursor.activatedAt() == null ? Long.MIN_VALUE : cursor.activatedAt().getEpochSecond();
        Map<Long, TdApi.Message> uniqueMessages = new LinkedHashMap<>();
        List<TdApi.Message> historicalContextMessages = new ArrayList<>();
        int remainingHistoricalContext = lastSeenMessageId == null ? Math.max(0, cursor.initialHistoricalPostCount()) : 0;
        long fromMessageId = 0L;

        while (uniqueMessages.size() < limit) {
            TdApi.Messages page = await(client.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, Math.min(HISTORY_PAGE_SIZE, limit), false)));
            if (page == null || page.messages == null || page.messages.length == 0) {
                break;
            }

            long oldestMessageIdInPage = Long.MAX_VALUE;
            boolean reachedOlderBoundary = false;
            for (TdApi.Message message : page.messages) {
                if (message == null) {
                    continue;
                }
                oldestMessageIdInPage = Math.min(oldestMessageIdInPage, message.id);
                if (!shouldInclude(message, lastSeenMessageId, activatedAtEpochSecond, cursor.backfillHistoryEnabled())) {
                    if ((lastSeenMessageId != null && message.id <= lastSeenMessageId)
                        || (!cursor.backfillHistoryEnabled() && message.date < activatedAtEpochSecond)) {
                        if (lastSeenMessageId == null
                            && !cursor.backfillHistoryEnabled()
                            && remainingHistoricalContext > 0
                            && message.content != null) {
                            historicalContextMessages.add(message);
                            remainingHistoricalContext -= 1;
                        } else {
                            reachedOlderBoundary = true;
                        }
                    }
                    continue;
                }
                uniqueMessages.putIfAbsent(message.id, message);
            }

            if (lastSeenMessageId == null
                && !cursor.backfillHistoryEnabled()
                && remainingHistoricalContext == 0) {
                reachedOlderBoundary = true;
            }

            if (page.messages.length < Math.min(HISTORY_PAGE_SIZE, limit)
                || oldestMessageIdInPage == Long.MAX_VALUE
                || oldestMessageIdInPage == fromMessageId
                || reachedOlderBoundary) {
                break;
            }
            fromMessageId = oldestMessageIdInPage;
        }

        if (!historicalContextMessages.isEmpty()) {
            historicalContextMessages.stream()
                .sorted(Comparator.comparingLong(message -> message.id))
                .forEach(message -> uniqueMessages.putIfAbsent(message.id, message));
        }

        return uniqueMessages.values().stream()
            .sorted(Comparator.comparingLong(message -> message.id))
            .limit(limit)
            .map(message -> toFetchedPost(client, chatId, message, cursor.includeMedia()))
            .toList();
    }

    @Override
    public TdlightPublicChannelClient.TdlightFetchedMedia fetchMedia(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchedPost post,
        TdlightPublicChannelClient.TdlightFetchedMediaReference mediaReference
    ) {
        SimpleTelegramClient client = requireClient(sessionContext);
        TdApi.File file = await(client.send(new TdApi.GetFile(Integer.parseInt(mediaReference.remoteMediaId()))));
        return toFetchedMedia(client, file, mediaReference.fileName(), mediaReference.mimeType(), mediaReference.durationSeconds());
    }

    @Override
    public TdlightPublicChannelClient.TdlightFetchedMedia fetchChannelAvatar(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel
    ) {
        SimpleTelegramClient client = requireClient(sessionContext);
        TdApi.Chat chat = await(client.send(new TdApi.GetChat(parseChatId(channel.sourceChannelId()))));
        if (chat == null || chat.photo == null) {
            return null;
        }

        TdApi.File avatarFile = chat.photo.small != null ? chat.photo.small : chat.photo.big;
        if (avatarFile == null) {
            return null;
        }

        return toFetchedMedia(
            client,
            avatarFile,
            "channel-%s.jpg".formatted(chat.id),
            "image/jpeg",
            0
        );
    }

    @Override
    public TdlightAuthorizedUser getCurrentUser(TdlightRuntimeSessionContext sessionContext) {
        return toAuthorizedUser(await(requireClient(sessionContext).getMeAsync()));
    }

    private TdApi.Chat resolveChat(
        SimpleTelegramClient client,
        TdlightPublicChannelClient.TdlightChannelReference reference
    ) {
        if (reference.sourceChannelHandle() != null && !reference.sourceChannelHandle().isBlank()) {
            return await(client.send(new TdApi.SearchPublicChat(reference.sourceChannelHandle())));
        }
        String sourceChannelId = Objects.requireNonNull(reference.sourceChannelId(), "sourceChannelId is required");
        if (sourceChannelId.startsWith("@")) {
            return await(client.send(new TdApi.SearchPublicChat(sourceChannelId.substring(1))));
        }
        return await(client.send(new TdApi.GetChat(parseChatId(sourceChannelId))));
    }

    private TdlightPublicChannelClient.TdlightFetchedPost toFetchedPost(
        SimpleTelegramClient client,
        long fallbackChatId,
        TdApi.Message message,
        boolean includeMedia
    ) {
        String body = extractBody(message.content);
        String authorDisplayName = resolveAuthorDisplayName(client, fallbackChatId, message);
        String authorExternalId = resolveAuthorExternalId(message.senderId);
        List<TdlightPublicChannelClient.TdlightFetchedMediaReference> mediaReferences = extractMediaReferences(message.content);
        List<TdlightPublicChannelClient.TdlightFetchedMedia> media = includeMedia
            ? extractFetchedMedia(client, message.content)
            : List.of();

        return new TdlightPublicChannelClient.TdlightFetchedPost(
            String.valueOf(message.id),
            authorExternalId,
            authorDisplayName,
            body,
            Instant.ofEpochSecond(message.date),
            mediaReferences,
            media
        );
    }

    private List<TdlightPublicChannelClient.TdlightFetchedMedia> extractFetchedMedia(
        SimpleTelegramClient client,
        TdApi.MessageContent content
    ) {
        List<TdlightPublicChannelClient.TdlightFetchedMedia> media = new ArrayList<>();
        if (content instanceof TdApi.MessagePhoto photo && photo.photo != null && photo.photo.sizes != null) {
            TdApi.PhotoSize largestPhoto = selectLargestPhoto(photo.photo.sizes);
            if (largestPhoto != null && largestPhoto.photo != null) {
                media.add(
                    toFetchedMedia(
                        client,
                        largestPhoto.photo,
                        "photo-%s.jpg".formatted(largestPhoto.photo.id),
                        "image/jpeg",
                        0
                    )
                );
            }
        } else if (content instanceof TdApi.MessageVideo video && video.video != null && video.video.video != null) {
            media.add(
                toFetchedMedia(
                    client,
                    video.video.video,
                    normalizeFileName(video.video.fileName, "video-%s.mp4".formatted(video.video.video.id)),
                    normalizeMimeType(video.video.mimeType, "video/mp4"),
                    video.video.duration
                )
            );
        } else if (content instanceof TdApi.MessageDocument document
            && document.document != null
            && document.document.document != null) {
            media.add(
                toFetchedMedia(
                    client,
                    document.document.document,
                    normalizeFileName(document.document.fileName, "document-%s".formatted(document.document.document.id)),
                    normalizeMimeType(document.document.mimeType, "application/octet-stream"),
                    0
                )
            );
        } else if (content instanceof TdApi.MessageAudio audio && audio.audio != null && audio.audio.audio != null) {
            media.add(
                toFetchedMedia(
                    client,
                    audio.audio.audio,
                    normalizeFileName(audio.audio.fileName, "audio-%s".formatted(audio.audio.audio.id)),
                    normalizeMimeType(audio.audio.mimeType, "audio/mpeg"),
                    audio.audio.duration
                )
            );
        } else if (content instanceof TdApi.MessageVoiceNote voiceNote
            && voiceNote.voiceNote != null
            && voiceNote.voiceNote.voice != null) {
            media.add(
                toFetchedMedia(
                    client,
                    voiceNote.voiceNote.voice,
                    "voice-%s.ogg".formatted(voiceNote.voiceNote.voice.id),
                    normalizeMimeType(voiceNote.voiceNote.mimeType, "audio/ogg"),
                    voiceNote.voiceNote.duration
                )
            );
        } else if (content instanceof TdApi.MessageVideoNote videoNote
            && videoNote.videoNote != null
            && videoNote.videoNote.video != null) {
            media.add(
                toFetchedMedia(
                    client,
                    videoNote.videoNote.video,
                    "video-note-%s.mp4".formatted(videoNote.videoNote.video.id),
                    "video/mp4",
                    videoNote.videoNote.duration
                )
            );
        }
        return media;
    }

    private List<TdlightPublicChannelClient.TdlightFetchedMediaReference> extractMediaReferences(TdApi.MessageContent content) {
        List<TdlightPublicChannelClient.TdlightFetchedMediaReference> mediaReferences = new ArrayList<>();
        if (content instanceof TdApi.MessagePhoto photo && photo.photo != null && photo.photo.sizes != null) {
            TdApi.PhotoSize largestPhoto = selectLargestPhoto(photo.photo.sizes);
            if (largestPhoto != null && largestPhoto.photo != null) {
                mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                    String.valueOf(largestPhoto.photo.id),
                    "photo-%s.jpg".formatted(largestPhoto.photo.id),
                    "image/jpeg",
                    largestPhoto.photo.size,
                    0
                ));
            }
        } else if (content instanceof TdApi.MessageVideo video && video.video != null && video.video.video != null) {
            mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                String.valueOf(video.video.video.id),
                normalizeFileName(video.video.fileName, "video-%s.mp4".formatted(video.video.video.id)),
                normalizeMimeType(video.video.mimeType, "video/mp4"),
                video.video.video.size,
                video.video.duration
            ));
        } else if (content instanceof TdApi.MessageDocument document
            && document.document != null
            && document.document.document != null) {
            mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                String.valueOf(document.document.document.id),
                normalizeFileName(document.document.fileName, "document-%s".formatted(document.document.document.id)),
                normalizeMimeType(document.document.mimeType, "application/octet-stream"),
                document.document.document.size,
                0
            ));
        } else if (content instanceof TdApi.MessageAudio audio && audio.audio != null && audio.audio.audio != null) {
            mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                String.valueOf(audio.audio.audio.id),
                normalizeFileName(audio.audio.fileName, "audio-%s".formatted(audio.audio.audio.id)),
                normalizeMimeType(audio.audio.mimeType, "audio/mpeg"),
                audio.audio.audio.size,
                audio.audio.duration
            ));
        } else if (content instanceof TdApi.MessageVoiceNote voiceNote
            && voiceNote.voiceNote != null
            && voiceNote.voiceNote.voice != null) {
            mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                String.valueOf(voiceNote.voiceNote.voice.id),
                "voice-%s.ogg".formatted(voiceNote.voiceNote.voice.id),
                normalizeMimeType(voiceNote.voiceNote.mimeType, "audio/ogg"),
                voiceNote.voiceNote.voice.size,
                voiceNote.voiceNote.duration
            ));
        } else if (content instanceof TdApi.MessageVideoNote videoNote
            && videoNote.videoNote != null
            && videoNote.videoNote.video != null) {
            mediaReferences.add(new TdlightPublicChannelClient.TdlightFetchedMediaReference(
                String.valueOf(videoNote.videoNote.video.id),
                "video-note-%s.mp4".formatted(videoNote.videoNote.video.id),
                "video/mp4",
                videoNote.videoNote.video.size,
                videoNote.videoNote.duration
            ));
        }
        return mediaReferences;
    }

    private TdlightPublicChannelClient.TdlightFetchedMedia toFetchedMedia(
        SimpleTelegramClient client,
        TdApi.File file,
        String fileName,
        String mimeType,
        int durationSeconds
    ) {
        TdApi.File downloadedFile = file;
        if (downloadedFile == null) {
            throw new IllegalStateException("TDLight media file is missing");
        }
        if (downloadedFile.local == null || !downloadedFile.local.isDownloadingCompleted || isBlank(downloadedFile.local.path)) {
            downloadedFile = await(client.send(new TdApi.DownloadFile(downloadedFile.id, DOWNLOAD_PRIORITY, 0, 0, true)));
        }
        if (downloadedFile.local == null || isBlank(downloadedFile.local.path)) {
            throw new IllegalStateException("TDLight media download completed without a local file path");
        }
        try {
            byte[] content = Files.readAllBytes(Path.of(downloadedFile.local.path));
            return new TdlightPublicChannelClient.TdlightFetchedMedia(
                fileName,
                mimeType,
                downloadedFile.size,
                durationSeconds,
                content
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read downloaded TDLight media from disk", exception);
        }
    }

    private String buildChannelAvatarDataUrl(SimpleTelegramClient client, TdApi.Chat chat) {
        if (chat == null || chat.photo == null) {
            return null;
        }

        TdApi.File avatarFile = chat.photo.small != null ? chat.photo.small : chat.photo.big;
        if (avatarFile == null) {
            return null;
        }

        try {
            TdlightPublicChannelClient.TdlightFetchedMedia media = toFetchedMedia(
                client,
                avatarFile,
                "channel-%s.jpg".formatted(chat.id),
                "image/jpeg",
                0
            );
            if (media.content() == null || media.content().length == 0) {
                return null;
            }
            return "data:%s;base64,%s".formatted(
                isBlank(media.mimeType()) ? "image/jpeg" : media.mimeType(),
                Base64.getEncoder().encodeToString(media.content())
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String extractBody(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText text && text.text != null) {
            return renderFormattedText(text.text);
        }
        if (content instanceof TdApi.MessagePhoto photo && photo.caption != null) {
            return renderFormattedText(photo.caption);
        }
        if (content instanceof TdApi.MessageVideo video && video.caption != null) {
            return renderFormattedText(video.caption);
        }
        if (content instanceof TdApi.MessageDocument document && document.caption != null) {
            return renderFormattedText(document.caption);
        }
        TdApi.FormattedText reflectedCaption = extractFormattedTextField(content, "caption");
        if (reflectedCaption != null) {
            return renderFormattedText(reflectedCaption);
        }
        TdApi.FormattedText reflectedText = extractFormattedTextField(content, "text");
        if (reflectedText != null) {
            return renderFormattedText(reflectedText);
        }
        return null;
    }

    private TdApi.FormattedText extractFormattedTextField(TdApi.MessageContent content, String fieldName) {
        if (content == null || isBlank(fieldName)) {
            return null;
        }

        try {
            java.lang.reflect.Field field = content.getClass().getField(fieldName);
            Object value = field.get(content);
            return value instanceof TdApi.FormattedText formattedText ? formattedText : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String renderFormattedText(TdApi.FormattedText formattedText) {
        if (formattedText == null || isBlank(formattedText.text)) {
            return null;
        }

        String text = formattedText.text;
        if (formattedText.entities == null || formattedText.entities.length == 0) {
            return trimToNull(text);
        }

        StringBuilder result = new StringBuilder();
        int cursor = 0;

        for (TdApi.TextEntity entity : formattedText.entities) {
            if (entity == null || entity.type == null) {
                continue;
            }

            int start = Math.max(0, Math.min(text.length(), entity.offset));
            int end = Math.max(start, Math.min(text.length(), entity.offset + entity.length));
            if (start < cursor) {
                continue;
            }

            if (cursor < start) {
                result.append(text, cursor, start);
            }

            String segment = text.substring(start, end);
            result.append(renderEntitySegment(segment, entity.type));
            cursor = end;
        }

        if (cursor < text.length()) {
            result.append(text.substring(cursor));
        }

        return trimToNull(result.toString());
    }

    private String renderEntitySegment(String segment, TdApi.TextEntityType type) {
        if (segment == null) {
            return "";
        }

        if (type instanceof TdApi.TextEntityTypeTextUrl textUrl) {
            String url = trimToNull(textUrl.url);
            String label = trimToNull(segment);
            if (url == null) {
                return segment;
            }
            if (label == null || label.equals(url)) {
                return url;
            }
            return "%s (%s)".formatted(label, url);
        }

        if (type instanceof TdApi.TextEntityTypeUrl) {
            return segment;
        }

        if (type instanceof TdApi.TextEntityTypeEmailAddress) {
            String email = trimToNull(segment);
            return email == null ? segment : "mailto:" + email;
        }

        return segment;
    }

    private String resolveAuthorDisplayName(SimpleTelegramClient client, long fallbackChatId, TdApi.Message message) {
        if (!isBlank(message.authorSignature)) {
            return message.authorSignature;
        }
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            TdApi.User user = await(client.send(new TdApi.GetUser(senderUser.userId)));
            String fullName = ("%s %s".formatted(
                trimToEmpty(user.firstName),
                trimToEmpty(user.lastName)
            )).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
            String username = firstActiveUsername(user.usernames);
            if (username != null) {
                return "@" + username;
            }
        }
        if (message.senderId instanceof TdApi.MessageSenderChat senderChat) {
            TdApi.Chat senderChatEntity = await(client.send(new TdApi.GetChat(senderChat.chatId)));
            if (!isBlank(senderChatEntity.title)) {
                return senderChatEntity.title;
            }
        }
        TdApi.Chat chat = await(client.send(new TdApi.GetChat(fallbackChatId)));
        return trimToNull(chat.title);
    }

    private String resolveAuthorExternalId(TdApi.MessageSender senderId) {
        if (senderId instanceof TdApi.MessageSenderUser senderUser) {
            return "user:" + senderUser.userId;
        }
        if (senderId instanceof TdApi.MessageSenderChat senderChat) {
            return "chat:" + senderChat.chatId;
        }
        return null;
    }

    private TdlightAuthorizedUser toAuthorizedUser(TdApi.User user) {
        if (user == null) {
            throw new IllegalStateException("TDLight did not return current user");
        }
        String fullName = ("%s %s".formatted(trimToEmpty(user.firstName), trimToEmpty(user.lastName))).trim();
        return new TdlightAuthorizedUser(
            String.valueOf(user.id),
            firstActiveUsername(user.usernames),
            fullName.isBlank() ? null : fullName
        );
    }

    private TdlightPublicChannelClient.TdlightChannelReferenceKind classifyReferenceKind(
        TdlightPublicChannelClient.TdlightChannelReference reference
    ) {
        if (reference.sourceChannelHandle() != null && !reference.sourceChannelHandle().isBlank()) {
            return TdlightPublicChannelClient.TdlightChannelReferenceKind.HANDLE;
        }
        if (reference.sourceChannelId() != null && reference.sourceChannelId().matches("-?\\d+")) {
            return TdlightPublicChannelClient.TdlightChannelReferenceKind.NUMERIC_ID;
        }
        return TdlightPublicChannelClient.TdlightChannelReferenceKind.UNKNOWN;
    }

    private boolean shouldInclude(
        TdApi.Message message,
        Long lastSeenMessageId,
        long activatedAtEpochSecond,
        boolean backfillHistoryEnabled
    ) {
        if (message == null || message.content == null) {
            return false;
        }
        if (lastSeenMessageId != null) {
            return message.id > lastSeenMessageId;
        }
        if (!backfillHistoryEnabled && message.date < activatedAtEpochSecond) {
            return false;
        }
        return true;
    }

    private TdApi.PhotoSize selectLargestPhoto(TdApi.PhotoSize[] sizes) {
        TdApi.PhotoSize best = null;
        long bestArea = -1L;
        for (TdApi.PhotoSize size : sizes) {
            if (size == null || size.photo == null) {
                continue;
            }
            long area = (long) size.width * size.height;
            if (area >= bestArea) {
                best = size;
                bestArea = area;
            }
        }
        return best;
    }

    private SimpleTelegramClient requireClient(TdlightRuntimeSessionContext sessionContext) {
        ActiveRuntimeSession runtimeSession = activeSessions.get(sessionContext.sessionHandle().sessionKey());
        if (runtimeSession == null) {
            throw new IllegalStateException("TDLight runtime session is not open");
        }
        return runtimeSession.client();
    }

    private long parseChatId(String sourceChannelId) {
        try {
            return Long.parseLong(sourceChannelId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TDLight sourceChannelId must resolve to a numeric chat id");
        }
    }

    private Long parseNullableLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("TDLight request failed", exception);
        }
    }

    private String firstActiveUsername(TdApi.Usernames usernames) {
        if (usernames == null || usernames.activeUsernames == null || usernames.activeUsernames.length == 0) {
            return null;
        }
        String username = usernames.activeUsernames[0];
        return isBlank(username) ? null : username;
    }

    private String normalizeFileName(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String normalizeMimeType(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record ActiveRuntimeSession(
        SimpleTelegramClientFactory factory,
        SimpleTelegramClient client,
        TdlightSessionFactory.TdlightRuntimeSession runtimeSession
    ) {
    }

    private static final class FailingClientInteraction implements ClientInteraction {

        @Override
        public CompletableFuture<String> onParameterRequest(
            it.tdlight.client.InputParameter parameter,
            it.tdlight.client.ParameterInfo parameterInfo
        ) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("TDLight runtime session requested interactive auth step: " + parameter)
            );
        }
    }
}
