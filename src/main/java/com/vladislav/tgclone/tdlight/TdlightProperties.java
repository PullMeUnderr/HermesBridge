package com.vladislav.tgclone.tdlight;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tdlight")
public record TdlightProperties(
    boolean enabled,
    boolean migrationEnabled,
    boolean entitlementRequired,
    TdlightClientMode publicChannelClientMode,
    boolean backfillHistoryEnabled,
    int publicChannelMessageImportLimit,
    int importedPostRetentionDays,
    boolean mediaImportEnabled,
    long maxImportedMediaBytes,
    int maxImportedVideoDurationSeconds,
    String sessionEncryptionKey,
    String libraryPath,
    String nativeWorkdir,
    String databaseDirectory,
    String filesDirectory,
    Integer apiId,
    String apiHash,
    String systemLanguageCode,
    String deviceModel,
    String applicationVersion
) {

    public TdlightProperties {
        if (publicChannelClientMode == null) {
            publicChannelClientMode = TdlightClientMode.STUB;
        }
        if (publicChannelMessageImportLimit <= 0) {
            publicChannelMessageImportLimit = 500;
        }
        if (importedPostRetentionDays <= 0) {
            importedPostRetentionDays = 7;
        }
        if (maxImportedMediaBytes <= 0) {
            maxImportedMediaBytes = 20L * 1024 * 1024;
        }
        if (maxImportedVideoDurationSeconds <= 0) {
            maxImportedVideoDurationSeconds = 180;
        }
        if (systemLanguageCode == null || systemLanguageCode.isBlank()) {
            systemLanguageCode = "en";
        }
        if (deviceModel == null || deviceModel.isBlank()) {
            deviceModel = "Hermes Local Dev";
        }
        if (applicationVersion == null || applicationVersion.isBlank()) {
            applicationVersion = "local-dev";
        }
    }
}
