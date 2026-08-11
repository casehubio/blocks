package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonGroundAnalyserTest {

    private static final Instant T0 = Instant.parse("2026-07-21T10:00:00Z");

    static ConversationState emptyState() {
        return new ConversationState(Map.of(), List.of(), List.of(), Map.of());
    }

    static ThreadEntry entry(String sender, MessageType type, String role, int round, String entryType, String content) {
        return new ThreadEntry(null, null, type, sender, T0.plusSeconds(round * 60L), role, round, entryType, content);
    }

    static ConversationPoint point(String id, String topic, List<ThreadEntry> thread) {
        return new ConversationPoint(id, topic,
                new PointClassification(Priority.MEDIUM, null, null),
                thread, ConversationProtocol.STATUS_OPEN);
    }

    static ConversationState stateWith(ConversationPoint... points) {
        var map = new LinkedHashMap<String, ConversationPoint>();
        for (var p : points) map.put(p.id(), p);
        return new ConversationState(map, List.of(), List.of(), Map.of());
    }

    @Nested
    class ExplicitAcknowledgement {

        @Test
        void noResponses_isPending() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.pendingClaims()).containsKey("p1");
        }

        @Test
        void oneAck_meetsThreshold_isEstablished() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "agreed")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.establishedFacts()).containsKey("p1");
        }

        @Test
        void belowThreshold_isPending() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "agreed")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(2));
            assertThat(result.pendingClaims()).containsKey("p1");
        }

        @Test
        void decline_isDisputed() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.DECLINE, "IMP", 2, "REJECT", "disagree")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.disputedPoints()).containsKey("p1");
        }

        @Test
        void done_countsAsAcknowledgement() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "do this"),
                    entry("bob", MessageType.DONE, "IMP", 2, "ACCEPT", "done")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.establishedFacts()).containsKey("p1");
        }

        @Test
        void status_doesNotCountAsAcknowledgement() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.STATUS, "IMP", 2, "ACCEPT", "working on it")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.pendingClaims()).containsKey("p1");
        }
    }

    @Nested
    class TacitAcceptance {

        @Test
        void zeroResponses_neverEstablished() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim")));
            var p2 = point("p2", "t", List.of(
                    entry("carol", MessageType.COMMAND, "REV", 11, "RAISE", "other")));
            var result = CommonGroundAnalyser.analyse(stateWith(p, p2),
                    EpistemicRules.tacitAcceptance(3));
            assertThat(result.pendingClaims()).containsKey("p1");
        }

        @Test
        void respondedAndStale_isEstablished() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.STATUS, "IMP", 2, "ACCEPT", "noted")));
            var p2 = point("p2", "t", List.of(
                    entry("carol", MessageType.COMMAND, "REV", 6, "RAISE", "other")));
            var result = CommonGroundAnalyser.analyse(stateWith(p, p2),
                    EpistemicRules.tacitAcceptance(3));
            assertThat(result.establishedFacts()).containsKey("p1");
        }

        @Test
        void decline_blocksEstablishment() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.DECLINE, "IMP", 2, "REJECT", "no")));
            var p2 = point("p2", "t", List.of(
                    entry("carol", MessageType.COMMAND, "REV", 6, "RAISE", "other")));
            var result = CommonGroundAnalyser.analyse(stateWith(p, p2),
                    EpistemicRules.tacitAcceptance(3));
            assertThat(result.disputedPoints()).containsKey("p1");
        }

        @Test
        void failure_blocksEstablishment() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "do this"),
                    entry("bob", MessageType.FAILURE, "IMP", 2, "ACCEPT", "failed")));
            var p2 = point("p2", "t", List.of(
                    entry("carol", MessageType.COMMAND, "REV", 6, "RAISE", "other")));
            var result = CommonGroundAnalyser.analyse(stateWith(p, p2),
                    EpistemicRules.tacitAcceptance(3));
            assertThat(result.disputedPoints()).containsKey("p1");
        }
    }

    @Nested
    class CommitmentResolution {

        @Test
        void done_isEstablished() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "do X"),
                    entry("bob", MessageType.DONE, "IMP", 2, "ACCEPT", "done")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.commitmentResolution());
            assertThat(result.establishedFacts()).containsKey("p1");
        }

        @Test
        void responseOnly_isPending() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "do X"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "acknowledged")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.commitmentResolution());
            assertThat(result.pendingClaims()).containsKey("p1");
        }

        @Test
        void failure_isDisputed() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "do X"),
                    entry("bob", MessageType.FAILURE, "IMP", 2, "ACCEPT", "failed")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.commitmentResolution());
            assertThat(result.disputedPoints()).containsKey("p1");
        }
    }

    @Nested
    class Composition {

        @Test
        void and_returnsConservative() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "ok")));
            var rule = EpistemicRules.explicitAcknowledgement(1)
                    .and(EpistemicRules.commitmentResolution());
            var result = CommonGroundAnalyser.analyse(stateWith(p), rule);
            assertThat(result.pendingClaims()).containsKey("p1");
        }

        @Test
        void or_returnsPermissive() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "ok")));
            var rule = EpistemicRules.explicitAcknowledgement(1)
                    .or(EpistemicRules.commitmentResolution());
            var result = CommonGroundAnalyser.analyse(stateWith(p), rule);
            assertThat(result.establishedFacts()).containsKey("p1");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyState_producesEmptyCommonGround() {
            var result = CommonGroundAnalyser.analyse(emptyState(),
                    EpistemicRules.explicitAcknowledgement(1));
            assertThat(result.establishedFacts()).isEmpty();
            assertThat(result.pendingClaims()).isEmpty();
            assertThat(result.disputedPoints()).isEmpty();
        }

        @Test
        void groundedFact_containsFirstThreadContent() {
            var p = point("p1", "review", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "the claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "agreed")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            var fact = result.establishedFacts().get("p1");
            assertThat(fact.content()).isEqualTo("the claim");
            assertThat(fact.topic()).isEqualTo("review");
            assertThat(fact.round()).isEqualTo(1);
        }

        @Test
        void groundedFact_acknowledgedByContainsCorrectSenders() {
            var p = point("p1", "t", List.of(
                    entry("alice", MessageType.COMMAND, "REV", 1, "RAISE", "claim"),
                    entry("bob", MessageType.RESPONSE, "IMP", 2, "ACCEPT", "ok"),
                    entry("carol", MessageType.DONE, "IMP2", 3, "ACCEPT", "done")));
            var result = CommonGroundAnalyser.analyse(stateWith(p),
                    EpistemicRules.explicitAcknowledgement(1));
            var fact = result.establishedFacts().get("p1");
            assertThat(fact.acknowledgedBy()).containsExactlyInAnyOrder("bob", "carol");
        }
    }
}
