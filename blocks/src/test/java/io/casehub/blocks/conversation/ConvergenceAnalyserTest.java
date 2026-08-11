package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConvergenceAnalyserTest {

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

    @Nested
    class StructuralPolicy {

        @Test
        void highSimilarity_isDeadlock() {
            var p = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "we should use approach X"),
                    entry("b", MessageType.RESPONSE, 2, "we should use approach X"),
                    entry("a", MessageType.RESPONSE, 3, "yes use approach X")));
            var state = stateWith(p);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.structural(0.5, 5), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.DEADLOCK);
        }

        @Test
        void staleRounds_isDiminishingReturns() {
            var p1 = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "point one")));
            var p2 = point("p2", List.of(
                    entry("b", MessageType.COMMAND, 1, "point two"),
                    entry("a", MessageType.STATUS, 10, "still here")));
            var state = stateWith(p1, p2);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.structural(0.95, 3), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.DIMINISHING_RETURNS);
        }

        @Test
        void allEstablished_isConsensus() {
            var p1 = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "claim one"),
                    entry("b", MessageType.RESPONSE, 2, "agreed")));
            var p2 = point("p2", List.of(
                    entry("a", MessageType.COMMAND, 1, "claim two"),
                    entry("b", MessageType.RESPONSE, 2, "agreed too")));
            var state = stateWith(p1, p2);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.structural(0.95, 5), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.CONSENSUS);
        }

        @Test
        void emptyState_isProgressing() {
            var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.structural(0.8, 3), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.PROGRESSING);
        }
    }

    @Nested
    class CommonGroundRatioPolicy {

        @Test
        void aboveConsensusThreshold_isConsensus() {
            var p1 = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "A"),
                    entry("b", MessageType.DONE, 2, "done")));
            var state = stateWith(p1);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.commitmentResolution());
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.commonGroundRatio(0.8, 0.5), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.CONSENSUS);
        }

        @Test
        void aboveDeadlockThreshold_isDeadlock() {
            var p1 = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "A"),
                    entry("b", MessageType.DECLINE, 2, "no")));
            var state = stateWith(p1);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.commitmentResolution());
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.commonGroundRatio(0.8, 0.5), 10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.DEADLOCK);
        }
    }

    @Nested
    class CompositePolicy {

        @Test
        void highestConfidence_wins() {
            var p1 = point("p1", List.of(
                    entry("a", MessageType.COMMAND, 1, "claim"),
                    entry("b", MessageType.DONE, 2, "done")));
            var state = stateWith(p1);
            var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.commitmentResolution());
            var signal = ConvergenceAnalyser.analyse(state, cg,
                    ConvergencePolicies.composite(
                            ConvergencePolicies.structural(0.95, 10),
                            ConvergencePolicies.commonGroundRatio(0.8, 0.5)),
                    10);
            assertThat(signal.state()).isEqualTo(ConvergenceState.CONSENSUS);
        }
    }

    @Nested
    class JaccardSimilarity {

        @Test
        void identicalTokens_returnsOne() {
            assertThat(ConvergenceAnalyser.jaccardSimilarity(
                    ConvergenceAnalyser.tokenize("hello world"),
                    ConvergenceAnalyser.tokenize("hello world")))
                    .isEqualTo(1.0);
        }

        @Test
        void disjointTokens_returnsZero() {
            assertThat(ConvergenceAnalyser.jaccardSimilarity(
                    ConvergenceAnalyser.tokenize("hello world"),
                    ConvergenceAnalyser.tokenize("foo bar")))
                    .isEqualTo(0.0);
        }

        @Test
        void partialOverlap_returnsFraction() {
            double sim = ConvergenceAnalyser.jaccardSimilarity(
                    ConvergenceAnalyser.tokenize("hello world foo"),
                    ConvergenceAnalyser.tokenize("hello world bar"));
            assertThat(sim).isEqualTo(0.5);
        }

        @Test
        void emptyBoth_returnsOne() {
            assertThat(ConvergenceAnalyser.jaccardSimilarity(Set.of(), Set.of()))
                    .isEqualTo(1.0);
        }

        @Test
        void oneEmpty_returnsZero() {
            assertThat(ConvergenceAnalyser.jaccardSimilarity(
                    ConvergenceAnalyser.tokenize("hello"), Set.of()))
                    .isEqualTo(0.0);
        }
    }
}
