package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agent.KeyedLock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupNarrativeOrchestrator {

    private final NarrativeStore store;
    private final Set<String> memberIds;
    private final ConcurrentHashMap<String, NarrativeState> cache = new ConcurrentHashMap<>();
    private final KeyedLock tickLocks = new KeyedLock();

    public GroupNarrativeOrchestrator(NarrativeStore store, Set<String> memberIds) {
        this.store = Objects.requireNonNull(store);
        this.memberIds = Set.copyOf(memberIds);
    }

    public NarrativeTick tick(String groupId, String tenantId) {
        var key = groupId + ":" + tenantId;
        return tickLocks.withLock(key, () -> {
            var loaded = store.load(groupId, tenantId);
            var previous = cache.get(key);

            if (loaded == null) {
                return new NarrativeTick.NoChange("no group narrative in store");
            }

            cache.put(key, loaded);

            if (previous == null) {
                return new NarrativeTick.Updated(null, loaded,
                        loaded.groupEpisodes().stream().map(GroupEpisode::id).toList(),
                        loaded.themes().stream().map(DerivedTheme::label).toList());
            }

            if (loaded.synthesisedAt().equals(previous.synthesisedAt())) {
                return new NarrativeTick.NoChange("no new synthesis");
            }

            var newEpisodeIds = loaded.groupEpisodes().stream()
                    .map(GroupEpisode::id)
                    .filter(id -> previous.groupEpisodes().stream()
                            .noneMatch(e -> e.id().equals(id)))
                    .toList();
            var newThemeLabels = loaded.themes().stream()
                    .map(DerivedTheme::label)
                    .filter(label -> previous.themes().stream()
                            .noneMatch(t -> t.label().equalsIgnoreCase(label)))
                    .toList();

            return new NarrativeTick.Updated(previous, loaded, newEpisodeIds, newThemeLabels);
        });
    }

    public Optional<NarrativeState> currentNarrative(String groupId, String tenantId) {
        return Optional.ofNullable(cache.get(groupId + ":" + tenantId));
    }

    public Set<String> members() {
        return memberIds;
    }
}
