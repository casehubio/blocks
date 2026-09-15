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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalEncounterLlmTest {

    private static final String SCENARIO = "historical-encounter";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());
    private static final Path RESULTS_DIR = Path.of("src/test/resources/results");

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
                .maxTurns(12)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);
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
                .maxTurns(12)
                .includeCognition(false)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);

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
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);

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
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);

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
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);

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
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        assertThat(result.turnCount()).isEqualTo(8);

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

    @Test
    void stage5_visualization() throws IOException {
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
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        Files.createDirectories(RESULTS_DIR);
        for (var m : result.metrics()) {
            var mermaid = MermaidGenerator.generate(m.snapshotAfter());
            Files.writeString(
                    RESULTS_DIR.resolve("stage-5-turn-" + m.turnNumber()
                            + "-" + m.agentId() + ".mmd"),
                    mermaid);
        }

        System.out.println("\n=== Stage 5 — per-turn Mermaid graphs written ===");
        ResultsWriter.writeConversation(result, "stage-5", RESULTS_DIR);
        ResultsWriter.writeMetrics(result.metrics(), "stage-5", RESULTS_DIR);
    }

    @Test
    void stage6_abComparison() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);

        var baselineStack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.BASELINE);
        var baseline = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(baselineStack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(12)
                .includeCognition(false)
                .build()
                .run();

        var fullStack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.FULL);
        var withCognition = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(fullStack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(12)
                .includeCognition(true)
                .build()
                .run();

        ResultsWriter.writeConversation(baseline, "stage-6-baseline", RESULTS_DIR);
        ResultsWriter.writeMetrics(baseline.metrics(), "stage-6-baseline", RESULTS_DIR);
        ResultsWriter.writeConversation(withCognition, "stage-6-cognition", RESULTS_DIR);
        ResultsWriter.writeMetrics(withCognition.metrics(), "stage-6-cognition", RESULTS_DIR);
        if (withCognition.finalSnapshot() != null) {
            ResultsWriter.writeDump(withCognition.finalSnapshot(),
                    "stage-6-cognition", RESULTS_DIR);
        }

        var evaluator = new ConversationEvaluator(agentProvider);
        var comparison = evaluator.compare(baseline, withCognition,
                withCognition.metrics());

        System.out.println("\n=== Stage 6 — A/B Comparison ===");
        System.out.printf("Winner: %s%n", comparison.winner());
        System.out.printf("Assessment: %s%n", comparison.assessment());
        for (var dim : comparison.baselineScores().keySet()) {
            System.out.printf("  %s: baseline=%d cognition=%d%n",
                    dim,
                    comparison.baselineScores().getOrDefault(dim, 0),
                    comparison.cognitionScores().getOrDefault(dim, 0));
        }

        var finalSnap = withCognition.finalSnapshot();
        if (finalSnap != null) {
            System.out.println("\n--- Cognitive Evidence ---");
            int totalBeliefs = finalSnap.mentalModels().values().stream()
                    .mapToInt(m -> m.beliefs().size()).sum();
            System.out.printf("Mental model beliefs: %d%n", totalBeliefs);
            if (finalSnap.narrative() != null) {
                System.out.printf("Narrative episodes: %d, themes: %d%n",
                        finalSnap.narrative().episodes().size(),
                        finalSnap.narrative().themes().size());
            }
            System.out.printf("Goal proposals: %d%n",
                    finalSnap.goalProposals().size());
        }

        Files.createDirectories(RESULTS_DIR);
        var sb = new StringBuilder();
        sb.append("# Stage 6 — A/B Comparison\n\n");
        sb.append("## Winner: ").append(comparison.winner()).append("\n\n");
        sb.append("## Assessment\n\n");
        sb.append(comparison.assessment()).append("\n\n");
        sb.append("## Scores\n\n");
        sb.append("| Dimension | Baseline | Cognition |\n");
        sb.append("|-----------|----------|----------|\n");
        for (var dim : comparison.baselineScores().keySet()) {
            sb.append(String.format("| %s | %d | %d |%n",
                    dim,
                    comparison.baselineScores().getOrDefault(dim, 0),
                    comparison.cognitionScores().getOrDefault(dim, 0)));
        }
        if (finalSnap != null) {
            sb.append("\n## Cognitive Evidence\n\n");
            int totalBeliefs = finalSnap.mentalModels().values().stream()
                    .mapToInt(m -> m.beliefs().size()).sum();
            sb.append(String.format("- Mental model beliefs: %d%n", totalBeliefs));
            if (finalSnap.narrative() != null) {
                sb.append(String.format("- Narrative episodes: %d, themes: %d%n",
                        finalSnap.narrative().episodes().size(),
                        finalSnap.narrative().themes().size()));
            }
            sb.append(String.format("- Goal proposals: %d%n",
                    finalSnap.goalProposals().size()));
            sb.append(String.format("- Sections contributed (final turn): %d%n",
                    withCognition.metrics().getLast().promptSectionsContributed()));
        }
        Files.writeString(RESULTS_DIR.resolve("stage-6-comparison.md"),
                sb.toString());
    }

    // --- per-orchestrator pipeline proof tests (#279) ---

    @Test
    void strategyContributesByTurn4() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.REAL_DRIVES, 4);
        var strategyContributed = result.metrics().stream()
                .anyMatch(m -> m.promptSectionContent().containsKey("StrategyPromptSection"));
        System.out.printf("[#279 proof] StrategyPromptSection contributed: %s%n", strategyContributed);
        assertThat(strategyContributed)
                .as("StrategyPromptSection should contribute by turn 4 (primed cases -> async reflect)")
                .isTrue();
    }

    @Test
    void moodEvolvesAcrossTurns() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.SIGNALS, 6);
        var moodT1 = result.metrics().getFirst().promptSectionContent().get("MoodPromptSection");
        var moodLast = result.metrics().getLast().promptSectionContent().get("MoodPromptSection");
        assertThat(moodT1).isNotNull();
        assertThat(moodLast).isNotNull();
        assertThat(moodT1).isNotEqualTo(moodLast);
        System.out.println("[#279 proof] Mood evolved between turns");
    }

    @Test
    void mentalModelFormsBeliefsAndDesires() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.SIGNALS, 4);
        var mmContributed = result.metrics().stream()
                .anyMatch(m -> {
                    var content = m.promptSectionContent().get("MentalModelPromptSection");
                    return content != null && !content.isBlank();
                });
        System.out.printf("[#279 proof] MentalModelPromptSection contributed: %s%n", mmContributed);
        assertThat(mmContributed).isTrue();
    }

    @Test
    void drivesReflectRealSources() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.REAL_DRIVES, 6);
        var driveContributed = result.metrics().stream()
                .anyMatch(m -> m.promptSectionContent().containsKey("DrivePromptSection"));
        System.out.printf("[#279 proof] DrivePromptSection contributed: %s%n", driveContributed);
        assertThat(driveContributed).isTrue();
    }

    @Test
    void userModelContributes() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.SIGNALS, 6);
        var umContributed = result.metrics().stream()
                .anyMatch(m -> m.promptSectionContent().containsKey("UserModelPromptSection"));
        System.out.printf("[#279 proof] UserModelPromptSection contributed: %s%n", umContributed);
        assertThat(umContributed).isTrue();
    }

    @Test
    void goalsProposedByTurn8() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.FULL, 8);
        var goalsContributed = result.metrics().stream()
                .anyMatch(m -> m.promptSectionContent().containsKey("GoalPromptSection"));
        System.out.printf("[#279 proof] GoalPromptSection contributed: %s%n", goalsContributed);
        assertThat(goalsContributed)
                .as("GoalPromptSection should contribute by turn 8")
                .isTrue();
    }

    @Test
    void narrativeFormsContent() throws IOException {
        var result = runShortConversation(CognitionStack.Stage.NARRATIVE, 6);
        var narContributed = result.metrics().stream()
                .anyMatch(m -> {
                    var content = m.promptSectionContent().get("NarrativePromptSection");
                    return content != null && !content.isBlank();
                });
        System.out.printf("[#279 proof] NarrativePromptSection contributed: %s%n", narContributed);
        assertThat(narContributed).isTrue();
    }

    private ConversationRunner.ConversationResult runShortConversation(
            CognitionStack.Stage stage, int turns) throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider, stage);

        return ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(turns)
                .includeCognition(true)
                .build()
                .run();
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
