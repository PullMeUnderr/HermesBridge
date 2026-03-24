package com.vladislav.tgclone.tdlight;

public record TdlightRuntimeConfiguration(
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
}
