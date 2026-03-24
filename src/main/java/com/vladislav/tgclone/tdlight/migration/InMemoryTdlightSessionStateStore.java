package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
@ConditionalOnMissingBean(TdlightSessionStateStore.class)
public class InMemoryTdlightSessionStateStore implements TdlightSessionStateStore {

    private final Clock clock;
    private final Map<Long, TdlightSessionBinding> bindings = new ConcurrentHashMap<>();

    public InMemoryTdlightSessionStateStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<TdlightSessionBinding> findBinding(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bindings.get(connection.getId()));
    }

    @Override
    public TdlightSessionBinding createBinding(
        TdlightConnection connection,
        String databaseDirectory,
        String filesDirectory
    ) {
        Long connectionId = requireConnectionId(connection);
        TdlightSessionBinding binding = new TdlightSessionBinding(
            connectionId,
            "tdlight-session-" + connectionId,
            databaseDirectory,
            filesDirectory,
            clock.instant()
        );
        bindings.put(connectionId, binding);
        return binding;
    }

    @Override
    public void revokeBinding(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return;
        }
        bindings.remove(connection.getId());
    }

    private Long requireConnectionId(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            throw new IllegalArgumentException("TDLight connection id is required for session binding");
        }
        return connection.getId();
    }
}
