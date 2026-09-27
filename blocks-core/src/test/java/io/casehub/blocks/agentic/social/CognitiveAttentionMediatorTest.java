package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveAttentionMediatorTest {

    private final CognitiveAttentionMediator mediator = new CognitiveAttentionMediator();

    @Test
    void drainReturnsEmptyWhenNoEvents() {
        assertThat(mediator.drainAttention("agent1")).isEmpty();
    }

    @Test
    void drainReturnsBriefingAfterEvent() {
        var briefing = briefing("agent1", "tenant1", SignalCategory.URGENCY_SPIKE, 0.9);
        mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

        var result = mediator.drainAttention("agent1");
        assertThat(result).isPresent();
        assertThat(result.get().signals()).hasSize(1);
        assertThat(result.get().signals().get(0).category()).isEqualTo(SignalCategory.URGENCY_SPIKE);
    }

    @Test
    void drainClearsQueue() {
        var briefing = briefing("agent1", "tenant1", SignalCategory.URGENCY_SPIKE, 0.9);
        mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

        mediator.drainAttention("agent1");
        assertThat(mediator.drainAttention("agent1")).isEmpty();
    }

    @Test
    void perPrincipalIsolation() {
        mediator.onAttentionRequired(new CognitiveAttentionRequired(
            briefing("agent1", "t", SignalCategory.URGENCY_SPIKE, 0.9), Instant.now()));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(
            briefing("agent2", "t", SignalCategory.DECAY_DETECTED, 0.5), Instant.now()));

        var r1 = mediator.drainAttention("agent1");
        var r2 = mediator.drainAttention("agent2");
        assertThat(r1).isPresent();
        assertThat(r1.get().signals().get(0).category()).isEqualTo(SignalCategory.URGENCY_SPIKE);
        assertThat(r2).isPresent();
        assertThat(r2.get().signals().get(0).category()).isEqualTo(SignalCategory.DECAY_DETECTED);
    }

    @Test
    void mergeMultipleBriefings() {
        var earlier = Instant.parse("2026-09-27T10:00:00Z");
        var later = Instant.parse("2026-09-27T10:05:00Z");

        var b1 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.9, "deadline")),
            0.7, earlier);
        var b2 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.DECAY_DETECTED, "node2", "Spanish", 0.5, "no activity")),
            0.85, later);

        mediator.onAttentionRequired(new CognitiveAttentionRequired(b1, earlier));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(b2, later));

        var result = mediator.drainAttention("agent1").orElseThrow();
        assertThat(result.urgencyP75()).isEqualTo(0.85);
        assertThat(result.generatedAt()).isEqualTo(later);
        assertThat(result.signals()).hasSize(2);
        assertThat(result.signals().get(0).significance())
            .isGreaterThanOrEqualTo(result.signals().get(1).significance());
    }

    @Test
    void mergeDeduplicatesBySourceNodeIdAndCategory() {
        var b1 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.7, "earlier")),
            0.7, Instant.now());
        var b2 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.9, "later")),
            0.8, Instant.now().plusSeconds(10));

        mediator.onAttentionRequired(new CognitiveAttentionRequired(b1, Instant.now()));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(b2, Instant.now()));

        var result = mediator.drainAttention("agent1").orElseThrow();
        assertThat(result.signals()).hasSize(1);
        assertThat(result.signals().get(0).significance()).isEqualTo(0.9);
    }

    private static AttentionBriefing briefing(String principalId, String tenantId,
                                               SignalCategory category, double significance) {
        return new AttentionBriefing(principalId, tenantId,
            List.of(signal(category, "node-1", "Source", significance, "reason")),
            significance, Instant.now());
    }

    private static AttentionSignal signal(SignalCategory category, String nodeId,
                                           String name, double significance, String reason) {
        return new AttentionSignal("agent1", "t", category, nodeId, name, significance, reason);
    }
}
