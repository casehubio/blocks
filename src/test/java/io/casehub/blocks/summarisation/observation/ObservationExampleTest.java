package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationExampleTest {

    record GameEvent(String actor, String action, String category) {}

    static final EventLevel L1_EVENTS = new EventLevel("game-events", 1);

    EventStreamBus<GameEvent> eventBus;
    ObservationAccumulator<GameEvent> playerAccumulator;

    @BeforeEach
    void setUp() {
        eventBus = new EventStreamBus<>();

        Summariser<GameEvent, String> gameSummariser = batch -> {
            long actors = batch.stream()
                    .map(e -> e.payload().actor()).distinct().count();
            return CompletableFuture.completedFuture(List.of(
                    "While you were away, " + actors + " characters were active "
                    + "across " + batch.size() + " events."));
        };

        var renderer = new TieredObservationRenderer<>(
                e -> e.actor() + " " + e.action(),
                GameEvent::category,
                3, 8, gameSummariser);

        playerAccumulator = new ObservationAccumulator<>(renderer, 0);
        eventBus.subscribe(e -> true, playerAccumulator::collect);
    }

    @Test
    void verbatimTier_fewEvents_fullDetail() {
        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("Penelope", "asked about a brass key", "dialogue", 2000);

        var obs = playerAccumulator.drainObservation(3000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(obs.eventCount()).isEqualTo(2);
        assertThat(obs.renderedText()).contains("Dastardly entered the Kitchen");
        assertThat(obs.renderedText()).contains("Penelope asked about a brass key");
        assertThat(obs.chunks()).hasSize(2);
        assertThat(obs.chunks().get(0).content())
                .isEqualTo("Dastardly entered the Kitchen");
    }

    @Test
    void groupedTier_moderateEvents_groupedByCategory() {
        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("AntHillMob", "passed through", "movement", 2000);
        publish("Penelope", "said hello", "dialogue", 3000);
        publish("Peter", "offered to help", "dialogue", 4000);
        publish("Dastardly", "tried the cabinet", "interaction", 5000);

        var obs = playerAccumulator.drainObservation(6000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.GROUPED);
        assertThat(obs.eventCount()).isEqualTo(5);
        assertThat(obs.chunks()).extracting(c -> c.metadata().get("groupKey"))
                .containsExactly("movement", "dialogue", "interaction");
        assertThat(obs.chunks().get(0).eventCount()).isEqualTo(2);
        assertThat(obs.chunks().get(1).eventCount()).isEqualTo(2);
        assertThat(obs.chunks().get(2).eventCount()).isEqualTo(1);
    }

    @Test
    void summarisedTier_manyEvents_singleSummary() {
        for (int i = 0; i < 10; i++) {
            publish("Actor" + (i % 3), "did action " + i, "cat" + (i % 2), 1000 + i * 100);
        }

        var obs = playerAccumulator.drainObservation(3000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.SUMMARISED);
        assertThat(obs.eventCount()).isEqualTo(10);
        assertThat(obs.chunks()).hasSize(1);
        assertThat(obs.chunks().get(0).content())
                .contains("3 characters were active")
                .contains("10 events");
    }

    @Test
    void multipleDrains_timeTracking() {
        publish("Dastardly", "entered", "movement", 100);
        var obs1 = playerAccumulator.drainObservation(500)
                .toCompletableFuture().join();
        assertThat(obs1.timeSinceLastDrain()).isEqualTo(500);

        publish("Penelope", "spoke", "dialogue", 700);
        var obs2 = playerAccumulator.drainObservation(1000)
                .toCompletableFuture().join();
        assertThat(obs2.timeSinceLastDrain()).isEqualTo(500);
    }

    @Test
    void visibilityFilter_agentSeesOnlyRelevantEvents() {
        var kitchenAccumulator = new ObservationAccumulator<>(
                new TieredObservationRenderer<>(
                        e -> e.actor() + " " + e.action(),
                        GameEvent::category, 5),
                0);
        eventBus.subscribe(
                e -> e.action().contains("Kitchen"),
                kitchenAccumulator::collect);

        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("AntHillMob", "went to the Ballroom", "movement", 2000);
        publish("Penelope", "left the Kitchen", "movement", 3000);

        var obs = kitchenAccumulator.drainObservation(4000)
                .toCompletableFuture().join();
        assertThat(obs.eventCount()).isEqualTo(2);
        assertThat(obs.renderedText()).contains("Kitchen");
        assertThat(obs.renderedText()).doesNotContain("Ballroom");
    }

    private void publish(String actor, String action, String category, long timestamp) {
        eventBus.publish(new LevelEvent<>(
                new GameEvent(actor, action, category), timestamp, L1_EVENTS));
    }
}
