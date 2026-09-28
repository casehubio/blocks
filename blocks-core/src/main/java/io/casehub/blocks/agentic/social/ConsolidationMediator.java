package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.ConsolidationArtifact;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConsolidationMediator {

    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> agentLastSeen = new ConcurrentHashMap<>();

    void onConsolidation(@Observes ConsolidationCompleted event) {
        if (event.artifacts() == null || event.artifacts().isEmpty()) return;
        snapshots.put(event.tenantId(), new Snapshot(event.artifacts(), Instant.now()));
    }

    public List<ConsolidationArtifact> drainForAgent(String agentId, String tenantId) {
        var snapshot = snapshots.get(tenantId);
        if (snapshot == null) return List.of();
        var seenKey = tenantId + ":" + agentId;
        var lastSeen = agentLastSeen.get(seenKey);
        if (lastSeen != null && !snapshot.timestamp.isAfter(lastSeen)) return List.of();
        agentLastSeen.put(seenKey, snapshot.timestamp);
        return snapshot.artifacts.stream()
            .filter(a -> isRelevant(a, agentId))
            .toList();
    }

    private static boolean isRelevant(ConsolidationArtifact a, String agentId) {
        return switch (a) {
            case ConsolidationArtifact.GraduatedExperience e -> e.agentId().equals(agentId);
            case ConsolidationArtifact.MergePerformed m -> true;
            case ConsolidationArtifact.MergeFlagged m -> true;
            case ConsolidationArtifact.CuriosityQuestion q -> true;
            case ConsolidationArtifact.CommunitySummaryCreated c -> true;
        };
    }

    record Snapshot(List<ConsolidationArtifact> artifacts, Instant timestamp) {}
}
