package com.vladislav.tgclone.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeGuessesSpecificMimeTypeWhenUploadProvidesOctetStream() throws Exception {
        MediaStorageService mediaStorageService = new MediaStorageService(
            new MediaProperties(tempDir.toString(), 1024 * 1024),
            Clock.fixed(Instant.parse("2026-03-17T01:00:00Z"), ZoneOffset.UTC)
        );

        StoredMediaFile stored = mediaStorageService.store(
            "clip.mp4",
            "application/octet-stream",
            new byte[] {1, 2, 3, 4}
        );

        assertEquals("video/mp4", stored.mimeType());
        assertTrue(Files.exists(stored.path()));
    }

    @Test
    void storeStripsMimeTypeParametersFromBrowserRecordedVideo() throws Exception {
        MediaStorageService mediaStorageService = new MediaStorageService(
            new MediaProperties(tempDir.toString(), 1024 * 1024),
            Clock.fixed(Instant.parse("2026-03-17T01:05:00Z"), ZoneOffset.UTC)
        );

        StoredMediaFile stored = mediaStorageService.store(
            "video-note.mp4",
            "video/mp4;codecs=avc1.42E01E,mp4a.40.2",
            new byte[] {1, 2, 3, 4}
        );

        assertEquals("video/mp4", stored.mimeType());
        assertTrue(Files.exists(stored.path()));
    }
}
