package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrAgentRoutingStrategyTest {

  @Mock AgentGraphQuery graphQuery;

  private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
    return new AgentRoutingContext(
        UUID.randomUUID(), capability, NullNode.instance, "test-tenant", experiences);
  }

  private AgentCandidate candidate(String id) {
    return new AgentCandidate(
        id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
  }

  private RetrievedExperience experience(String capName, String workerName, String outcome) {
    return new RetrievedExperience(
        "problem",
        "solution",
        "COMPLETED",
        0.9,
        0.85,
        Map.of(),
        List.of(
            new ExperiencePlanStep("binding-a", capName, workerName, outcome, 0, Map.of())),
        Map.of());
  }

  @Test
  void idIsCbr() {
    var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    assertThat(strategy.id()).isEqualTo("cbr");
  }

  @Test
  void emptyCandidatesReturnsUnresolvable() {
    var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result = strategy.select(context("cap", List.of()), List.of());
    assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
  }

  @Test
  void emptyExperiencesNoGraphReturnsUnresolvable() {
    var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result =
        strategy
            .select(context("analysis", List.of()), List.of(candidate("agent-a")))
            .await()
            .indefinitely();
    assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
  }

  @Nested
  class ExperienceBasedSelection {

    @Test
    void selectsWorkerWithHighestSuccessRate() {
      var experiences =
          List.of(
              experience("analysis", "agent-a", "SUCCESS"),
              experience("analysis", "agent-b", "FAILURE"),
              experience("analysis", "agent-a", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(
                  context("analysis", experiences),
                  List.of(candidate("agent-a"), candidate("agent-b")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void ignoresWorkersNotInCandidateList() {
      var experiences = List.of(experience("analysis", "not-a-candidate", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(context("analysis", experiences), List.of(candidate("agent-a")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }

    @Test
    void ignoresExperiencesForDifferentCapability() {
      var experiences = List.of(experience("other-cap", "agent-a", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(context("analysis", experiences), List.of(candidate("agent-a")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }

    @Test
    void tiesBrokenByTotalCount() {
      var exp =
          new RetrievedExperience(
              "p",
              "s",
              "COMPLETED",
              0.9,
              0.8,
              Map.of(),
              List.of(
                  new ExperiencePlanStep("b", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                  new ExperiencePlanStep("b", "analysis", "agent-b", "SUCCESS", 0, Map.of()),
                  new ExperiencePlanStep("b", "analysis", "agent-b", "SUCCESS", 0, Map.of())),
              Map.of());

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(
                  context("analysis", List.of(exp)),
                  List.of(candidate("agent-a"), candidate("agent-b")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }

      @Test
      void addedStepsExcludedFromAnalysis() {
          var exp =
                  new RetrievedExperience(
                          "problem",
                          "solution",
                          "COMPLETED",
                          0.9,
                          0.85,
                          Map.of(),
                          List.of(
                                  new ExperiencePlanStep(
                                          "b1", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                          "ADDED", "adapter recommendation"),
                                  new ExperiencePlanStep(
                                          "b2", "analysis", "agent-b", "SUCCESS", 0, Map.of(),
                                          "RETAINED", null)),
                          Map.of());

          var strategy = new CbrAgentRoutingStrategy(
                  (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
          var result =
                  strategy
                          .select(
                                  context("analysis", List.of(exp)),
                                  List.of(candidate("agent-a"), candidate("agent-b")))
                          .await()
                          .indefinitely();

          assertThat(result).isInstanceOf(RoutingResult.Selected.class);
          assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
      }

    @Test
    void substitutedStepsExcludedFromAnalysis() {
      var exp =
              new RetrievedExperience(
                      "problem",
                      "solution",
                      "COMPLETED",
                      0.9,
                      0.85,
                      Map.of(),
                      List.of(
                              new ExperiencePlanStep(
                                      "b1", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                      "SUBSTITUTED", "replaced original-worker"),
                              new ExperiencePlanStep(
                                      "b2", "analysis", "agent-b", "FAILURE", 0, Map.of(),
                                      "RETAINED", null)),
                      Map.of());

      var strategy = new CbrAgentRoutingStrategy(
              (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
              strategy
                      .select(
                              context("analysis", List.of(exp)),
                              List.of(candidate("agent-a"), candidate("agent-b")))
                      .await()
                      .indefinitely();

      // agent-a excluded (SUBSTITUTED), agent-b has FAILURE (0.0 score)
      // Both have no positive score — should be unresolvable
      assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }


  }

  @Nested
  class WeightedOutcomes {

    @Test
    void gateExpiredCountsAsHalfCredit() {
      var experiences =
          List.of(
              experience("analysis", "agent-a", "GATE_EXPIRED"),
              experience("analysis", "agent-b", "FAILURE"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(
                  context("analysis", experiences),
                  List.of(candidate("agent-a"), candidate("agent-b")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void unknownOutcomeTreatedAsFailure() {
      var experiences =
          List.of(
              experience("analysis", "agent-a", "UNKNOWN_OUTCOME"),
              experience("analysis", "agent-b", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(
                  context("analysis", experiences),
                  List.of(candidate("agent-a"), candidate("agent-b")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }
  }

  @Nested
  class Fallbacks {

    @Test
    void fallsBackToGraphQueryWhenNoExperiences() {
      when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
          .thenReturn(List.of("agent-b", "agent-a"));

      var strategy = new CbrAgentRoutingStrategy((AgentGraphQuery) graphQuery, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(
                  context("analysis", List.of()),
                  List.of(candidate("agent-a"), candidate("agent-b")))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }
  }

  @Nested
  class WithTrust {

    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    @Test
    void excludedCandidatesFilteredBeforeCbr() {
      var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null, Set.of(), 0.0);
      when(policyProvider.forCapability("analysis")).thenReturn(policy);
      var good = candidate("qualified");
      var bad = candidate("excluded");
      var classified =
          List.of(
              new TrustCandidateClassifier.ClassifiedCandidate(
                  good,
                  TrustCandidateClassifier.Phase.QUALIFIED,
                  OptionalDouble.of(0.9),
                  1.0),
              new TrustCandidateClassifier.ClassifiedCandidate(
                  bad,
                  TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                  OptionalDouble.of(0.3),
                  1.0));
      when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
          .thenReturn(classified);

      var experiences = List.of(experience("analysis", "qualified", "SUCCESS"));

      var strategy =
          new CbrAgentRoutingStrategy((AgentGraphQuery) graphQuery, classifier, scoreSource, policyProvider, new DefaultCbrOutcomeWeights(), null);
      var result =
          strategy
              .select(context("analysis", experiences), List.of(good, bad))
              .await()
              .indefinitely();

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("qualified");
    }
  }

  @Nested
  class ConfigurableWeights {

    @Test
    void customWeightsMakeGateExpiredBeatSuccess() {
      var customWeights = new CbrOutcomeWeights() {
        @Override
        public Map<RoutingOutcome, Double> weights() {
          return Map.of(
              RoutingOutcome.SUCCESS, 0.5,
              RoutingOutcome.GATE_EXPIRED, 1.0,
              RoutingOutcome.GATE_REJECTED, 0.0,
              RoutingOutcome.FAILURE, 0.0);
        }
      };

      var experiences = List.of(
          experience("analysis", "agent-a", "GATE_EXPIRED"),
          experience("analysis", "agent-b", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null, customWeights, null);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void unrecognisedOutcomeDefaultsToZero() {
      var experiences = List.of(
          experience("analysis", "agent-a", "DECLINED"),
          experience("analysis", "agent-b", "SUCCESS"));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }
  }

  @Nested
  class SimilarityWeighting {

    private RetrievedExperience experienceWithSimilarity(
        String capName, String workerName, String outcome, double similarity) {
      return new RetrievedExperience(
          "problem", "solution", "COMPLETED", 0.9, similarity,
          Map.of(),
          List.of(new ExperiencePlanStep("binding-a", capName, workerName, outcome, 0, Map.of())),
          Map.of());
    }

    @Test
    void highSimilaritySuccessOutweighsLowSimilaritySuccess() {
      // agent-a: SUCCESS at high similarity, FAILURE at low similarity
      // agent-b: SUCCESS at low similarity, FAILURE at high similarity
      // Similarity weighting makes agent-a's SUCCESS count more
      var experiences = List.of(
          experienceWithSimilarity("analysis", "agent-a", "SUCCESS", 0.95),
          experienceWithSimilarity("analysis", "agent-a", "FAILURE", 0.3),
          experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.3),
          experienceWithSimilarity("analysis", "agent-b", "FAILURE", 0.95));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      // agent-a: (1.0*0.95 + 0.0*0.3) / (0.95+0.3) = 0.76
      // agent-b: (1.0*0.3 + 0.0*0.95) / (0.3+0.95) = 0.24
      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void negativeSimilarityClampedToZero() {
      var experiences = List.of(
          experienceWithSimilarity("analysis", "agent-a", "SUCCESS", -0.5),
          experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.7));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }

    @Test
    void zeroSimilarityExperienceContributesNothing() {
      var experiences = List.of(
          experienceWithSimilarity("analysis", "agent-a", "SUCCESS", 0.0),
          experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.8));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }
  }

  @Nested
  class SignalIntegration {

    private RoutingSignalProvider signalProvider(String id, RoutingSignal signal) {
      return new RoutingSignalProvider() {
        @Override public String id() { return id; }
        @Override public RoutingSignal signal(AgentRoutingContext ctx, List<AgentCandidate> e) {
          return signal;
        }
      };
    }

    @Test
    void signalBoostsCandidateAboveExperienceOnlyWinner() {
      var experiences = List.of(
          experience("analysis", "agent-a", "GATE_EXPIRED"),
          experience("analysis", "agent-b", "FAILURE"));

      var signal = new RoutingSignal(Map.of(
          "agent-b", new RoutingSignal.CandidateSignal(0.9, "plan-fit")));
      var assembler = new RoutingSignalAssembler(
          List.of(signalProvider("test", signal)));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null,
          new DefaultCbrOutcomeWeights(), assembler);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
    }

    @Test
    void signalOnlyNoExperiences() {
      var signal = new RoutingSignal(Map.of(
          "agent-a", new RoutingSignal.CandidateSignal(0.7, "signal-only")));
      var assembler = new RoutingSignalAssembler(
          List.of(signalProvider("test", signal)));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null,
          new DefaultCbrOutcomeWeights(), assembler);
      var result = strategy.select(
          context("analysis", List.of()),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void noSignalsNoExperiencesFallsThrough() {
      var assembler = new RoutingSignalAssembler(List.of());

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null,
          new DefaultCbrOutcomeWeights(), assembler);
      var result = strategy.select(
          context("analysis", List.of()),
          List.of(candidate("agent-a")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }

    @Test
    void sparseCandidateMapHandledGracefully() {
      var experiences = List.of(
          experience("analysis", "agent-a", "SUCCESS"),
          experience("analysis", "agent-b", "SUCCESS"));

      var signal = new RoutingSignal(Map.of(
          "agent-a", new RoutingSignal.CandidateSignal(0.5, "boosted")));
      var assembler = new RoutingSignalAssembler(
          List.of(signalProvider("test", signal)));

      var strategy = new CbrAgentRoutingStrategy(
          (AgentGraphQuery) null, null, null, null,
          new DefaultCbrOutcomeWeights(), assembler);
      var result = strategy.select(
          context("analysis", experiences),
          List.of(candidate("agent-a"), candidate("agent-b")))
          ;

      assertThat(result).isInstanceOf(RoutingResult.Selected.class);
      assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }
  }
}
