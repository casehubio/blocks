package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EngagementEvidenceTest {

    @Test
    void constructsWithRequiredFields() {
        var evidence = new EngagementEvidence(
                "agent1", "subject1", "tenant1",
                "conv1", "Conversation summary",
                5, 0.8, 42.0, 0.15,
                Map.of("verbosity", 0.6), Instant.now());
        assertThat(evidence.agentId()).isEqualTo("agent1");
        assertThat(evidence.turnCount()).isEqualTo(5);
        assertThat(evidence.dimensionSnapshots()).containsEntry("verbosity", 0.6);
    }

    @Test
    void nullableFieldsAccepted() {
        var evidence = new EngagementEvidence(
                "a", "s", "t", null, null,
                0, 0, 0, 0, Map.of(), Instant.now());
        assertThat(evidence.conversationId()).isNull();
        assertThat(evidence.conversationSummary()).isNull();
    }

    @Test
    void nullAgentIdThrows() {
        assertThatNullPointerException().isThrownBy(() ->
                new EngagementEvidence(null, "s", "t", null, null,
                        0, 0, 0, 0, Map.of(), Instant.now()));
    }

    @Test
    void nullDimensionSnapshotsThrows() {
        assertThatNullPointerException().isThrownBy(() ->
                new EngagementEvidence("a", "s", "t", null, null,
                        0, 0, 0, 0, null, Instant.now()));
    }

    @Test
    void dimensionSnapshotsDefensivelyCopied() {
        var mutable = new HashMap<String, Double>();
        mutable.put("verbosity", 0.5);
        var evidence = new EngagementEvidence("a", "s", "t", null, null,
                0, 0, 0, 0, mutable, Instant.now());
        mutable.put("extra", 1.0);
        assertThat(evidence.dimensionSnapshots()).doesNotContainKey("extra");
    }
}
