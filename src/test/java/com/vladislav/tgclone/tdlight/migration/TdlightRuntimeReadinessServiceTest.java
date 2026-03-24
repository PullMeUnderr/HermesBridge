package com.vladislav.tgclone.tdlight.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vladislav.tgclone.tdlight.TdlightClientMode;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TdlightRuntimeReadinessServiceTest {

    @Test
    void inspectMarksRealModeAsNotReadyWhileNoopRuntimeSkeletonIsStillWired() {
        TdlightProperties properties = new TdlightProperties(
            true,
            true,
            true,
            TdlightClientMode.REAL,
            false,
            500,
            7,
            true,
            20L * 1024 * 1024,
            180,
            "dev-session-key",
            null,
            null,
            "/tmp/tdlight-db",
            "/tmp/tdlight-files",
            123456,
            "api-hash",
            "en",
            "Hermes Local Dev",
            "local-dev"
        );

        TdlightRuntimeReadinessService readinessService = new TdlightRuntimeReadinessService(
            properties,
            new NoopTdlightRuntimeAdapter(
                new NoopTdlightNativeLibraryBootstrap(),
                new NoopTdlightSessionFactory(
                    new InMemoryTdlightSessionStateStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                    new InMemoryTdlightSessionSecretStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
                )
            ),
            new NoopTdlightSessionFactory(
                new InMemoryTdlightSessionStateStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                new InMemoryTdlightSessionSecretStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            ),
            new NoopTdlightNativeLibraryBootstrap()
        );

        TdlightRuntimeReadinessService.TdlightRuntimeReadiness readiness = readinessService.inspect();

        assertFalse(readiness.ready());
        assertTrue(
            readiness.blockers().contains("TDLight REAL runtime adapter is still backed by NoopTdlightRuntimeAdapter")
        );
        assertTrue(
            readiness.blockers().contains("TDLight REAL session factory is still backed by NoopTdlightSessionFactory")
        );
        assertTrue(
            readiness.hints().contains(
                "TDLight native bootstrap currently relies on NoopTdlightNativeLibraryBootstrap reflection init"
            )
        );
    }
}
