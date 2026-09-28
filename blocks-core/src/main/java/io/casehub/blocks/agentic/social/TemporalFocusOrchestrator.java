package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.AffectTrajectory;
import io.casehub.neocortex.cognitive.index.AffectTrajectoryAnalyzer;
import io.casehub.neocortex.cognitive.index.AttentionItem;
import io.casehub.neocortex.cognitive.index.TemporalEntry;
import io.casehub.neocortex.cognitive.index.TemporalFocus;
import io.casehub.neocortex.cognitive.index.TemporalFocusConfig;
import io.casehub.neocortex.cognitive.index.TemporalIndex;
import io.casehub.neocortex.cognitive.index.TemporalQuery;
import io.casehub.neocortex.cognitive.index.TemporalSource;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TemporalFocusOrchestrator {

    private static final MemoryDomain AFFECT_DOMAIN = new MemoryDomain("affect");
    private static final int RECENT_LIMIT = 20;
    private static final int UPCOMING_LIMIT = 10;
    private static final int AFFECT_MEMORY_LIMIT = 20;
    private static final Duration LOOKBACK = Duration.ofHours(24);

    private final TemporalIndex index;
    private final CaseMemoryStore memoryStore;
    private final TemporalFocusConfig config;
    private volatile List<AttentionItem> lastFocus = List.of();

    public TemporalFocusOrchestrator(TemporalIndex index,
                                      CaseMemoryStore memoryStore,
                                      TemporalFocusConfig config) {
        this.index = index;
        this.memoryStore = memoryStore;
        this.config = config;
    }

    public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
        var now = Instant.now();
        var tenantIds = List.of(tenantId);

        var recentQuery = TemporalQuery.since(tenantIds, now.minus(LOOKBACK), RECENT_LIMIT)
                .withEntityIds(activeSubjects);
        var upcomingQuery = TemporalQuery.upcoming(tenantIds, now, UPCOMING_LIMIT);

        var entries = new ArrayList<>(index.query(recentQuery));
        entries.addAll(index.query(upcomingQuery));

        var trajectories = computeTrajectories(entries, tenantId);
        lastFocus = TemporalFocus.focus(entries, now, trajectories, config);
    }

    public List<AttentionItem> lastFocus() {
        return lastFocus;
    }

    private Map<String, AffectTrajectory> computeTrajectories(
            List<TemporalEntry> entries, String tenantId) {
        var entityIds = entries.stream()
                .map(e -> extractEntityId(e.source()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        var trajectories = new HashMap<String, AffectTrajectory>();
        for (String entityId : entityIds) {
            var query = MemoryQuery.forSubjects(
                    List.of(Subject.of("unknown", entityId)),
                    AFFECT_DOMAIN, tenantId)
                    .withLimit(AFFECT_MEMORY_LIMIT);
            var memories = memoryStore.query(query);
            if (!memories.isEmpty()) {
                trajectories.put(entityId, AffectTrajectoryAnalyzer.analyze(memories));
            }
        }
        return trajectories;
    }

    private static @Nullable String extractEntityId(TemporalSource source) {
        return switch (source) {
            case TemporalSource.FromMindMap(var node) -> node.id();
            case TemporalSource.FromMemory(var memory) -> memory.subject().id();
            case TemporalSource.FromCbr(var cbrCase) -> cbrCase.caseId();
        };
    }
}
