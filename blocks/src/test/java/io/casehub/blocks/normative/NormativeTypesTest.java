package io.casehub.blocks.normative;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormativeTypesTest {

    @Nested
    class SpecificityOrdering {
        @Test
        void instanceIsMoreSpecificThanUniversal() {
            assertThat(NormSpecificity.INSTANCE.isMoreSpecificThan(NormSpecificity.UNIVERSAL)).isTrue();
        }

        @Test
        void universalIsNotMoreSpecificThanInstance() {
            assertThat(NormSpecificity.UNIVERSAL.isMoreSpecificThan(NormSpecificity.INSTANCE)).isFalse();
        }

        @Test
        void sameSpecificityIsNotMoreSpecific() {
            assertThat(NormSpecificity.TENANT.isMoreSpecificThan(NormSpecificity.TENANT)).isFalse();
        }

        @Test
        void orderingIsTransitive() {
            assertThat(NormSpecificity.CASE_TYPE.isMoreSpecificThan(NormSpecificity.DOMAIN)).isTrue();
            assertThat(NormSpecificity.DOMAIN.isMoreSpecificThan(NormSpecificity.UNIVERSAL)).isTrue();
            assertThat(NormSpecificity.CASE_TYPE.isMoreSpecificThan(NormSpecificity.UNIVERSAL)).isTrue();
        }
    }

    @Nested
    class NormDecisionRecord {
        @Test
        void validDecision() {
            var d = new NormDecision<>("classifier-a", "approve", 1,
                    NormSpecificity.TENANT, Instant.now());
            assertThat(d.source()).isEqualTo("classifier-a");
            assertThat(d.priority()).isEqualTo(1);
        }

        @Test
        void rejectsNullSource() {
            assertThatThrownBy(() -> new NormDecision<>(null, "x", 1,
                    NormSpecificity.UNIVERSAL, Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class NormResolutionRecord {
        @Test
        void capturesWinnerAndOverridden() {
            var winner = new NormDecision<>("a", "approve", 1,
                    NormSpecificity.INSTANCE, Instant.now());
            var loser = new NormDecision<>("b", "deny", 5,
                    NormSpecificity.UNIVERSAL, Instant.now());
            var resolution = new NormResolution<>(winner, List.of(loser),
                    "Higher priority wins", ResolutionMethod.PRIORITY);
            assertThat(resolution.winner()).isEqualTo(winner);
            assertThat(resolution.overridden()).containsExactly(loser);
            assertThat(resolution.method()).isEqualTo(ResolutionMethod.PRIORITY);
        }

        @Test
        void defensiveCopyOfOverridden() {
            var winner = new NormDecision<>("a", "x", 1,
                    NormSpecificity.UNIVERSAL, Instant.now());
            var list = new java.util.ArrayList<>(List.of(winner));
            var resolution = new NormResolution<>(winner, list, "test", ResolutionMethod.PRIORITY);
            list.clear();
            assertThat(resolution.overridden()).hasSize(1);
        }
    }
}
