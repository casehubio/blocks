package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

class CognitionDiagnostic {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    @Test
    void showFullPromptWithCognition() throws IOException {
        var compiled = loadCognition();
        var world = loadWorld();
        var descriptors = DescriptorLoader.load("historical-encounter");
        var agentProvider = TestAgentProvider.claude();

        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.SIGNALS);

        System.out.println("Running 2-turn conversation with FULL system prompt logging...");
        System.out.println("Watch how the system prompt GROWS with cognitive state each turn.\n");

        var result = ConversationRunner.builder()
                .agentProvider(agentProvider)
                .cognition(stack)
                .world(world)
                .descriptors(descriptors)
                .maxTurns(2)
                .includeCognition(true)
                .build()
                .run();

        System.out.printf("%nConversation complete. %d turns, %s%n",
                result.turnCount(), result.elapsed());
    }

    @Test
    void showPromptSectionsBeforeAndAfterSignals() throws IOException {
        var compiled = loadCognition();
        var agentProvider = TestAgentProvider.claude();
        var stack = CognitionStack.from(compiled, agentProvider,
                CognitionStack.Stage.SIGNALS);
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        System.out.println("=== BEFORE any interaction ===");
        System.out.println("(This is what gets injected into the system prompt on Turn 1)\n");
        var ctx = new PromptContext("leonardo", "showcase", "nikola");
        for (var section : stack.promptSections()) {
            var text = section.contribute(ctx);
            var sectionName = section instanceof DirectivePromptSection dps
                    ? dps.delegateName() : section.getClass().getSimpleName();
            if (text != null && !text.isBlank()) {
                System.out.printf("--- %s ---\n%s\n\n", sectionName, text);
            } else {
                System.out.printf("--- %s --- (empty)\n\n", sectionName);
            }
        }

        // Tick + record a simulated interaction
        stack.tick("leonardo", "showcase", leonardo, Set.of("nikola"));
        stack.core().recordInteraction("leonardo", "showcase", "nikola",
                "I am fascinated by your rotating magnetic field. " +
                "The way you harness invisible forces reminds me of " +
                "how water flows through my canal designs.",
                "Your understanding of fluid dynamics is remarkable. " +
                "The vortices you observed in water are the same " +
                "mathematical structures I see in electromagnetic fields.");

        // Tick again to process signals
        stack.tick("leonardo", "showcase", leonardo, Set.of("nikola"));

        System.out.println("\n=== AFTER one interaction ===");
        System.out.println("(This is what gets injected into the system prompt on Turn 2)\n");
        for (var section : stack.promptSections()) {
            var text = section.contribute(ctx);
            var sectionName = section instanceof DirectivePromptSection dps
                    ? dps.delegateName() : section.getClass().getSimpleName();
            if (text != null && !text.isBlank()) {
                System.out.printf("--- %s ---\n%s\n\n", sectionName, text);
            } else {
                System.out.printf("--- %s --- (empty)\n\n", sectionName);
            }
        }

        // Show snapshot
        var snapshot = stack.snapshot("leonardo", "showcase", 2, Set.of("nikola"));
        System.out.println("=== COGNITIVE STATE ===");
        System.out.printf("Mood: P=%.2f A=%.2f D=%.2f%n",
                snapshot.mood().pleasure(), snapshot.mood().arousal(),
                snapshot.mood().dominance());
        if (snapshot.drives() != null) {
            System.out.printf("Drives: dominant=%s composite=%.2f%n",
                    snapshot.drives().dominantDrive(),
                    snapshot.drives().compositeMotivation());
            snapshot.drives().drives().forEach((axis, intensity) ->
                    System.out.printf("  %s: %.2f (%s)%n",
                            axis, intensity.intensity(), intensity.trigger()));
        }
        var mentalModel = snapshot.mentalModels().get("nikola");
        if (mentalModel != null) {
            System.out.println("\nMental model of nikola:");
            mentalModel.beliefs().forEach(b ->
                    System.out.printf("  BELIEF: %s (%.2f) — %s%n",
                            b.key(), b.confidence(), b.description()));
            mentalModel.desires().forEach(d ->
                    System.out.printf("  DESIRE: %s (%.2f) — %s%n",
                            d.key(), d.confidence(), d.description()));
            mentalModel.intentions().forEach(i ->
                    System.out.printf("  INTENTION: %s (%.2f) — %s%n",
                            i.key(), i.confidence(), i.description()));
        }
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledCognition loadCognition() throws IOException {
        try (var is = CognitionDiagnostic.class.getResourceAsStream(
                "/examples/historical-encounter/cognition.yaml")) {
            return new CognitionCompiler().compile(
                    YAML.readValue(is, CognitionDefinition.class));
        }
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledWorld loadWorld() throws IOException {
        try (var is = CognitionDiagnostic.class.getResourceAsStream(
                "/examples/historical-encounter/world.yaml")) {
            return new WorldCompiler(new ObservationFilterRegistry())
                    .compile(YAML.readValue(is, WorldDefinition.class));
        }
    }
}
