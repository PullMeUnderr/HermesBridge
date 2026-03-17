package com.vladislav.tgclone.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentMediaStorageCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentMediaStorageCleaner.class);

    private final MediaProperties mediaProperties;
    private final String datasourceUrl;

    public DevelopmentMediaStorageCleaner(
        MediaProperties mediaProperties,
        @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        this.mediaProperties = mediaProperties;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
        if (!isEphemeralH2(datasourceUrl) || mediaProperties.useObjectStorage()) {
            return;
        }

        Path root = Path.of(mediaProperties.storageRoot()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(root))
                .forEach(this::deleteQuietly);
        }
        log.info("Cleared development media storage at {}", root);
    }

    private boolean isEphemeralH2(String url) {
        return url != null && url.startsWith("jdbc:h2:mem:");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete development media file {}", path);
        }
    }
}
