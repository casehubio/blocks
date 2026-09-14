package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalEncounterLlmTest {

    private static final String SCENARIO = "historical-encounter";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());
    private static final Path RESULTS_DIR = Path.of("agentic-yaml/src/test/resources/results");

    private static TestAgentProvider agentProvider;

    @BeforeAll
    static void setUpLlm() {
        agentProvider = TestAgentProvider.claude();
    }

    @Test
    void descriptorsLoadWithBriefings() {
        var descriptors = DescriptorLoader.load(SCENARIO);

        assertThat(descriptors).hasSize(3);
        for (var d : descriptors) {
            System.out.printf("=== %s ===%n", d.name());
            System.out.printf("  briefing: %s...%n",
                    d.briefing().substring(0, Math.min(100, d.briefing().length())));
            System.out.printf("  capabilities: %s%n",
                    d.capabilities().stream().map(c -> c.name()).toList());
            assertThat(d.briefing()).isNotBlank();
        }
    }

    @Test
    void cognitionTicksProduceState() throws IOException {
        var compiled = loadCognition();
        var stack = CognitionStack.from(compiled, agentProvider);
        var descriptors = DescriptorLoader.load(SCENARIO);

        stack.tick("leonardo", "test", descriptors.get(0));
        stack.tick("nikola", "test", descriptors.get(1));

        assertThat(stack.mood().currentMood("leonardo", "test")).isPresent();
        assertThat(stack.drives().currentDrives("leonardo", "test")).isPresent();
        assertThat(stack.mood().currentMood("nikola", "test")).isPresent();
        assertThat(stack.drives().currentDrives("nikola", "test")).isPresent();

        stack.mood().currentMood("leonardo", "test").ifPresent(m ->
                System.out.printf("[leonardo mood] pleasure=%.2f arousal=%.2f dominance=%.2f%n",
                        m.pleasure(), m.arousal(), m.dominance()));
        stack.drives().currentDrives("leonardo", "test").ifPresent(d ->
                System.out.printf("[leonardo drives] dominant=%s composite=%.2f%n",
                        d.dominantDrive(), d.compositeMotivation()));
    }

    @Test
    void fullConversationRuns() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);
        assertThat(result.elapsed()).isNotNull();

        System.out.printf("%n=== Conversation complete ===%n");
        System.out.printf("Turns: %d  Elapsed: %s%n", result.turnCount(), result.elapsed());
    }

    @Test
    void stage0_baselineCapture() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.BASELINE);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .includeCognition(false)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);

        for (var m : result.metrics()) {
            assertThat(m.promptSectionsContributed()).isZero();
        }

        ResultsWriter.writeConversation(result, "stage-0", RESULTS_DIR);
        if (result.finalSnapshot() != null) {
            ResultsWriter.writeDump(result.finalSnapshot(), "stage-0",
                    RESULTS_DIR);
        }
        ResultsWriter.writeMetrics(result.metrics(), "stage-0", RESULTS_DIR);

        System.out.println("\n=== Stage 0 baseline complete ===");
        System.out.printf("Turns: %d  Elapsed: %s%n",
                result.turnCount(), result.elapsed());
    }

    @Test
    void stage1_signalExtraction() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.SIGNALS);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);

        var totalSections = result.metrics().stream()
                .mapToInt(CognitionMetrics::promptSectionsContributed)
                .sum();
        assertThat(totalSections).isGreaterThan(0);

        ResultsWriter.writeConversation(result, "stage-1", RESULTS_DIR);
        if (result.finalSnapshot() != null) {
            ResultsWriter.writeDump(result.finalSnapshot(), "stage-1",
                    RESULTS_DIR);
        }
        ResultsWriter.writeMetrics(result.metrics(), "stage-1", RESULTS_DIR);

        System.out.println("\n=== Stage 1 complete ===");
        System.out.printf("Total sections contributed: %d%n", totalSections);
    }

    @Test
    void stage2_realDrives() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.REAL_DRIVES);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);

        ResultsWriter.writeConversation(result, "stage-2", RESULTS_DIR);
        if (result.finalSnapshot() != null) {
            ResultsWriter.writeDump(result.finalSnapshot(), "stage-2",
                    RESULTS_DIR);
        }
        ResultsWriter.writeMetrics(result.metrics(), "stage-2", RESULTS_DIR);

        System.out.println("\n=== Stage 2 complete ===");
        if (result.finalSnapshot() != null && result.finalSnapshot().drives() != null) {
            System.out.printf("Final drives — dominant: %s  composite: %.2f%n",
                    result.finalSnapshot().drives().dominantDrive(),
                    result.finalSnapshot().drives().compositeMotivation());
        }
    }

    @Test
    void stage3_narrativeSynthesis() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.NARRATIVE);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);

        ResultsWriter.writeConversation(result, "stage-3", RESULTS_DIR);
        if (result.finalSnapshot() != null) {
            ResultsWriter.writeDump(result.finalSnapshot(), "stage-3",
                    RESULTS_DIR);
            var narrative = result.finalSnapshot().narrative();
            System.out.printf("\n=== Stage 3 complete ===%n");
            System.out.printf("Narrative episodes: %d, themes: %d%n",
                    narrative != null ? narrative.episodes().size() : 0,
                    narrative != null ? narrative.themes().size() : 0);
        }
        ResultsWriter.writeMetrics(result.metrics(), "stage-3", RESULTS_DIR);
    }

    @Test
    void stage4_fullOrchestration() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.FULL);

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(4)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(4);

        ResultsWriter.writeConversation(result, "stage-4", RESULTS_DIR);
        if (result.finalSnapshot() != null) {
            ResultsWriter.writeDump(result.finalSnapshot(), "stage-4",
                    RESULTS_DIR);
            System.out.printf("\n=== Stage 4 complete ===%n");
            System.out.printf("Goal proposals: %d%n",
                    result.finalSnapshot().goalProposals().size());
        }
        ResultsWriter.writeMetrics(result.metrics(), "stage-4", RESULTS_DIR);
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledCognition loadCognition() throws IOException {
        try (var is = HistoricalEncounterLlmTest.class.getResourceAsStream(
                "/examples/" + SCENARIO + "/cognition.yaml")) {
            return new CognitionCompiler().compile(YAML.readValue(is, CognitionDefinition.class));
        }
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledWorld loadWorld() throws IOException {
        try (var is = HistoricalEncounterLlmTest.class.getResourceAsStream(
                "/examples/" + SCENARIO + "/world.yaml")) {
            return new WorldCompiler(new ObservationFilterRegistry())
                    .compile(YAML.readValue(is, WorldDefinition.class));
        }
    }
}
