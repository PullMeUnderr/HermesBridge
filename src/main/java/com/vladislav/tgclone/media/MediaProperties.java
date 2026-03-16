package com.vladislav.tgclone.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
    String storageRoot,
    long maxFileSizeBytes
) {
}
