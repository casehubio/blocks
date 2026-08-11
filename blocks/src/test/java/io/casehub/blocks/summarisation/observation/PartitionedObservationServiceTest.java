package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionedObservationServiceTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    record TestEvent(String observer, String partition, String text, long ts) {}

    ObservationRenderer<TestEvent> verbatimRenderer = (events, ctx) ->
            CompletableFuture.completedFuture(new ObservationResult(
                    events.stream().map(e -> e.payload().text()).reduce("", (a, b) -> a + b + "\n"),
                    List.of(), events.size(), ctx.timeSinceLastDrain(), ObservationTier.VERBATIM));

    VisibilityPolicy<TestEvent, String> simplePolicy = event ->
            Map.of(event.observer(), Set.of(event.partition()));

    PartitionedObservationService<TestEvent, String> createService() {
        return new PartitionedObservationService<>(
                verbatimRenderer, simplePolicy, TestEvent::ts, LEVEL);
    }

    @Test
    void publishAndDrain_singleObserverSinglePartition() {
        var service = createService();
        service.addObserver("alice", "room-a");

        service.publishEvent(new TestEvent("alice", "room-a", "hello", 1000));

        var drain = service.drain("alice", "room-a", 2000);
        assertThat(drain.currentPartition().eventCount()).isEqualTo(1);
        assertThat(drain.currentPartition().renderedText()).contains("hello");
        assertThat(drain.rememberedPartitions()).isEmpty();
    }

    @Test
    void publishEvent_routesOnlyToMatchingObserver() {
        var service = createService();
        service.addObserver("alice", "room-a");
        service.addObserver("bob", "room-a");

        service.publishEvent(new TestEvent("alice", "room-a", "alice-only", 1000));

        var aliceDrain = service.drain("alice", "room-a", 2000);
        assertThat(aliceDrain.currentPartition().eventCount()).isEqualTo(1);

        var bobDrain = service.drain("bob", "room-a", 2000);
        assertThat(bobDrain.currentPartition().eventCount()).isZero();
    }

    @Test
    void partitionTransition_previousPartitionBecomesRemembered() {
        var service = createService();
        service.addObserver("alice", "room-a");

        service.publishEvent(new TestEvent("alice", "room-a", "event-in-a", 1000));

        var drain = service.drain("alice", "room-b", 2000);
        assertThat(drain.currentPartition().eventCount()).isZero();
        assertThat(drain.rememberedPartitions()).containsKey("room-a");
        assertThat(drain.rememberedPartitions().get("room-a").result().eventCount()).isEqualTo(1);
    }

    @Test
    void rememberedPartition_cachedAfterFirstDrain() {
        var service = createService();
        service.addObserver("alice", "room-a");

        service.publishEvent(new TestEvent("alice", "room-a", "event-in-a", 1000));

        service.drain("alice", "room-b", 2000);
        var secondDrain = service.drain("alice", "room-b", 3000);

        assertThat(secondDrain.rememberedPartitions()).containsKey("room-a");
        assertThat(secondDrain.rememberedPartitions().get("room-a").cachedAt()).isEqualTo(2000);
    }

    @Test
    void unknownObserver_returnsEmptyDrain() {
        var service = createService();
        var drain = service.drain("nobody", "room-a", 1000);
        assertThat(drain.currentPartition().eventCount()).isZero();
        assertThat(drain.rememberedPartitions()).isEmpty();
    }

    @Test
    void multiObserverVisibility() {
        VisibilityPolicy<TestEvent, String> broadcastPolicy = event ->
                Map.of("alice", Set.of(event.partition()),
                       "bob", Set.of(event.partition()));

        var service = new PartitionedObservationService<>(
                verbatimRenderer, broadcastPolicy, TestEvent::ts, LEVEL);
        service.addObserver("alice", "room-a");
        service.addObserver("bob", "room-a");

        service.publishEvent(new TestEvent("alice", "room-a", "broadcast", 1000));

        assertThat(service.drain("alice", "room-a", 2000).currentPartition().eventCount()).isEqualTo(1);
        assertThat(service.drain("bob", "room-a", 2000).currentPartition().eventCount()).isEqualTo(1);
    }

    @Test
    void clear_removesAllObservers() {
        var service = createService();
        service.addObserver("alice", "room-a");
        service.publishEvent(new TestEvent("alice", "room-a", "hello", 1000));

        service.clear();

        var drain = service.drain("alice", "room-a", 2000);
        assertThat(drain.currentPartition().eventCount()).isZero();
    }
}
