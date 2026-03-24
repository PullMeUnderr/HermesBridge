package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightClientMode;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import java.util.ArrayList;
import java.util.List;

public class TdlightRuntimeReadinessService {

    private final TdlightProperties tdlightProperties;
    private final TdlightRuntimeAdapter tdlightRuntimeAdapter;
    private final TdlightSessionFactory tdlightSessionFactory;
    private final TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap;

    public TdlightRuntimeReadinessService(
        TdlightProperties tdlightProperties,
        TdlightRuntimeAdapter tdlightRuntimeAdapter,
        TdlightSessionFactory tdlightSessionFactory,
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap
    ) {
        this.tdlightProperties = tdlightProperties;
        this.tdlightRuntimeAdapter = tdlightRuntimeAdapter;
        this.tdlightSessionFactory = tdlightSessionFactory;
        this.tdlightNativeLibraryBootstrap = tdlightNativeLibraryBootstrap;
    }

    public TdlightRuntimeReadiness inspect() {
        List<String> blockers = new ArrayList<>();
        List<String> hints = new ArrayList<>();

        if (!tdlightProperties.enabled()) {
            blockers.add("app.tdlight.enabled=false");
        }
        if (!tdlightProperties.migrationEnabled()) {
            blockers.add("app.tdlight.migration-enabled=false");
        }
        if (tdlightProperties.publicChannelClientMode() != TdlightClientMode.REAL) {
            blockers.add("app.tdlight.public-channel-client-mode is not REAL");
        }
        if (tdlightProperties.apiId() == null) {
            blockers.add("app.tdlight.api-id is missing");
        }
        if (isBlank(tdlightProperties.apiHash())) {
            blockers.add("app.tdlight.api-hash is missing");
        }
        if (isBlank(tdlightProperties.databaseDirectory())) {
            blockers.add("app.tdlight.database-directory is missing");
        }
        if (isBlank(tdlightProperties.filesDirectory())) {
            blockers.add("app.tdlight.files-directory is missing");
        }
        if (tdlightProperties.publicChannelClientMode() == TdlightClientMode.REAL) {
            if (tdlightRuntimeAdapter == null) {
                blockers.add("TDLight runtime adapter bean is missing");
            } else if (tdlightRuntimeAdapter.getClass() == NoopTdlightRuntimeAdapter.class) {
                blockers.add("TDLight REAL runtime adapter is still backed by NoopTdlightRuntimeAdapter");
            }

            if (tdlightSessionFactory == null) {
                blockers.add("TDLight session factory bean is missing");
            } else if (tdlightSessionFactory.getClass() == NoopTdlightSessionFactory.class) {
                blockers.add("TDLight REAL session factory is still backed by NoopTdlightSessionFactory");
            }
        }

        boolean tdlightClassesPresent = classExists("it.tdlight.Init")
            && classExists("it.tdlight.client.SimpleTelegramClientFactory")
            && classExists("it.tdlight.jni.TdApi");
        if (!tdlightClassesPresent) {
            blockers.add("tdlight-java classes are missing from the classpath");
            hints.add("Add the tdlight-java dependency from the official TDLight Maven repository");
        }

        if (isBlank(tdlightProperties.nativeWorkdir())) {
            hints.add("app.tdlight.native-workdir is not set; TDLight will use its default native workdir");
        }
        if (tdlightProperties.publicChannelClientMode() == TdlightClientMode.REAL
            && tdlightNativeLibraryBootstrap != null
            && tdlightNativeLibraryBootstrap.getClass() == NoopTdlightNativeLibraryBootstrap.class) {
            hints.add("TDLight native bootstrap currently relies on NoopTdlightNativeLibraryBootstrap reflection init");
        }
        if (isBlank(tdlightProperties.sessionEncryptionKey())) {
            hints.add("app.tdlight.session-encryption-key is not set yet");
        }
        hints.add("REAL mode still requires an authorized TDLight user session for the chosen connection");

        return new TdlightRuntimeReadiness(
            blockers.isEmpty(),
            tdlightProperties.publicChannelClientMode().name(),
            tdlightClassesPresent,
            blockers,
            hints
        );
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record TdlightRuntimeReadiness(
        boolean ready,
        String clientMode,
        boolean tdlightClassesPresent,
        List<String> blockers,
        List<String> hints
    ) {
    }
}
