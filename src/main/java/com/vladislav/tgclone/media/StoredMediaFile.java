package com.vladislav.tgclone.media;

import java.nio.file.Path;

public record StoredMediaFile(
    String storageKey,
    Path path,
    String originalFilename,
    String mimeType,
    long sizeBytes
) {
}
