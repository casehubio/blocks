package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectionEventAdapter {

    private final ReflectionQueryStore reflectionQueryStore;
    private final EventStreamBus<ReflectionEntry> outputBus;
    private final EventLevel outputLevel;
    private final ConcurrentHashMap<String, Instant> watermarks =
            new ConcurrentHashMap<>();

    public ReflectionEventAdapter(
            ReflectionQueryStore reflectionQueryStore,
            EventStreamBus<ReflectionEntry> outputBus,
            EventLevel outputLevel) {
        this.reflectionQueryStore = reflectionQueryStore;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void publishNewReflections(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var since = watermarks.getOrDefault(key, Instant.EPOCH);
        var reflections = reflectionQueryStore.findSince(
                agentId, tenantId, since);
        if (reflections.isEmpty()) return;

        for (var r : reflections) {
            outputBus.publish(new LevelEvent<>(
                    r, r.generatedAt().toEpochMilli(),
                    outputLevel, tenantId));
        }

        var latest = reflections.getLast().generatedAt();
        watermarks.put(key, latest);
    }
}
