package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptOptimisationSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class PromptOptimiserSpecs {
        @Test
        void fewShotWithoutDiversity() throws Exception {
            var spec = mapper.readValue("type: few-shot", PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            assertThat(((PromptOptimiserSpec.FewShot) spec).diversity()).isNull();
        }

        @Test
        void fewShotWithDiversity() throws Exception {
            var yaml = """
                    type: few-shot
                    diversity:
                      type: outcome-aware
                      weight: 0.3
                    """;
            var spec = mapper.readValue(yaml, PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            var fs = (PromptOptimiserSpec.FewShot) spec;
            assertThat(fs.diversity()).isInstanceOf(DiversityStrategySpec.OutcomeAware.class);
            assertThat(((DiversityStrategySpec.OutcomeAware) fs.diversity()).weight()).isEqualTo(0.3);
        }

        @Test
        void instruction() throws Exception {
            var spec = mapper.readValue("type: instruction", PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.Instruction.class);
        }
    }

    @Nested
    class DiversityStrategySpecs {
        @Test
        void topN() throws Exception {
            var spec = mapper.readValue("type: top-n", DiversityStrategySpec.class);
            assertThat(spec).isInstanceOf(DiversityStrategySpec.TopN.class);
        }

        @Test
        void outcomeAware() throws Exception {
            var yaml = """
                    type: outcome-aware
                    weight: 0.5
                    """;
            var spec = mapper.readValue(yaml, DiversityStrategySpec.class);
            assertThat(spec).isInstanceOf(DiversityStrategySpec.OutcomeAware.class);
            assertThat(((DiversityStrategySpec.OutcomeAware) spec).weight()).isEqualTo(0.5);
        }

        @Test
        void outcomeAwareInvalidWeight() {
            assertThatThrownBy(() -> new DiversityStrategySpec.OutcomeAware(1.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ConfidenceScorerSpecs {
        @Test
        void arousal() throws Exception {
            var spec = mapper.readValue("type: arousal", ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Arousal.class);
        }

        @Test
        void surprise() throws Exception {
            var spec = mapper.readValue("type: surprise", ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Surprise.class);
        }

        @Test
        void composite() throws Exception {
            var yaml = """
                    type: composite
                    scorers:
                      - scorer:
                          type: arousal
                        weight: 0.6
                      - scorer:
                          type: surprise
                        weight: 0.4
                    """;
            var spec = mapper.readValue(yaml, ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Composite.class);
            var composite = (ConfidenceScorerSpec.Composite) spec;
            assertThat(composite.scorers()).hasSize(2);
            assertThat(composite.scorers().get(0).scorer()).isInstanceOf(ConfidenceScorerSpec.Arousal.class);
            assertThat(composite.scorers().get(0).weight()).isEqualTo(0.6);
        }

        @Test
        void compositeEmpty() {
            assertThatThrownBy(() -> new ConfidenceScorerSpec.Composite(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PipelineSpecs {
        @Test
        void fullPipeline() throws Exception {
            var yaml = """
                    signature:
                      id: test-prompt
                      baseSystemPrompt: "Test prompt"
                    optimiser:
                      type: few-shot
                    config:
                      maxExamples: 3
                      minQualityThreshold: 0.8
                      minOutcomeCount: 50
                      minVariantOutcomes: 20
                    safety:
                      qualityFloor: 0.3
                      maxExperimentCycles: 5
                      maxExperimentAge: P30D
                      circuitBreakerThreshold: 5
                      enabled: true
                    examples:
                      - input: test-input
                        output: test-output
                        outcome: SUCCESS
                        qualityScore: 0.9
                    """;
            var spec = mapper.readValue(yaml, PromptOptimisationPipelineSpec.class);
            assertThat(spec.signature().id()).isEqualTo("test-prompt");
            assertThat(spec.optimiser()).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            assertThat(spec.config().maxExamples()).isEqualTo(3);
            assertThat(spec.safety().qualityFloor()).isEqualTo(0.3);
            assertThat(spec.examples()).hasSize(1);
        }

        @Test
        void minimalPipeline() throws Exception {
            var yaml = """
                    signature:
                      id: minimal
                      baseSystemPrompt: "Minimal"
                    """;
            var spec = mapper.readValue(yaml, PromptOptimisationPipelineSpec.class);
            assertThat(spec.signature().id()).isEqualTo("minimal");
            assertThat(spec.optimiser()).isNull();
            assertThat(spec.config()).isNull();
            assertThat(spec.safety()).isNull();
            assertThat(spec.examples()).isNull();
        }
    }

    @Nested
    class DefinitionSpecs {
        @Test
        void fullDefinition() throws Exception {
            var yaml = """
                    pipelines:
                      routing:
                        signature:
                          id: routing
                          baseSystemPrompt: "Route it"
                      decomposition:
                        signature:
                          id: decomp
                          baseSystemPrompt: "Decompose it"
                        optimiser:
                          type: instruction
                    """;
            var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
            assertThat(def.pipelines()).hasSize(2);
            assertThat(def.pipelines()).containsKeys("routing", "decomposition");
        }
    }
}
