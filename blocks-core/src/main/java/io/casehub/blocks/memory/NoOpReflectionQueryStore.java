package io.casehub.blocks.memory;

import java.time.Instant;
import java.util.List;

public class NoOpReflectionQueryStore implements ReflectionQueryStore {

    @Override
    public List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since) {
        return List.of();
    }

    @Override
    public int countSince(String agentId, String tenantId, Instant since) {
        return 0;
    }
}
