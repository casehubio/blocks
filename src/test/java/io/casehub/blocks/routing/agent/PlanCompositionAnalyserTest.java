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

class PlanCompositionAnalyserTest {

  private final PlanCompositionAnalyser analyser =
      new PlanCompositionAnalyser(new DefaultCbrCaseOutcomeWeights());

  private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
    return new AgentRoutingContext(
        UUID.randomUUID(), capability, NullNode.instance, "test-tenant", experiences);
  }

  private AgentCandidate candidate(String id) {
    return new AgentCandidate(
        id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
  }

  private RetrievedExperience multiStepExperience(
      String outcome, double similarity, List<ExperiencePlanStep> planTrace) {
    return new RetrievedExperience(
        "problem", "solution", outcome, 0.9, similarity,
        Map.of(), planTrace, Map.of());
  }

  @Test
  void idIsPlanComposition() {
    assertThat(analyser.id()).isEqualTo("plan-composition");
  }

  @Nested
  class ReturnsNull {

    @Test
    void whenNoExperiences() {
      var result = analyser.signal(
          context("analysis", List.of()),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenAllExperiencesSingleStep() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNoEligibleCandidatesInTraces() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "not-eligible", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "other", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNegativeSimilarityOnly() {
      var exp = multiStepExperience("COMPLETED", -0.5, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenCancelledCaseOutcome() {
      var exp = multiStepExperience("CANCELLED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }
  }

  @Nested
  class Scoring {

    @Test
    void workerInCompletedMultiStepPlanScoresHigh() {
      var exp = multiStepExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(result.candidates()).containsKey("agent-a");
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void workerInFaultedMultiStepPlanScoresLow() {
      var exp = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.2, within(0.01));
    }

    @Test
    void multipleExperiencesAveraged() {
      var exp1 = multiStepExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var exp2 = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp1, exp2)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // agent-a: (1.0*0.9 + 0.2*0.9) / (0.9+0.9) = 1.08/1.8 = 0.6
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.6, within(0.01));
    }

    @Test
    void higherSimilarityExperienceWeighsMore() {
      var expHigh = multiStepExperience("COMPLETED", 0.95, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var expLow = multiStepExperience("FAULTED", 0.1, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(expHigh, expLow)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // agent-a: (1.0*0.95 + 0.2*0.1) / (0.95+0.1) = 0.97/1.05 ≈ 0.924
      assertThat(result.candidates().get("agent-a").score()).isGreaterThan(0.9);
    }

    @Test
    void onlyTargetCapabilityStepsAreScored() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "other-cap", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "analysis", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a"), candidate("agent-b")));

      assertThat(result).isNotNull();
      assertThat(result.candidates()).containsKey("agent-b");
      assertThat(result.candidates()).doesNotContainKey("agent-a");
    }

    @Test
    void unknownCaseOutcomeDefaultsToZero() {
      var exp = multiStepExperience("ESCALATED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNull();
    }
  }

  @Nested
  class CustomCaseOutcomeWeights {

    @Test
    void domainWeightsAreRespected() {
      var customWeights = new CbrCaseOutcomeWeights() {
        @Override
        public Map<String, Double> weights() {
          return Map.of("COMPLETED", 1.0, "FAULTED", 0.8, "CANCELLED", 0.5);
        }
      };
      var customAnalyser = new PlanCompositionAnalyser(customWeights);

      var exp = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = customAnalyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.8, within(0.01));
    }
  }
}
