package io.casehub.blocks.memory;

import java.time.Instant;
import java.util.List;

public interface ReflectionQueryStore {
    List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since);

    int countSince(String agentId, String tenantId, Instant since);
}
