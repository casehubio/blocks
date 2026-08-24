package io.casehub.blocks.agentic.social.narrative;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class NarrativeOrchestrator {

    private final NarrativeStore store;
    private final ConcurrentHashMap<String, NarrativeState> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public NarrativeOrchestrator(NarrativeStore store) {
        this.store = store;
    }

    public NarrativeTick tick(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            var loaded = store.load(agentId, tenantId);
            var previous = cache.get(key);

            if (loaded == null) {
                return new NarrativeTick.NoChange("no narrative in store");
            }

            cache.put(key, loaded);

            if (previous == null) {
                return new NarrativeTick.Updated(null, loaded,
                        loaded.episodes().stream().map(IndividualEpisode::id).toList(),
                        loaded.themes().stream().map(DerivedTheme::label).toList());
            }

            if (loaded.synthesisedAt().equals(previous.synthesisedAt())) {
                return new NarrativeTick.NoChange("no new synthesis");
            }

            var newEpisodeIds = loaded.episodes().stream()
                    .map(IndividualEpisode::id)
                    .filter(id -> previous.episodes().stream()
                            .noneMatch(e -> e.id().equals(id)))
                    .toList();
            var newThemeLabels = loaded.themes().stream()
                    .map(DerivedTheme::label)
                    .filter(label -> previous.themes().stream()
                            .noneMatch(t -> t.label().equalsIgnoreCase(label)))
                    .toList();

            return new NarrativeTick.Updated(previous, loaded,
                    newEpisodeIds, newThemeLabels);
        } finally {
            lock.unlock();
        }
    }

    public Optional<NarrativeState> currentNarrative(String agentId, String tenantId) {
        return Optional.ofNullable(cache.get(agentId + ":" + tenantId));
    }
}
