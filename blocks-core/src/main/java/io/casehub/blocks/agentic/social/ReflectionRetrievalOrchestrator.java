package io.casehub.blocks.agentic.social;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;

import java.util.List;

public class ReflectionRetrievalOrchestrator {

    private final ReflectionQueryStore store;
    private volatile List<ReflectionEntry> lastReflections = List.of();

    public ReflectionRetrievalOrchestrator(ReflectionQueryStore store) {
        this.store = store;
    }

    public void tick(String agentId, String tenantId) {
        lastReflections = store.findSalient(agentId, tenantId);
    }

    public List<ReflectionEntry> lastReflections() {
        return lastReflections;
    }
}
