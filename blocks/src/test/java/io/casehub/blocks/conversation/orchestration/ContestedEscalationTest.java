package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationFold;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.PointClassification;
import io.casehub.blocks.conversation.Priority;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContestedEscalationTest {

    private final ContestedEscalation termination = new ContestedEscalation(2);

    @Test
    void noDisputes_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void disputesBelowThreshold_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "DISPUTE", "Disagree", "DISPUTED");
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void disputesExceedThreshold_escalates() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "design",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "DISPUTE", "No", "DISPUTED");
        state = ConversationFold.respondToPoint(state, "p1",
                3L, null, "alice", Instant.now(),
                "REV", 2, "DISPUTE", "Yes", "DISPUTED");
        state = ConversationFold.respondToPoint(state, "p1",
                4L, null, "bob", Instant.now(),
                "IMP", 2, "DISPUTE", "Still no", "DISPUTED");
        var decision = evaluate(state);
        assertThat(decision).isInstanceOf(TerminationDecision.Escalate.class);
    }

    private TerminationDecision evaluate(ConversationState state) {
        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        return termination.evaluate(ctx);
    }
}
