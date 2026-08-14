package io.casehub.blocks.agentic.intention;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JointIntentionTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");

    @Nested
    class Lifecycle {
        @Test
        void formCreatesFreshIntention() {
            var ji = JointIntention.form("ji-1", "Investigate case", Set.of("a", "b"), T1);
            assertThat(ji.status()).isEqualTo(IntentionStatus.FORMED);
            assertThat(ji.committedParties()).containsExactlyInAnyOrder("a", "b");
            assertThat(ji.dropReason()).isNull();
        }

        @Test
        void activateTransitionsToActive() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1).activate();
            assertThat(ji.status()).isEqualTo(IntentionStatus.ACTIVE);
        }

        @Test
        void reconsiderTransitionsToReconsidering() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1)
                    .activate().reconsider();
            assertThat(ji.status()).isEqualTo(IntentionStatus.RECONSIDERING);
        }

        @Test
        void dropTransitionsToDroppedWithReason() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1)
                    .activate().drop("conditions changed");
            assertThat(ji.status()).isEqualTo(IntentionStatus.DROPPED);
            assertThat(ji.dropReason()).isEqualTo("conditions changed");
            assertThat(ji.status().isTerminal()).isTrue();
        }

        @Test
        void fulfillTransitionsToFulfilled() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1)
                    .activate().fulfill();
            assertThat(ji.status()).isEqualTo(IntentionStatus.FULFILLED);
            assertThat(ji.status().isTerminal()).isTrue();
        }
    }

    @Nested
    class PartyManagement {
        @Test
        void droppingPartyRemovesFromSet() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a", "b", "c"), T1)
                    .activate().withPartyDropped("b");
            assertThat(ji.committedParties()).containsExactlyInAnyOrder("a", "c");
        }

        @Test
        void droppingLastPartyAutoDropsIntention() {
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1)
                    .activate().withPartyDropped("a");
            assertThat(ji.status()).isEqualTo(IntentionStatus.DROPPED);
            assertThat(ji.committedParties()).isEmpty();
            assertThat(ji.dropReason()).isEqualTo("All parties dropped");
        }
    }

    @Nested
    class StatusPredicates {
        @Test
        void activeStates() {
            assertThat(IntentionStatus.ACTIVE.isActive()).isTrue();
            assertThat(IntentionStatus.RECONSIDERING.isActive()).isTrue();
            assertThat(IntentionStatus.FORMED.isActive()).isFalse();
            assertThat(IntentionStatus.DROPPED.isActive()).isFalse();
        }

        @Test
        void terminalStates() {
            assertThat(IntentionStatus.DROPPED.isTerminal()).isTrue();
            assertThat(IntentionStatus.FULFILLED.isTerminal()).isTrue();
            assertThat(IntentionStatus.ACTIVE.isTerminal()).isFalse();
        }
    }

    @Nested
    class Monitor {
        @Test
        void monitorReturnsSignalWhenConditionMet() {
            IntentionMonitor monitor = intention ->
                    intention.committedParties().size() < 2
                            ? ReconsiderationSignal.drop(ReconsiderationReason.PARTY_DROPPED, "Too few members")
                            : null;
            var ji = JointIntention.form("ji-1", "plan", Set.of("a"), T1).activate();
            var signal = monitor.evaluate(ji);
            assertThat(signal).isNotNull();
            assertThat(signal.shouldDrop()).isTrue();
            assertThat(signal.reason()).isEqualTo(ReconsiderationReason.PARTY_DROPPED);
        }

        @Test
        void monitorReturnsNullWhenHealthy() {
            IntentionMonitor monitor = intention ->
                    intention.committedParties().size() < 2
                            ? ReconsiderationSignal.drop(ReconsiderationReason.PARTY_DROPPED, "Too few")
                            : null;
            var ji = JointIntention.form("ji-1", "plan", Set.of("a", "b"), T1).activate();
            assertThat(monitor.evaluate(ji)).isNull();
        }
    }
}
