package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchAnalysisExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "research-analysis";

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.routing()).isInstanceOf(RoundRobinRouting.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("methodology-critic");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("evidence-critic");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void cognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.CURIOSITY)).isEqualTo(1.6);
        assertThat(compiled.drive().axisWeights().get(DriveAxis.AUTONOMY)).isEqualTo(1.4);
        assertThat(compiled.drive().changeThreshold()).isEqualTo(0.10);

        assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.4);
        assertThat(compiled.mood().baseline().arousal()).isEqualTo(0.6);

        assertThat(compiled.strategyLearning()).isNotNull();
        assertThat(compiled.strategyLearning().maxGuidelines()).isEqualTo(15);

        assertThat(compiled.mentalModel()).isNotNull();
        assertThat(compiled.mentalModel().confidenceFloor()).isEqualTo(0.3);

        assertThat(compiled.userModel().minSignalsForSynthesis()).isEqualTo(8);
        assertThat(compiled.userModel().maxObservationsInPrompt()).isEqualTo(10);

        assertThat(compiled.goalProposal().proposalThreshold()).isEqualTo(0.55);
        assertThat(compiled.goalProposal().maxDriveGoals()).isEqualTo(4);

        assertThat(compiled.goalEscalation().escalationSalienceThreshold()).isEqualTo(0.6);
        assertThat(compiled.goalEscalation().maxPrimaryDriveGoals()).isEqualTo(2);

        assertThat(compiled.normDetection().minObservationsForNorm()).isEqualTo(15);
        assertThat(compiled.normDetection().establishedThreshold()).isEqualTo(0.75);

        assertThat(compiled.collectiveGoal().alignmentThreshold()).isEqualTo(0.65);
        assertThat(compiled.collectiveGoal().minAlignedAgents()).isEqualTo(2);

        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(60);
        assertThat(compiled.retention().recencyWeight()).isEqualTo(0.55);
    }

    @SuppressWarnings("unchecked")
    @Test
    void caseYamlParses() throws IOException {
        var caseMap = load(SCENARIO, "case.yaml", Map.class);

        assertThat(caseMap).containsKey("dsl");
        assertThat(caseMap.get("dsl")).isEqualTo("1.0.0");
        assertThat(caseMap).containsKey("name");
        assertThat(caseMap.get("name")).isEqualTo("research-analysis");
        assertThat(caseMap).containsKey("capabilities");
    }
}
