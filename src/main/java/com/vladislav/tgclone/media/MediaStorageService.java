package com.vladislav.tgclone.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MediaStorageService {

    private static final DateTimeFormatter PARTITION_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        .withZone(ZoneOffset.UTC);

    private final MediaProperties mediaProperties;
    private final Clock clock;

    public MediaStorageService(MediaProperties mediaProperties, Clock clock) {
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    public StoredMediaFile store(String originalFilename, String mimeType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("attachment content is empty");
        }
        if (content.length > mediaProperties.maxFileSizeBytes()) {
            throw new IllegalArgumentException(
                "attachment exceeds max allowed size of %s bytes".formatted(mediaProperties.maxFileSizeBytes())
            );
        }

        String safeFilename = sanitizeFilename(originalFilename);
        String resolvedMimeType = normalizeMimeType(mimeType, safeFilename);
        Instant now = clock.instant();
        String datePartition = PARTITION_FORMAT.format(now);
        String storageKey = datePartition + "/" + UUID.randomUUID() + "-" + safeFilename;

        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store attachment", ex);
        }

        return new StoredMediaFile(storageKey, target, safeFilename, resolvedMimeType, content.length);
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }

        Path root = Paths.get(mediaProperties.storageRoot()).toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    private String sanitizeFilename(String value) {
        String candidate = value == null || value.isBlank() ? "attachment.bin" : value.trim();
        candidate = candidate.replace('\\', '_').replace('/', '_');
        return candidate.isBlank() ? "attachment.bin" : candidate;
    }

    private String normalizeMimeType(String mimeType, String filename) {
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType.trim();
        }

        String guessed = URLConnection.guessContentTypeFromName(filename);
        return guessed == null || guessed.isBlank() ? "application/octet-stream" : guessed;
    }
}
