package io.casehub.blocks.memory;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@DefaultBean
@ApplicationScoped
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
