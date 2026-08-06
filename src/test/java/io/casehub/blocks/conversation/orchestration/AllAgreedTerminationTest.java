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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllAgreedTerminationTest {

    private final AllAgreedTermination termination =
            new AllAgreedTermination(Set.of("AGREED", "VERIFIED"));

    @Test
    void noPoints_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void allPointsResolved_completes() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "AGREE", "ok", "AGREED");
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void somePointsUnresolved_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "t1",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "c1");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "AGREE", "ok", "AGREED");
        state = ConversationFold.createPoint(state, "p2", "t2",
                3L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "c2");
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void customResolvedStatuses_respected() {
        var custom = new AllAgreedTermination(Set.of("DONE"));
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "AGREE", "ok", "AGREED");
        assertThat(evaluate(state, custom)).isInstanceOf(TerminationDecision.Continue.class);
    }

    private TerminationDecision evaluate(ConversationState state) {
        return evaluate(state, termination);
    }

    private TerminationDecision evaluate(ConversationState state,
                                          AllAgreedTermination t) {
        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        return t.evaluate(ctx).await().indefinitely();
    }
}
