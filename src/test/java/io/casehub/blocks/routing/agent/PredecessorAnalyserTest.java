package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.eidos.api.MatchDegree;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PredecessorAnalyserTest {

    private final PredecessorAnalyser analyser =
            new PredecessorAnalyser(new DefaultCbrCaseOutcomeWeights());

    private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
        return new AgentRoutingContext(
                UUID.randomUUID(), capability, NullNode.instance, "test-tenant", experiences);
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(
                id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
    }

    private ExperiencePlanStep step(String capability, String worker, int priority) {
        return new ExperiencePlanStep("b" + priority, capability, worker, "SUCCESS", priority, Map.of());
    }

    private RetrievedExperience experience(String outcome, double similarity, List<ExperiencePlanStep> trace) {
        return new RetrievedExperience("problem", "solution", outcome, 0.9, similarity, Map.of(), trace, Map.of());
    }

    @Test
    void idIsPredecessor() {
        assertThat(analyser.id()).isEqualTo("predecessor");
    }

    @Nested
    class ReturnsNull {
        @Test
        void whenNoExperiences() {
            var result = analyser.signal(context("review", List.of()), List.of(candidate("a")));
            assertThat(result).isNull();
        }

        @Test
        void whenAllSingleStep() {
            var exp = experience("COMPLETED", 0.8, List.of(step("review", "a", 0)));
            var result = analyser.signal(context("review", List.of(exp)), List.of(candidate("a")));
            assertThat(result).isNull();
        }

        @Test
        void whenTargetCapabilityIsFirstStep() {
            var exp = experience("COMPLETED", 0.8, List.of(
                    step("review", "a", 0),
                    step("analysis", "b", 1)));
            var result = analyser.signal(context("review", List.of(exp)), List.of(candidate("a")));
            assertThat(result).isNull();
        }

        @Test
        void whenCandidateNotEligible() {
            var exp = experience("COMPLETED", 0.8, List.of(
                    step("analysis", "x", 0),
                    step("review", "not-eligible", 1)));
            var result = analyser.signal(context("review", List.of(exp)), List.of(candidate("a")));
            assertThat(result).isNull();
        }
    }

    @Nested
    class Scoring {
        @Test
        void scoresBasedOnImmediatePredecessor() {
            var exp = experience("COMPLETED", 0.9, List.of(
                    step("analysis", "analyst-1", 0),
                    step("review", "reviewer-a", 1)));

            var result = analyser.signal(
                    context("review", List.of(exp)),
                    List.of(candidate("reviewer-a")));

            assertThat(result).isNotNull();
            assertThat(result.candidates()).containsKey("reviewer-a");
            assertThat(result.candidates().get("reviewer-a").score()).isCloseTo(1.0, within(0.01));
        }

        @Test
        void faultedCaseReducesScore() {
            var exp = experience("FAULTED", 0.9, List.of(
                    step("analysis", "analyst-1", 0),
                    step("review", "reviewer-a", 1)));

            var result = analyser.signal(
                    context("review", List.of(exp)),
                    List.of(candidate("reviewer-a")));

            assertThat(result).isNotNull();
            assertThat(result.candidates().get("reviewer-a").score()).isCloseTo(0.2, within(0.01));
        }

        @Test
        void predecessorContextInReason() {
            var exp = experience("COMPLETED", 0.9, List.of(
                    step("analysis", "analyst-1", 0),
                    step("review", "reviewer-a", 1)));

            var result = analyser.signal(
                    context("review", List.of(exp)),
                    List.of(candidate("reviewer-a")));

            assertThat(result.candidates().get("reviewer-a").reason()).contains("predecessor");
        }

        @Test
        void multipleExperiencesSamePredecessorAveraged() {
            var exp1 = experience("COMPLETED", 0.9, List.of(
                    step("analysis", "analyst-1", 0),
                    step("review", "reviewer-a", 1)));
            var exp2 = experience("FAULTED", 0.9, List.of(
                    step("analysis", "analyst-1", 0),
                    step("review", "reviewer-a", 1)));

            var result = analyser.signal(
                    context("review", List.of(exp1, exp2)),
                    List.of(candidate("reviewer-a")));

            assertThat(result).isNotNull();
            // (1.0*0.9 + 0.2*0.9) / (0.9+0.9) = 0.6
            assertThat(result.candidates().get("reviewer-a").score()).isCloseTo(0.6, within(0.01));
        }

        @Test
        void threeStepPlan_usesImmediatePredecessor() {
            var exp = experience("COMPLETED", 0.8, List.of(
                    step("triage", "triage-bot", 0),
                    step("analysis", "analyst-1", 1),
                    step("review", "reviewer-a", 2)));

            var result = analyser.signal(
                    context("review", List.of(exp)),
                    List.of(candidate("reviewer-a")));

            assertThat(result).isNotNull();
            assertThat(result.candidates().get("reviewer-a").score()).isCloseTo(1.0, within(0.01));
            assertThat(result.candidates().get("reviewer-a").reason()).contains("analysis:analyst-1");
        }

        @Test
        void stepsOutOfPriorityOrderAreSorted() {
            var exp = experience("COMPLETED", 0.8, List.of(
                    step("review", "reviewer-a", 2),
                    step("triage", "triage-bot", 0),
                    step("analysis", "analyst-1", 1)));

            var result = analyser.signal(
                    context("review", List.of(exp)),
                    List.of(candidate("reviewer-a")));

            assertThat(result).isNotNull();
            assertThat(result.candidates().get("reviewer-a").reason()).contains("analysis:analyst-1");
        }
    }
}
