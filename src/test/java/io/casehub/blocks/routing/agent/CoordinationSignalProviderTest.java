package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.eidos.api.MatchDegree;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CoordinationSignalProviderTest {
    private static double scoreOf(RoutingSignal.CandidateSignal signal) {
        return ((RoutingSignal.CandidateSignal.Score) signal).value();
    }


    private final CoordinationSignalProvider provider =
      new CoordinationSignalProvider(new DefaultCoordinationOutcomeWeights());

  private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
    return new AgentRoutingContext(
        UUID.randomUUID(), capability, NullNode.instance, "test-tenant", experiences, null, null);
  }

  private AgentCandidate candidate(String id) {
    return new AgentCandidate(
        id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
  }

  private RetrievedExperience teamExperience(
      String outcome, double similarity, List<ExperiencePlanStep> planTrace) {
    return new RetrievedExperience(
        "problem", "solution", outcome, 0.9, similarity,
        Map.of(), planTrace, Map.of());
  }

  @Test
  void idIsCoordination() {
    assertThat(provider.id()).isEqualTo("coordination");
  }

  @Nested
  class ReturnsNull {

    @Test
    void whenNoExperiences() {
      var result = provider.evaluate(
          context("analysis", List.of()),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenAllExperiencesSingleAgent() {
      var exp = teamExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNoEligibleCandidatesInTeams() {
      var exp = teamExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "not-eligible", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "also-not-eligible", "SUCCESS", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNegativeSimilarityOnly() {
      var exp = teamExperience("COMPLETED", -0.5, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenCancelledCaseOutcome() {
      var exp = teamExperience("CANCELLED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }
  }

  @Nested
  class TeamAffinityScoring {

    @Test
    void candidateInCompletedTeamScoresHigh() {
      var exp = teamExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a"), candidate("agent-b")));

      assertThat(result).isNotNull();
      assertThat(scoreOf(result.candidates().get("agent-a"))).isCloseTo(1.0, within(0.01));
      assertThat(scoreOf(result.candidates().get("agent-b"))).isCloseTo(1.0, within(0.01));
    }

    @Test
    void candidateInFaultedTeamScoresLow() {
      var exp = teamExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "FAILURE", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(scoreOf(result.candidates().get("agent-a"))).isCloseTo(0.2, within(0.01));
    }

    @Test
    void multipleExperiencesWeightedBySimilarity() {
      var exp1 = teamExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var exp2 = teamExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp1, exp2)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // agent-a in exp1 (COMPLETED, w=1.0) and exp2 (FAULTED, w=0.2), equal similarity
      // score = (1.0*0.9 + 0.2*0.9) / (0.9+0.9) = 1.08/1.8 = 0.6
      assertThat(scoreOf(result.candidates().get("agent-a"))).isCloseTo(0.6, within(0.01));
    }

    @Test
    void higherSimilarityExperienceWeighsMore() {
      var expHigh = teamExperience("COMPLETED", 0.95, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var expLow = teamExperience("FAULTED", 0.1, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(expHigh, expLow)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // (1.0*0.95 + 0.2*0.1) / (0.95+0.1) = 0.97/1.05 ≈ 0.924
      assertThat(scoreOf(result.candidates().get("agent-a"))).isGreaterThan(0.9);
    }

    @Test
    void scoresAllTeamMembersNotJustTargetCapability() {
      var exp = teamExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of()),
          new ExperiencePlanStep("b3", "approval", "agent-c", "SUCCESS", 2, Map.of())));

      var result = provider.evaluate(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a"), candidate("agent-b"), candidate("agent-c")));

      assertThat(result).isNotNull();
      assertThat(result.candidates()).hasSize(3);
      assertThat(result.candidates()).containsKeys("agent-a", "agent-b", "agent-c");
    }

    @Test
    void duplicateWorkerInTraceCountedOnce() {
      var exp = teamExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-a", "SUCCESS", 1, Map.of()),
          new ExperiencePlanStep("b3", "approval", "agent-b", "SUCCESS", 2, Map.of())));

      Set<String> team = CoordinationSignalProvider.extractTeam(exp.planTrace());
      assertThat(team).containsExactlyInAnyOrder("agent-a", "agent-b");
    }
  }

    @Nested
    class AdaptationGuidedRetrieval {

        @Test
        void higherTeamOverlapScoresHigher() {
            // exp1: team = {agent-a, agent-b} — both eligible → overlap = 1.0
            var exp1 = teamExperience("COMPLETED", 0.8, List.of(
                    new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                    new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
            // exp2: team = {agent-a, agent-x} — only agent-a eligible → overlap = 0.5
            var exp2 = teamExperience("COMPLETED", 0.8, List.of(
                    new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                    new ExperiencePlanStep("b2", "review", "agent-x", "SUCCESS", 1, Map.of())));

            var result = provider.evaluate(
                    context("analysis", List.of(exp1, exp2)),
                    List.of(candidate("agent-a"), candidate("agent-b")));

            assertThat(result).isNotNull();
            double agentAScore = scoreOf(result.candidates().get("agent-a"));
            double agentBScore = scoreOf(result.candidates().get("agent-b"));
            assertThat(agentAScore).isGreaterThan(0.5);
            assertThat(agentBScore).isCloseTo(1.0, within(0.01));
        }

        @Test
        void zeroOverlapExperienceIsSkipped() {
            var exp = teamExperience("COMPLETED", 0.9, List.of(
                    new ExperiencePlanStep("b1", "analysis", "agent-x", "SUCCESS", 0, Map.of()),
                    new ExperiencePlanStep("b2", "review", "agent-y", "SUCCESS", 1, Map.of())));

            var result = provider.evaluate(
                    context("analysis", List.of(exp)),
                    List.of(candidate("agent-a")));

            assertThat(result).isNull();
        }

        @Test
        void agrWeightsCompetingExperiencesByOverlap() {
            // COMPLETED team with full overlap (agent-a + agent-b both eligible)
            var goodExp = teamExperience("COMPLETED", 0.8, List.of(
                    new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                    new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

            // FAULTED team with partial overlap (agent-a eligible, agent-x not)
            var badExp = teamExperience("FAULTED", 0.8, List.of(
                    new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                    new ExperiencePlanStep("b2", "review", "agent-x", "FAILURE", 1, Map.of())));

            // With AGR: goodExp overlap=1.0 (adj=0.8), badExp overlap=0.5 (adj=0.4)
            // score = (1.0*0.8 + 0.2*0.4) / (0.8+0.4) = 0.88/1.2 ≈ 0.733
            var agrResult = provider.evaluate(
                    context("analysis", List.of(goodExp, badExp)),
                    List.of(candidate("agent-a"), candidate("agent-b")));

            assertThat(agrResult).isNotNull();
            // Without AGR both experiences would have equal weight → score = 0.6
            // With AGR the full-overlap COMPLETED experience dominates → score > 0.6
            assertThat(scoreOf(agrResult.candidates().get("agent-a"))).isGreaterThan(0.7);
        }
    }
}
