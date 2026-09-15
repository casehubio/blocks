package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.eidos.api.MatchDegree;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPreferenceSignalProviderTest {

    private final ModelPreferenceSignalProvider provider =
            new ModelPreferenceSignalProvider(new DefaultCbrOutcomeWeights());

    @Test
    void id() {
        assertThat(provider.id()).isEqualTo("model-preference");
    }

    @Test
    void noExperiences_returnsNull() {
        var ctx = context("cap1", List.of());
        var result = provider.evaluate(ctx, List.of(candidate("w1")));
        assertThat(result).isNull();
    }

    @Test
    void noModelIdInParameters_returnsNull() {
        var exp = experience("cap1", "w1", RoutingOutcome.SUCCESS, 0.9, Map.of());
        var ctx = context("cap1", List.of(exp));
        var result = provider.evaluate(ctx, List.of(candidate("w1")));
        assertThat(result).isNull();
    }

    @Test
    void singleModelPerWorker_scoresCandidate() {
        var exp = experience("cap1", "w1", RoutingOutcome.SUCCESS, 0.9,
                Map.of("modelId", "claude-sonnet-4"));
        var ctx = context("cap1", List.of(exp));
        var result = provider.evaluate(ctx, List.of(candidate("w1")));

        assertThat(result).isNotNull();
        assertThat(result.candidates()).containsKey("w1");
        var signal = result.candidates().get("w1");
        assertThat(signal).isInstanceOf(RoutingSignal.CandidateSignal.Score.class);
        var score = (RoutingSignal.CandidateSignal.Score) signal;
        assertThat(score.value()).isEqualTo(1.0);
        assertThat(score.rationale()).contains("claude-sonnet-4");
    }

    @Test
    void multipleModelsPerWorker_recommendsHighestScoring() {
        var success = experience("cap1", "w1", RoutingOutcome.SUCCESS, 0.9,
                Map.of("modelId", "claude-sonnet-4"));
        var failure = experience("cap1", "w1", RoutingOutcome.FAILURE, 0.9,
                Map.of("modelId", "gpt-4o"));
        var ctx = context("cap1", List.of(success, failure));
        var result = provider.evaluate(ctx, List.of(candidate("w1")));

        assertThat(result).isNotNull();
        var score = (RoutingSignal.CandidateSignal.Score) result.candidates().get("w1");
        assertThat(score.rationale()).contains("claude-sonnet-4");
    }

    @Test
    void candidateNotInExperiences_excluded() {
        var exp = experience("cap1", "w1", RoutingOutcome.SUCCESS, 0.9,
                Map.of("modelId", "claude-sonnet-4"));
        var ctx = context("cap1", List.of(exp));
        var result = provider.evaluate(ctx, List.of(candidate("w2")));
        assertThat(result).isNull();
    }

    @Test
    void similarityWeighting_closerExperiencesHaveMoreInfluence() {
        var closeSuccess = experience("cap1", "w1", RoutingOutcome.SUCCESS, 0.95,
                Map.of("modelId", "model-a"));
        var distantFailure = experience("cap1", "w1", RoutingOutcome.FAILURE, 0.1,
                Map.of("modelId", "model-a"));
        var ctx = context("cap1", List.of(closeSuccess, distantFailure));
        var result = provider.evaluate(ctx, List.of(candidate("w1")));

        assertThat(result).isNotNull();
        var score = (RoutingSignal.CandidateSignal.Score) result.candidates().get("w1");
        assertThat(score.value()).isGreaterThan(0.5);
    }

    private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
        return new AgentRoutingContext(
                UUID.randomUUID(), capability, NullNode.instance, "tenant", experiences, null, null);
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(
                id, Set.of("cap1"), 0, AgentHealth.READY, null, new MatchDegree.None(), Map.of());
    }

    private RetrievedExperience experience(String capName, String workerName,
                                            RoutingOutcome outcome, double similarity,
                                            Map<String, Object> stepParams) {
        return new RetrievedExperience(
                "problem", "solution", "COMPLETED", 0.9, similarity, Map.of(),
                List.of(new ExperiencePlanStep("binding", capName, workerName, outcome, 0, stepParams)),
                Map.of());
    }
}
