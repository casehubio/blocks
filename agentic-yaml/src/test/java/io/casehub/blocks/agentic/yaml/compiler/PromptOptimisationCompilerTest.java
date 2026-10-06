package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.ConfidenceScorerRegistry;
import io.casehub.blocks.agentic.yaml.registry.DiversityStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.PromptOptimiserRegistry;
import io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition;
import io.casehub.blocks.memory.CompositeConfidenceScorer;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.SafetyConfig;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.blocks.prompt.optimiser.InstructionOptimiser;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PromptOptimisationCompilerTest {

    private ObjectMapper mapper;
    private PromptOptimisationCompiler compiler;
    private PromptOptimisationCompiler compilerWithAgent;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
        var diversityRegistry = new DiversityStrategyRegistry();
        var optimiserRegistry = new PromptOptimiserRegistry(diversityRegistry);
        var scorerRegistry = new ConfidenceScorerRegistry();
        compiler = new PromptOptimisationCompiler(optimiserRegistry, scorerRegistry, null);
        compilerWithAgent = new PromptOptimisationCompiler(
                optimiserRegistry, scorerRegistry, mock(AgentProvider.class));
    }

    @Test
    void compilesMinimalPipeline() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines()).hasSize(1);
        var pipeline = compiled.pipelines().get("test");
        assertThat(pipeline.signature().id()).isEqualTo("test");
        assertThat(pipeline.optimiser()).isInstanceOf(FewShotOptimiser.class);
        assertThat(pipeline.config()).isEqualTo(OptimiserConfig.defaults());
        assertThat(pipeline.safety()).isEqualTo(SafetyConfig.defaults());
        assertThat(pipeline.confidenceScorer()).isNull();
        assertThat(pipeline.examples()).isEmpty();
    }

    @Test
    void compilesWithInstructionOptimiser() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    optimiser:
                      type: instruction
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compilerWithAgent.compile(def);
        assertThat(compiled.pipelines().get("test").optimiser())
                .isInstanceOf(InstructionOptimiser.class);
    }

    @Test
    void compilesWithConfidenceScorer() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    confidenceScorer:
                      type: composite
                      scorers:
                        - scorer:
                            type: arousal
                          weight: 0.7
                        - scorer:
                            type: surprise
                          weight: 0.3
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").confidenceScorer())
                .isInstanceOf(CompositeConfidenceScorer.class);
    }

    @Test
    void compilesWithExamples() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    examples:
                      - input: in
                        output: out
                        outcome: SUCCESS
                        qualityScore: 0.9
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").examples()).hasSize(1);
        assertThat(compiled.pipelines().get("test").examples().get(0).input()).isEqualTo("in");
    }

    @Test
    void compilesSignatureWithTypeRefs() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                      inputType: java.lang.String
                      outputType: java.lang.Integer
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").signature().inputType())
                .isEqualTo(String.class);
        assertThat(compiled.pipelines().get("test").signature().outputType())
                .isEqualTo(Integer.class);
    }

    @Test
    void compilesMultiplePipelines() throws Exception {
        var yaml = """
                pipelines:
                  routing:
                    signature:
                      id: routing
                      baseSystemPrompt: "Route"
                  decomp:
                    signature:
                      id: decomp
                      baseSystemPrompt: "Decompose"
                    optimiser:
                      type: instruction
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compilerWithAgent.compile(def);
        assertThat(compiled.pipelines()).hasSize(2);
        assertThat(compiled.pipelines().get("routing").optimiser())
                .isInstanceOf(FewShotOptimiser.class);
        assertThat(compiled.pipelines().get("decomp").optimiser())
                .isInstanceOf(InstructionOptimiser.class);
    }

    @Test
    void invalidClassNameThrows() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                      inputType: com.nonexistent.Fake
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        assertThatThrownBy(() -> compiler.compile(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.nonexistent.Fake");
    }
}
