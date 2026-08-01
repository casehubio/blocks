package io.casehub.blocks.summarisation.examples.multiagent;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.observation.ObservationAccumulator;
import io.casehub.blocks.summarisation.observation.ObservationContext;
import io.casehub.blocks.summarisation.observation.ObservationRenderer;
import io.casehub.blocks.summarisation.observation.ObservationResult;
import io.casehub.blocks.summarisation.observation.ObservationTier;
import io.casehub.blocks.summarisation.observation.PartitionedDrain;
import io.casehub.blocks.summarisation.observation.PartitionedObservationService;
import io.casehub.blocks.summarisation.observation.TieredObservationRenderer;
import io.casehub.blocks.summarisation.observation.VisibilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-agent observation pipeline example — demonstrates how LLM agents
 * operating in a partitioned world (rooms, zones, channels) can maintain
 * bounded, relevant context using casehub-blocks observation primitives.
 *
 * <p>Models a monitoring centre where agents observe sensor zones. Each agent
 * sees events in their current zone and retains compacted memories of zones
 * they've visited. Demonstrates:
 *
 * <ul>
 *   <li>{@link PartitionedObservationService} — per-observer per-zone accumulators</li>
 *   <li>{@link VisibilityPolicy} — zone-presence routing with alert broadcast</li>
 *   <li>{@link TieredObservationRenderer} — verbatim/grouped/summarised tiers</li>
 *   <li>Mechanical compaction — supersedes stale sensor readings</li>
 *   <li>Cross-partition memory — remembered zones after agent moves</li>
 * </ul>
 */
class MultiAgentObservationPipelineTest {

    static final EventLevel SENSOR = new EventLevel("sensor", 0);

    // --- Domain model ---

    enum EventType { READING, ALERT, MOVE, STATUS }

    record SensorEvent(
            String agentId,
            String zone,
            EventType type,
            String sensorId,
            String description,
            long timestamp) {}

    // --- Mechanical compaction: supersede stale readings per sensor ---

    static List<LevelEvent<SensorEvent>> compact(List<LevelEvent<SensorEvent>> events) {
        if (events.isEmpty()) return List.of();

        var latestPerSensor = new LinkedHashMap<String, LevelEvent<SensorEvent>>();
        var nonReadings = new ArrayList<LevelEvent<SensorEvent>>();

        for (var event : events) {
            SensorEvent e = event.payload();
            if (e.type() == EventType.READING) {
                latestPerSensor.put(e.sensorId(), event);
            } else {
                nonReadings.add(event);
            }
        }

        var result = new ArrayList<>(nonReadings);
        result.addAll(latestPerSensor.values());
        result.sort(Comparator.comparingLong(LevelEvent::timestamp));
        return List.copyOf(result);
    }

    // --- Compacting renderer: compact then delegate to tiered ---

    static class CompactingRenderer implements ObservationRenderer<SensorEvent> {
        private final TieredObservationRenderer<SensorEvent> delegate;

        CompactingRenderer(int verbatimThreshold, int groupedThreshold,
                           Summariser<SensorEvent, String> summariser) {
            java.util.function.Function<SensorEvent, String> eventRenderer = e -> e.description();
            java.util.function.Function<SensorEvent, String> groupKey = e -> e.type().name();
            var tier = summariser != null
                    ? new TieredObservationRenderer<>(eventRenderer, groupKey,
                            verbatimThreshold, groupedThreshold, summariser)
                    : new TieredObservationRenderer<>(eventRenderer, groupKey,
                            verbatimThreshold);
            this.delegate = tier.withHeaderFormatter(ctx -> "");
        }

        @Override
        public CompletionStage<ObservationResult> render(
                List<LevelEvent<SensorEvent>> events, ObservationContext context) {
            if (events.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ObservationResult.empty(context.timeSinceLastDrain()));
            }
            return delegate.render(compact(events), context);
        }
    }

    // --- Visibility policy: zone presence + alert broadcast ---

    record AgentPosition(String zone) {}

    Map<String, AgentPosition> agentPositions;

    VisibilityPolicy<SensorEvent, String> zonePolicy = event -> {
        if (event.zone() == null) return Map.of();

        var result = new HashMap<String, Set<String>>();

        for (var entry : agentPositions.entrySet()) {
            String agentId = entry.getKey();
            String agentZone = entry.getValue().zone();

            if (event.type() == EventType.ALERT) {
                result.put(agentId, Set.of(agentZone));
            } else if (agentZone.equals(event.zone())) {
                result.put(agentId, Set.of(agentZone));
            }
        }
        return result;
    };

    // --- Test infrastructure ---

    PartitionedObservationService<SensorEvent, String> service;

    @BeforeEach
    void setUp() {
        agentPositions = new HashMap<>();
        agentPositions.put("alice", new AgentPosition("zone-north"));
        agentPositions.put("bob", new AgentPosition("zone-north"));

        var renderer = new CompactingRenderer(5, 10, null);
        service = new PartitionedObservationService<>(
                renderer, zonePolicy, SensorEvent::timestamp, SENSOR);

        service.addObserver("alice", "zone-north");
        service.addObserver("bob", "zone-north");
    }

    private void publish(String zone, EventType type, String sensorId,
                         String description, long ts) {
        service.publishEvent(new SensorEvent(null, zone, type, sensorId, description, ts));
    }

    private void moveAgent(String agentId, String newZone) {
        agentPositions.put(agentId, new AgentPosition(newZone));
    }

    // --- Tests ---

    @Test
    void singleZone_agentsSeeSameEvents() {
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22.5°C", 1000);
        publish("zone-north", EventType.READING, "humid-1", "Humidity: 45%", 1100);

        var aliceDrain = service.drain("alice", "zone-north", 2000);
        var bobDrain = service.drain("bob", "zone-north", 2000);

        assertThat(aliceDrain.currentPartition().eventCount()).isEqualTo(2);
        assertThat(bobDrain.currentPartition().eventCount()).isEqualTo(2);
        assertThat(aliceDrain.currentPartition().renderedText()).contains("22.5°C");
    }

    @Test
    void differentZones_agentsOnlySeeTheirZone() {
        moveAgent("bob", "zone-south");

        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22.5°C", 1000);
        publish("zone-south", EventType.READING, "temp-2", "Temperature: 18.0°C", 1100);

        var aliceDrain = service.drain("alice", "zone-north", 2000);
        var bobDrain = service.drain("bob", "zone-south", 2000);

        assertThat(aliceDrain.currentPartition().renderedText()).contains("22.5°C");
        assertThat(aliceDrain.currentPartition().renderedText()).doesNotContain("18.0°C");

        assertThat(bobDrain.currentPartition().renderedText()).contains("18.0°C");
        assertThat(bobDrain.currentPartition().renderedText()).doesNotContain("22.5°C");
    }

    @Test
    void alertBroadcast_allAgentsSeeAlertRegardlessOfZone() {
        moveAgent("bob", "zone-south");

        publish("zone-north", EventType.ALERT, "smoke-1",
                "ALERT: Smoke detected in zone-north!", 1000);

        var aliceDrain = service.drain("alice", "zone-north", 2000);
        var bobDrain = service.drain("bob", "zone-south", 2000);

        assertThat(aliceDrain.currentPartition().renderedText()).contains("Smoke detected");
        assertThat(bobDrain.currentPartition().renderedText()).contains("Smoke detected");
    }

    @Test
    void mechanicalCompaction_supersededReadingsRemoved() {
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 20.0°C", 1000);
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 21.0°C", 1100);
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22.5°C", 1200);
        publish("zone-north", EventType.STATUS, null, "System check OK", 1150);

        var drain = service.drain("alice", "zone-north", 2000);

        assertThat(drain.currentPartition().renderedText()).contains("22.5°C");
        assertThat(drain.currentPartition().renderedText()).doesNotContain("20.0°C");
        assertThat(drain.currentPartition().renderedText()).doesNotContain("21.0°C");
        assertThat(drain.currentPartition().renderedText()).contains("System check OK");
        assertThat(drain.currentPartition().eventCount()).isEqualTo(2);
    }

    @Test
    void crossZoneMemory_agentRemembersAfterMoving() {
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22.5°C", 1000);
        publish("zone-north", EventType.STATUS, null, "All sensors nominal", 1100);

        moveAgent("alice", "zone-south");

        publish("zone-south", EventType.READING, "temp-2", "Temperature: 18.0°C", 1200);

        var drain = service.drain("alice", "zone-south", 2000);

        assertThat(drain.currentPartition().renderedText()).contains("18.0°C");
        assertThat(drain.rememberedPartitions()).containsKey("zone-north");
        assertThat(drain.rememberedPartitions().get("zone-north").result().renderedText())
                .contains("22.5°C");
    }

    @Test
    void rememberedPartitions_cachedAcrossDrains() {
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22.5°C", 1000);

        moveAgent("alice", "zone-south");

        var drain1 = service.drain("alice", "zone-south", 2000);
        assertThat(drain1.rememberedPartitions().get("zone-north").cachedAt()).isEqualTo(2000);

        var drain2 = service.drain("alice", "zone-south", 5000);
        assertThat(drain2.rememberedPartitions().get("zone-north").cachedAt())
                .as("cached at first drain time, not re-drained")
                .isEqualTo(2000);
    }

    @Test
    void tieredRendering_groupedAboveThreshold() {
        var renderer = new CompactingRenderer(2, 10, null);
        var smallService = new PartitionedObservationService<>(
                renderer, zonePolicy, SensorEvent::timestamp, SENSOR);
        smallService.addObserver("alice", "zone-north");

        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22°C", 1000);
        publish("zone-north", EventType.READING, "humid-1", "Humidity: 45%", 1100);
        publish("zone-north", EventType.STATUS, null, "System nominal", 1200);

        smallService.publishEvent(new SensorEvent(null, "zone-north", EventType.READING,
                "temp-1", "Temperature: 22°C", 1000));
        smallService.publishEvent(new SensorEvent(null, "zone-north", EventType.READING,
                "humid-1", "Humidity: 45%", 1100));
        smallService.publishEvent(new SensorEvent(null, "zone-north", EventType.STATUS,
                null, "System nominal", 1200));

        var drain = smallService.drain("alice", "zone-north", 2000);

        assertThat(drain.currentPartition().tier()).isEqualTo(ObservationTier.GROUPED);
    }

    @Test
    void endToEnd_agentMonitorsMultipleZonesAndReturns() {
        publish("zone-north", EventType.READING, "temp-1", "Temperature: 22°C", 1000);
        publish("zone-north", EventType.READING, "humid-1", "Humidity: 45%", 1100);

        moveAgent("alice", "zone-south");

        publish("zone-south", EventType.READING, "temp-2", "Temperature: 18°C", 1200);
        publish("zone-south", EventType.ALERT, "smoke-2",
                "ALERT: Smoke in zone-south!", 1300);

        var southDrain = service.drain("alice", "zone-south", 2000);
        assertThat(southDrain.currentPartition().eventCount()).isGreaterThan(0);
        assertThat(southDrain.rememberedPartitions()).containsKey("zone-north");

        // Alice returns to zone-north. zone-south was drained as current (never
        // cached as remembered), so no remembered partitions exist after return.
        // This is the correct at-most-once drain semantic: the southDrain consumed
        // zone-south's buffer. To retain zone-south memories, the consumer should
        // cache the drain result externally (as wacky-manor's ObservationService does).
        moveAgent("alice", "zone-north");

        publish("zone-north", EventType.READING, "temp-1", "Temperature: 23°C", 2100);

        var returnDrain = service.drain("alice", "zone-north", 3000);
        assertThat(returnDrain.currentPartition().renderedText()).contains("23°C");
        // zone-north was cached as remembered when alice was in zone-south,
        // but it's now current again — cache is not returned for current partition
        assertThat(returnDrain.rememberedPartitions()).doesNotContainKey("zone-north");
    }
}
