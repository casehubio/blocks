package io.casehub.blocks.normative;

import io.casehub.api.spi.RiskDecision;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionStrategyTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");

    @Nested
    class Priority {
        private final ConflictResolutionStrategy<String> strategy = new PriorityResolution<>();

        @Test
        void highestPriorityWins() {
            var low = new NormDecision<>("a", "approve", 10, NormSpecificity.UNIVERSAL, T1);
            var high = new NormDecision<>("b", "deny", 1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(low, high));
            assertThat(result.winner()).isEqualTo(high);
            assertThat(result.overridden()).containsExactly(low);
            assertThat(result.method()).isEqualTo(ResolutionMethod.PRIORITY);
        }

        @Test
        void tieBreaksByPosition() {
            var a = new NormDecision<>("a", "x", 1, NormSpecificity.UNIVERSAL, T1);
            var b = new NormDecision<>("b", "y", 1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(a, b));
            assertThat(result.winner().source()).isEqualTo("a");
        }

        @Test
        void threeWayConflict() {
            var low = new NormDecision<>("c", "z", 10, NormSpecificity.UNIVERSAL, T1);
            var mid = new NormDecision<>("b", "y", 5, NormSpecificity.UNIVERSAL, T1);
            var high = new NormDecision<>("a", "x", 1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(low, mid, high));
            assertThat(result.winner().source()).isEqualTo("a");
            assertThat(result.overridden()).hasSize(2);
        }
    }

    @Nested
    class Specificity {
        private final ConflictResolutionStrategy<String> strategy = new SpecificityResolution<>();

        @Test
        void mostSpecificWins() {
            var general = new NormDecision<>("a", "approve", 1, NormSpecificity.UNIVERSAL, T1);
            var specific = new NormDecision<>("b", "deny", 1, NormSpecificity.INSTANCE, T1);
            var result = strategy.resolve(List.of(general, specific));
            assertThat(result.winner()).isEqualTo(specific);
            assertThat(result.method()).isEqualTo(ResolutionMethod.SPECIFICITY);
        }

        @Test
        void tenantBeatsUniversal() {
            var universal = new NormDecision<>("a", "x", 1, NormSpecificity.UNIVERSAL, T1);
            var tenant = new NormDecision<>("b", "y", 1, NormSpecificity.TENANT, T1);
            var result = strategy.resolve(List.of(universal, tenant));
            assertThat(result.winner().specificity()).isEqualTo(NormSpecificity.TENANT);
        }
    }

    @Nested
    class Recency {
        private final ConflictResolutionStrategy<String> strategy = new RecencyResolution<>();

        @Test
        void mostRecentWins() {
            var old = new NormDecision<>("a", "approve", 1, NormSpecificity.UNIVERSAL, T1);
            var recent = new NormDecision<>("b", "deny", 1, NormSpecificity.UNIVERSAL, T2);
            var result = strategy.resolve(List.of(old, recent));
            assertThat(result.winner()).isEqualTo(recent);
            assertThat(result.method()).isEqualTo(ResolutionMethod.RECENCY);
        }
    }

    @Nested
    class MostRestrictive {
        private final ConflictResolutionStrategy<RiskDecision> strategy = new MostRestrictiveResolution();

        @Test
        void gateRequiredBeatsAutonomous() {
            var auto = new NormDecision<RiskDecision>("a", new RiskDecision.Autonomous(), 1,
                    NormSpecificity.UNIVERSAL, T1);
            var gate = new NormDecision<RiskDecision>("b",
                    new RiskDecision.GateRequired("needs review", true, null, null, null, null, null),
                    1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(auto, gate));
            assertThat(result.winner().decision()).isInstanceOf(RiskDecision.GateRequired.class);
            assertThat(result.method()).isEqualTo(ResolutionMethod.MOST_RESTRICTIVE);
        }

        @Test
        void allAutonomousReturnsFirst() {
            var a = new NormDecision<RiskDecision>("a", new RiskDecision.Autonomous(), 1,
                    NormSpecificity.UNIVERSAL, T1);
            var b = new NormDecision<RiskDecision>("b", new RiskDecision.Autonomous(), 1,
                    NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(a, b));
            assertThat(result.winner().decision()).isInstanceOf(RiskDecision.Autonomous.class);
        }
    }

    @Nested
    class Escalation {
        @Test
        void alwaysEscalates() {
            var strategy = new EscalationResolution<>("ESCALATED");
            var a = new NormDecision<>("a", "approve", 1, NormSpecificity.UNIVERSAL, T1);
            var b = new NormDecision<>("b", "deny", 1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(a, b));
            assertThat(result.winner().decision()).isEqualTo("ESCALATED");
            assertThat(result.winner().source()).isEqualTo("escalation");
            assertThat(result.overridden()).hasSize(2);
            assertThat(result.method()).isEqualTo(ResolutionMethod.ESCALATION);
        }

        @Test
        void allConflictingAreOverridden() {
            var strategy = new EscalationResolution<>("HUMAN_REVIEW");
            var a = new NormDecision<>("a", "x", 1, NormSpecificity.UNIVERSAL, T1);
            var b = new NormDecision<>("b", "y", 1, NormSpecificity.UNIVERSAL, T1);
            var c = new NormDecision<>("c", "z", 1, NormSpecificity.UNIVERSAL, T1);
            var result = strategy.resolve(List.of(a, b, c));
            assertThat(result.overridden()).containsExactly(a, b, c);
        }
    }
}
