package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentalModelSnapshotTest {

    @Test
    void validSnapshot() {
        var now = Instant.now();
        var belief = new AttributedState("risk", "high risk", 0.8, 2, now, BdiDimension.BELIEF);
        var desire = new AttributedState("resolution", "quick fix", 0.6, 0, now, BdiDimension.DESIRE);
        var snapshot = new MentalModelSnapshot("agent1", "user1", "tenant1",
                List.of(belief), List.of(desire), List.of(), now, now, now);
        assertThat(snapshot.beliefs()).hasSize(1);
        assertThat(snapshot.desires()).hasSize(1);
        assertThat(snapshot.intentions()).isEmpty();
    }

    @Test
    void listsAreDefensivelyCopied() {
        var now = Instant.now();
        var beliefs = new ArrayList<>(List.of(
                new AttributedState("k", "v", 0.5, 0, now, BdiDimension.BELIEF)));
        var snapshot = new MentalModelSnapshot("a", "u", "t",
                beliefs, List.of(), List.of(), now, null, now);
        beliefs.clear();
        assertThat(snapshot.beliefs()).hasSize(1);
    }

    @Test
    void nullAgentIdThrows() {
        assertThatThrownBy(() -> new MentalModelSnapshot(null, "u", "t",
                List.of(), List.of(), List.of(), Instant.now(), null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullBeliefsThrows() {
        assertThatThrownBy(() -> new MentalModelSnapshot("a", "u", "t",
                null, List.of(), List.of(), Instant.now(), null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLastSignalThrows() {
        assertThatThrownBy(() -> new MentalModelSnapshot("a", "u", "t",
                List.of(), List.of(), List.of(), null, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLastInferenceIsAllowed() {
        var now = Instant.now();
        var snapshot = new MentalModelSnapshot("a", "u", "t",
                List.of(), List.of(), List.of(), now, null, now);
        assertThat(snapshot.lastInference()).isNull();
    }
}
