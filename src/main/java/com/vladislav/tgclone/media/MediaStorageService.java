package com.vladislav.tgclone.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final DateTimeFormatter PARTITION_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        .withZone(ZoneOffset.UTC);

    private final MediaProperties mediaProperties;
    private final Clock clock;
    private final S3Client objectStorageClient;

    public MediaStorageService(MediaProperties mediaProperties, Clock clock) {
        this.mediaProperties = mediaProperties;
        this.clock = clock;
        this.objectStorageClient = mediaProperties.useObjectStorage() ? buildObjectStorageClient(mediaProperties) : null;
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

        Path target = null;
        if (mediaProperties.useObjectStorage()) {
            storeInObjectStorage(storageKey, resolvedMimeType, content);
        } else {
            target = resolveLocal(storageKey);
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, content);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to store attachment", ex);
            }
        }

        return new StoredMediaFile(storageKey, target, safeFilename, resolvedMimeType, content.length);
    }

    public InputStream openStream(String storageKey) {
        if (mediaProperties.useObjectStorage()) {
            return openObjectStorageStream(storageKey);
        }

        try {
            return Files.newInputStream(resolveLocal(storageKey));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open media stream", ex);
        }
    }

    public boolean exists(String storageKey) {
        if (mediaProperties.useObjectStorage()) {
            try {
                objectStorageClient.headObject(HeadObjectRequest.builder()
                    .bucket(mediaProperties.s3Bucket())
                    .key(storageKey)
                    .build());
                return true;
            } catch (NoSuchKeyException ex) {
                return false;
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    return false;
                }
                throw new IllegalStateException("Failed to check media object in object storage", ex);
            }
        }

        return Files.exists(resolveLocal(storageKey));
    }

    public MaterializedMediaFile materialize(String storageKey, String preferredFilename) {
        if (!mediaProperties.useObjectStorage()) {
            return new MaterializedMediaFile(resolveLocal(storageKey), false);
        }

        String sanitized = sanitizeFilename(preferredFilename);
        String suffix = extractSuffix(sanitized);
        try (InputStream inputStream = openObjectStorageStream(storageKey)) {
            Path tempFile = Files.createTempFile("hermes-media-", suffix);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return new MaterializedMediaFile(tempFile, true);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to materialize media from object storage", ex);
        }
    }

    public Path resolve(String storageKey) {
        return materialize(storageKey, "attachment.bin").path();
    }

    private Path resolveLocal(String storageKey) {
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

    private void storeInObjectStorage(String storageKey, String mimeType, byte[] content) {
        try {
            objectStorageClient.putObject(
                PutObjectRequest.builder()
                    .bucket(mediaProperties.s3Bucket())
                    .key(storageKey)
                    .contentType(mimeType)
                    .build(),
                RequestBody.fromBytes(content)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store attachment in object storage", ex);
        }
    }

    private InputStream openObjectStorageStream(String storageKey) {
        try {
            return objectStorageClient.getObject(
                GetObjectRequest.builder()
                    .bucket(mediaProperties.s3Bucket())
                    .key(storageKey)
                    .build()
            );
        } catch (NoSuchKeyException ex) {
            throw new IllegalStateException("Attachment not found in object storage", ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IllegalStateException("Attachment not found in object storage", ex);
            }
            throw new IllegalStateException("Failed to read attachment from object storage", ex);
        }
    }

    private S3Client buildObjectStorageClient(MediaProperties properties) {
        requireObjectStorageProperty(properties.s3Bucket(), "app.media.s3-bucket");
        requireObjectStorageProperty(properties.s3AccessKeyId(), "app.media.s3-access-key-id");
        requireObjectStorageProperty(properties.s3SecretAccessKey(), "app.media.s3-secret-access-key");
        String endpoint = requireObjectStorageProperty(properties.resolvedS3Endpoint(), "app.media.s3-endpoint");

        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(properties.resolvedS3Region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.s3AccessKeyId().trim(), properties.s3SecretAccessKey().trim())
                )
            )
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    private String requireObjectStorageProperty(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required object storage property: " + propertyName);
        }
        return value.trim();
    }

    private String sanitizeFilename(String value) {
        String candidate = value == null || value.isBlank() ? "attachment.bin" : value.trim();
        candidate = candidate.replace('\\', '_').replace('/', '_');
        return candidate.isBlank() ? "attachment.bin" : candidate;
    }

    private String normalizeMimeType(String mimeType, String filename) {
        if (mimeType != null && !mimeType.isBlank()) {
            String normalized = mimeType.trim();
            int parameterDelimiter = normalized.indexOf(';');
            if (parameterDelimiter >= 0) {
                normalized = normalized.substring(0, parameterDelimiter).trim();
            }
            if (!normalized.equalsIgnoreCase("application/octet-stream")) {
                return normalized.toLowerCase(Locale.ROOT);
            }
        }

        String guessed = URLConnection.guessContentTypeFromName(filename);
        return guessed == null || guessed.isBlank() ? "application/octet-stream" : guessed;
    }

    private String extractSuffix(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return ".bin";
        }
        return filename.substring(dotIndex);
    }

    public record MaterializedMediaFile(
        Path path,
        boolean temporary
    ) implements AutoCloseable {

        @Override
        public void close() {
            if (!temporary) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                log.warn("Failed to delete temporary media file {}", path, ex);
            }
        }
    }
}
