package com.vladislav.tgclone.tdlight.migration;

import org.springframework.stereotype.Component;

@Component
public class TdlightPublicChannelReferenceParser {

    public ParsedTdlightChannelReference parse(String rawReference, String rawHandleOverride) {
        String normalizedReference = normalizeRequired(rawReference);
        String normalizedHandleOverride = normalizeNullable(rawHandleOverride);

        if (isTelegramUrl(normalizedReference)) {
            String pathSegment = extractLastPathSegment(normalizedReference);
            if (pathSegment == null) {
                throw new IllegalArgumentException("telegramChannelId must be a valid public channel reference");
            }
            return fromHandleOrId(pathSegment, normalizedHandleOverride, normalizedReference);
        }

        return fromHandleOrId(normalizedReference, normalizedHandleOverride, normalizedReference);
    }

    private ParsedTdlightChannelReference fromHandleOrId(
        String rawValue,
        String rawHandleOverride,
        String originalReference
    ) {
        String normalized = rawValue.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("telegramChannelId must be a valid public channel reference");
        }

        String handle = rawHandleOverride;
        String channelId = normalized;

        if (normalized.startsWith("@")) {
            handle = normalizeHandle(normalized.substring(1));
            channelId = "@" + handle;
        } else if (looksLikeHandle(normalized)) {
            handle = normalizeHandle(normalized);
            channelId = "@" + handle;
        } else if (looksLikeNumericId(normalized)) {
            channelId = normalized;
        }

        if (handle == null && rawHandleOverride != null) {
            handle = normalizeHandle(rawHandleOverride);
        }

        return new ParsedTdlightChannelReference(
            channelId,
            handle,
            originalReference
        );
    }

    private boolean isTelegramUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("https://t.me/")
            || lower.startsWith("http://t.me/")
            || lower.startsWith("https://telegram.me/")
            || lower.startsWith("http://telegram.me/");
    }

    private String extractLastPathSegment(String value) {
        String withoutQuery = value.split("[?#]", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        if (slash < 0 || slash == withoutQuery.length() - 1) {
            return null;
        }
        String segment = withoutQuery.substring(slash + 1).trim();
        if (segment.isBlank() || "s".equalsIgnoreCase(segment) || "c".equalsIgnoreCase(segment)) {
            return null;
        }
        return segment;
    }

    private boolean looksLikeNumericId(String value) {
        return value.matches("-?\\d+");
    }

    private boolean looksLikeHandle(String value) {
        return value.matches("[A-Za-z][A-Za-z0-9_]{3,}");
    }

    private String normalizeHandle(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]{3,}")) {
            throw new IllegalArgumentException("telegramChannelHandle must look like a public Telegram handle");
        }
        return normalized;
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("telegramChannelId is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ParsedTdlightChannelReference(
        String sourceChannelId,
        String sourceChannelHandle,
        String originalReference
    ) {
    }
}
