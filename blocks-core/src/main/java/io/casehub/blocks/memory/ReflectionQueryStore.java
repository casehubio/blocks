package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.ReflectionEntry;
import java.time.Instant;
import java.util.List;

public interface ReflectionQueryStore extends io.casehub.neocortex.memory.ReflectionQueryStore {

    // Methods override the neocortex SPI with matching signatures
    List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since);

    int countSince(String agentId, String tenantId, Instant since);

    default List<ReflectionEntry> findSalient(String agentId, String tenantId) {
        return List.of();
    }
}
