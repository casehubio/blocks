package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.index.AffectSnapshot;
import io.casehub.neocortex.cognitive.index.AgentPair;
import io.casehub.neocortex.cognitive.index.PadDimension;
import io.casehub.neocortex.cognitive.index.PadDistanceMatrix;
import io.casehub.neocortex.cognitive.index.PairwiseDifferences;
import io.casehub.neocortex.cognitive.index.PerspectivalComparison;
import io.casehub.neocortex.cognitive.index.TrajectoryAlignment;
import io.casehub.neocortex.cognitive.index.TrendAgreement;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SocialComparisonPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent", "tenant", null);

    @Test
    void returnsNullWhenEmpty() {
        var section = new SocialComparisonPromptSection(Map.of());
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void rendersDivergentPair() {
        var agentA = PrincipalId.agent("hooded-claw");
        var agentB = PrincipalId.agent("muttley");
        var pair = AgentPair.of(agentA, agentB);

        var snapshots = Map.of(
                agentA, new AffectSnapshot(agentA, 0.8, 0.3, 0.5, null),
                agentB, new AffectSnapshot(agentB, 0.2, 0.7, 0.3, null));

        var distances = new PadDistanceMatrix(Map.of(pair, 0.73));
        var diffs = Map.of(
                PadDimension.PLEASURE, new PairwiseDifferences(Map.of(pair, 0.6)),
                PadDimension.AROUSAL, new PairwiseDifferences(Map.of(pair, -0.4)),
                PadDimension.DOMINANCE, new PairwiseDifferences(Map.of(pair, 0.2)));
        var alignment = new TrajectoryAlignment(Map.of(pair, -0.5),
                Map.of(pair, TrendAgreement.DIVERGENT));

        var comparison = new PerspectivalComparison("node-1", "Penelope",
                snapshots, Set.of(), distances, diffs, alignment, 2);

        var section = new SocialComparisonPromptSection(
                Map.of("node-1", comparison), 0.4);
        var text = section.contribute(CTX);

        assertThat(text).contains("Penelope");
        assertThat(text).contains("0.73");
        assertThat(text).contains("pleasure");
        assertThat(text).contains("DIVERGENT");
    }

    @Test
    void filtersAlignedPairsBelowThreshold() {
        var agentA = PrincipalId.agent("a");
        var agentB = PrincipalId.agent("b");
        var pair = AgentPair.of(agentA, agentB);

        var snapshots = Map.of(
                agentA, new AffectSnapshot(agentA, 0.5, 0.5, 0.5, null),
                agentB, new AffectSnapshot(agentB, 0.5, 0.5, 0.5, null));

        var distances = new PadDistanceMatrix(Map.of(pair, 0.1));
        var diffs = Map.of(
                PadDimension.PLEASURE, new PairwiseDifferences(Map.of(pair, 0.0)),
                PadDimension.AROUSAL, new PairwiseDifferences(Map.of(pair, 0.0)),
                PadDimension.DOMINANCE, new PairwiseDifferences(Map.of(pair, 0.0)));
        var alignment = new TrajectoryAlignment(Map.of(pair, 1.0),
                Map.of(pair, TrendAgreement.ALIGNED));

        var comparison = new PerspectivalComparison("node-1", "Penelope",
                snapshots, Set.of(), distances, diffs, alignment, 2);

        var section = new SocialComparisonPromptSection(
                Map.of("node-1", comparison), 0.4);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void filtersAlignedTrajectoryAboveDistanceThreshold() {
        var agentA = PrincipalId.agent("a");
        var agentB = PrincipalId.agent("b");
        var pair = AgentPair.of(agentA, agentB);

        var snapshots = Map.of(
                agentA, new AffectSnapshot(agentA, 0.8, 0.3, 0.5, null),
                agentB, new AffectSnapshot(agentB, 0.2, 0.7, 0.3, null));

        var distances = new PadDistanceMatrix(Map.of(pair, 0.73));
        var diffs = Map.of(
                PadDimension.PLEASURE, new PairwiseDifferences(Map.of(pair, 0.6)),
                PadDimension.AROUSAL, new PairwiseDifferences(Map.of(pair, -0.4)),
                PadDimension.DOMINANCE, new PairwiseDifferences(Map.of(pair, 0.2)));
        var alignment = new TrajectoryAlignment(Map.of(pair, 0.95),
                Map.of(pair, TrendAgreement.ALIGNED));

        var comparison = new PerspectivalComparison("node-1", "Penelope",
                snapshots, Set.of(), distances, diffs, alignment, 2);

        var section = new SocialComparisonPromptSection(
                Map.of("node-1", comparison), 0.4);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void rendersMixedTrajectory() {
        var agentA = PrincipalId.agent("a");
        var agentB = PrincipalId.agent("b");
        var pair = AgentPair.of(agentA, agentB);

        var snapshots = Map.of(
                agentA, new AffectSnapshot(agentA, 0.8, 0.3, 0.5, null),
                agentB, new AffectSnapshot(agentB, 0.2, 0.7, 0.3, null));

        var distances = new PadDistanceMatrix(Map.of(pair, 0.73));
        var diffs = Map.of(
                PadDimension.PLEASURE, new PairwiseDifferences(Map.of(pair, 0.6)),
                PadDimension.AROUSAL, new PairwiseDifferences(Map.of(pair, -0.4)),
                PadDimension.DOMINANCE, new PairwiseDifferences(Map.of(pair, 0.2)));
        var alignment = new TrajectoryAlignment(Map.of(pair, 0.3),
                Map.of(pair, TrendAgreement.MIXED));

        var comparison = new PerspectivalComparison("node-1", "Penelope",
                snapshots, Set.of(), distances, diffs, alignment, 2);

        var section = new SocialComparisonPromptSection(
                Map.of("node-1", comparison), 0.4);
        var text = section.contribute(CTX);

        assertThat(text).contains("MIXED");
    }
}
