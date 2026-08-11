package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.conversation.*;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConvergenceTerminationTest {

    private static final Instant T0 = Instant.parse("2026-07-21T10:00:00Z");

    static ThreadEntry entry(String sender, MessageType type, int round, String content) {
        return new ThreadEntry(null, null, type, sender, T0.plusSeconds(round * 60L),
                sender, round, "ENTRY", content);
    }

    static ConversationPoint point(String id, List<ThreadEntry> thread) {
        return new ConversationPoint(id, "general",
                new PointClassification(Priority.MEDIUM, null, null),
                thread, ConversationProtocol.STATUS_OPEN);
    }

    static ConversationState stateWith(ConversationPoint... points) {
        var map = new LinkedHashMap<String, ConversationPoint>();
        for (var p : points) map.put(p.id(), p);
        return new ConversationState(map, List.of(), List.of(), Map.of());
    }

    @Test
    void consensus_completesConversation() {
        var p1 = point("p1", List.of(
                entry("a", MessageType.COMMAND, 1, "claim"),
                entry("b", MessageType.RESPONSE, 2, "agreed")));
        var state = stateWith(p1);

        var termination = new ConvergenceTermination<ConversationState>(
                s -> s, EpistemicRules.explicitAcknowledgement(1),
                ConvergencePolicies.structural(0.95, 5),
                10, 0.5, Set.of(ConvergenceState.CONSENSUS));

        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        var decision = termination.evaluate(ctx).await().indefinitely();

        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void deadlock_escalates() {
        var p1 = point("p1", List.of(
                entry("a", MessageType.COMMAND, 1, "claim"),
                entry("b", MessageType.DECLINE, 2, "no")));
        var state = stateWith(p1);

        var termination = new ConvergenceTermination<ConversationState>(
                s -> s, EpistemicRules.commitmentResolution(),
                ConvergencePolicies.commonGroundRatio(0.8, 0.5),
                10, 0.5, Set.of(ConvergenceState.DEADLOCK));

        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        var decision = termination.evaluate(ctx).await().indefinitely();

        assertThat(decision).isInstanceOf(TerminationDecision.Escalate.class);
    }

    @Test
    void belowConfidenceThreshold_continues() {
        var p1 = point("p1", List.of(
                entry("a", MessageType.COMMAND, 1, "claim"),
                entry("b", MessageType.RESPONSE, 2, "agreed")));
        var p2 = point("p2", List.of(
                entry("a", MessageType.COMMAND, 1, "another claim")));
        var state = stateWith(p1, p2);

        var termination = new ConvergenceTermination<ConversationState>(
                s -> s, EpistemicRules.explicitAcknowledgement(1),
                ConvergencePolicies.commonGroundRatio(0.8, 0.5),
                10, 0.8, Set.of(ConvergenceState.CONSENSUS));

        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        var decision = termination.evaluate(ctx).await().indefinitely();

        assertThat(decision).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void stateNotInTerminateSet_continues() {
        var p1 = point("p1", List.of(
                entry("a", MessageType.COMMAND, 1, "claim"),
                entry("b", MessageType.RESPONSE, 2, "agreed")));
        var state = stateWith(p1);

        var termination = new ConvergenceTermination<ConversationState>(
                s -> s, EpistemicRules.explicitAcknowledgement(1),
                ConvergencePolicies.structural(0.95, 5),
                10, 0.5, Set.of(ConvergenceState.DEADLOCK));

        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        var decision = termination.evaluate(ctx).await().indefinitely();

        assertThat(decision).isInstanceOf(TerminationDecision.Continue.class);
    }
}
