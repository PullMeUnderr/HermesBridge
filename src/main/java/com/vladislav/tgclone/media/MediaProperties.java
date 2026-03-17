package com.vladislav.tgclone.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
    String storageRoot,
    long maxFileSizeBytes,
    String storageProvider,
    String s3Endpoint,
    String s3Bucket,
    String s3AccessKeyId,
    String s3SecretAccessKey,
    String s3Region
) {

    public boolean useObjectStorage() {
        String normalized = storageProvider == null ? "" : storageProvider.trim();
        return "backblaze-b2".equalsIgnoreCase(normalized)
            || "s3".equalsIgnoreCase(normalized)
            || "r2".equalsIgnoreCase(normalized);
    }

    public String resolvedS3Endpoint() {
        if (s3Endpoint == null || s3Endpoint.isBlank()) {
            return null;
        }
        return s3Endpoint.trim();
    }

    public String resolvedS3Region() {
        if (s3Region != null && !s3Region.isBlank()) {
            return s3Region.trim();
        }

        String endpoint = resolvedS3Endpoint();
        if (endpoint != null) {
            String normalized = endpoint.replace("https://", "").replace("http://", "");
            if (normalized.startsWith("s3.") && normalized.contains(".backblazeb2.com")) {
                return normalized.substring("s3.".length(), normalized.indexOf(".backblazeb2.com"));
            }
        }

        return "us-east-1";
    }
}
