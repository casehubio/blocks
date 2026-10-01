package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeSynthesisGate;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.memory.RetentionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCompilerTest {

    private ObjectMapper mapper;
    private CognitionCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        compiler = new CognitionCompiler();
    }

    @Test
    void drivePartialOverrideMergesWithDefaults() throws IOException {
        var yaml = """
                drive:
                  changeThreshold: 0.1
                  axisWeights:
                    CURIOSITY: 1.5
                """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled = compiler.compile(definition);

        assertThat(compiled.drive().changeThreshold()).isEqualTo(0.1);
        assertThat(compiled.drive().axisWeights().get(DriveAxis.CURIOSITY)).isEqualTo(1.5);
        assertThat(compiled.drive().maxIntensity()).isEqualTo(DriveConfig.defaults().maxIntensity());
        assertThat(compiled.drive().moodPleasureModulation()).isEqualTo(DriveConfig.defaults().moodPleasureModulation());
    }

    @Test
    void nullSectionUsesDefaults() throws IOException {
        var yaml = "{}";
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled = compiler.compile(definition);

        assertThat(compiled.drive()).isEqualTo(DriveConfig.defaults());
    }

    @Test
    void moodPartialOverrideWithExternalNestedRecord() throws IOException {
        var yaml = """
                   mood:
                     baseline:
                       pleasure: 0.5
                       arousal: 0.3
                       dominance: 0.2
                     maxDisplacement: 0.8
                   """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.mood().baseline().pleasure()).isEqualTo(0.5);
        assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.8);
        assertThat(compiled.mood().decayTimeConstant()).isEqualTo(MoodConfig.defaults().decayTimeConstant());
    }

    @Test
    void personalitySimplestConfig() throws IOException {
        var yaml = """
                   personality:
                     decayFactor: 0.05
                   """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.personality().decayFactor()).isEqualTo(0.05);
        assertThat(compiled.personality().l2Ceiling()).isEqualTo(PersonalityEvolutionConfig.defaults().l2Ceiling());
    }

    @Test
    void narrativeWithNestedSynthesisGatePartialMerge() throws IOException {
        var yaml = """
                   narrative:
                     maxEpisodes: 100
                     synthesisGate:
                       minNewReflections: 3
                   """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(100);
        assertThat(compiled.narrative().synthesisGate().minNewReflections()).isEqualTo(3);
        assertThat(compiled.narrative().synthesisGate().noveltyThreshold())
                .isEqualTo(NarrativeSynthesisGate.defaults().noveltyThreshold());
    }

    @Test
    void retentionUsesDefaultStaticField() throws IOException {
        var yaml = """
                   retention:
                     recencyWeight: 0.5
                   """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.retention().recencyWeight()).isEqualTo(0.5);
        assertThat(compiled.retention().retentionThreshold()).isEqualTo(RetentionConfig.DEFAULT.retentionThreshold());
    }

    @Test
    void cognitiveGoalPartialOverrideMergesWithDefaults() throws IOException {
        var yaml = """
                   cognitiveGoal:
                     driveWeight: 0.3
                     surfacingCooldown: PT30M
                   """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.cognitiveGoal().driveWeight()).isEqualTo(0.3);
        assertThat(compiled.cognitiveGoal().surfacingCooldown()).isEqualTo(java.time.Duration.ofMinutes(30));
        assertThat(compiled.cognitiveGoal().caseCreationThreshold())
                .isEqualTo(io.casehub.neocortex.cognition.goal.CognitiveGoalConfig.defaults().caseCreationThreshold());
        assertThat(compiled.cognitiveGoal().minimumSurfacingPriority())
                .isEqualTo(io.casehub.neocortex.cognition.goal.CognitiveGoalConfig.defaults().minimumSurfacingPriority());
    }

    @Test
    void nullCognitiveGoalUsesDefaults() throws IOException {
        var yaml       = "{}";
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled   = compiler.compile(definition);

        assertThat(compiled.cognitiveGoal())
                .isEqualTo(io.casehub.neocortex.cognition.goal.CognitiveGoalConfig.defaults());
    }


    @Test
    void fullYamlAllSections() throws IOException {
        try (var is = getClass().getResourceAsStream("/cognition/full.yaml")) {
            var definition = mapper.readValue(is, CognitionDefinition.class);
            var compiled   = compiler.compile(definition);
            assertThat(compiled.drive()).isNotNull();
            assertThat(compiled.mood()).isNotNull();
            assertThat(compiled.personality()).isNotNull();
            assertThat(compiled.userModel()).isNotNull();
            assertThat(compiled.strategyLearning()).isNotNull();
            assertThat(compiled.mentalModel()).isNotNull();
            assertThat(compiled.narrative()).isNotNull();
            assertThat(compiled.goalProposal()).isNotNull();
            assertThat(compiled.goalEscalation()).isNotNull();
            assertThat(compiled.normDetection()).isNotNull();
            assertThat(compiled.collectiveGoal()).isNotNull();
            assertThat(compiled.retention()).isNotNull();
            assertThat(compiled.cognitiveGoal()).isNotNull();
            assertThat(compiled.drive().changeThreshold()).isEqualTo(0.1);
            assertThat(compiled.narrative().maxEpisodes()).isEqualTo(100);
            assertThat(compiled.retention().recencyWeight()).isEqualTo(0.5);
        }
    }


}
