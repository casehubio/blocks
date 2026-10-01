package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.blocks.agentic.yaml.compiler.CompiledWorld;
import io.casehub.blocks.agentic.yaml.spec.ConversationSpec;
import io.casehub.blocks.agentic.yaml.spec.JointIntentionSpec;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.blocks.agentic.yaml.registry.ConvergencePolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.EpistemicRuleRegistry;
import io.casehub.blocks.agentic.yaml.registry.TurnPolicyRegistry;
import io.casehub.blocks.summarisation.observation.affordance.AffordanceRenderer;
import io.casehub.blocks.summarisation.observation.affordance.AnnotatedSection;
import io.casehub.blocks.summarisation.observation.affordance.ObservationSection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalEncounterExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "historical-encounter";

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.routing()).isInstanceOf(RoundRobinRouting.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("leonardo");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("nikola");
        assertThat(candidates.get(0).descriptor().capabilities()).hasSize(4);

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void fullCognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.CURIOSITY)).isEqualTo(2.0);
        assertThat(compiled.drive().narrativeModulationStrength()).isEqualTo(0.3);

        assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.7);
        assertThat(compiled.mood().moodInfluence()).isEqualTo(0.4);

        assertThat(compiled.personality().l2Ceiling()).isEqualTo(0.3);
        assertThat(compiled.personality().dampeningFactor()).isEqualTo(0.15);

        assertThat(compiled.userModel().synthesisCooldown()).isNotNull();
        assertThat(compiled.userModel().positiveWeight()).isEqualTo(1.5);
        assertThat(compiled.userModel().maxObservationsInPrompt()).isEqualTo(15);

        assertThat(compiled.strategyLearning().minSignalsForConversationCase()).isEqualTo(2);
        assertThat(compiled.strategyLearning().maxReflectionSources()).isEqualTo(10);
        assertThat(compiled.strategyLearning().maxBufferSize()).isEqualTo(50);

        assertThat(compiled.mentalModel().beliefHalfLife()).isNotNull();
        assertThat(compiled.mentalModel().projectionFloor()).isEqualTo(0.15);
        assertThat(compiled.mentalModel().maxSignalsInPrompt()).isEqualTo(12);
        assertThat(compiled.mentalModel().maxBufferSize()).isEqualTo(40);

        assertThat(compiled.goalProposal().staleAfter()).isNotNull();
        assertThat(compiled.goalProposal().cooldown()).isNotNull();
        assertThat(compiled.goalProposal().failureAbandonmentThreshold()).isEqualTo(2);

        assertThat(compiled.goalEscalation().minAxisAlignmentWeight()).isEqualTo(0.3);
        assertThat(compiled.goalEscalation().crossAxisMinWeight()).isEqualTo(0.2);
        assertThat(compiled.goalEscalation().minCrossAxisCount()).isEqualTo(2);

        assertThat(compiled.normDetection().decliningThreshold()).isEqualTo(0.25);

        assertThat(compiled.collectiveGoal().cooldown()).isNotNull();

        assertThat(compiled.narrative().maxThemes()).isEqualTo(8);
        assertThat(compiled.narrative().themeSalienceFloor()).isEqualTo(0.2);
        assertThat(compiled.narrative().maxReflectionsPerSynthesis()).isEqualTo(10);
        assertThat(compiled.narrative().synthesisGate().quietPeriodBypass()).isNotNull();

        assertThat(compiled.retention().retentionThreshold()).isEqualTo(0.3);
        assertThat(compiled.retention().confidenceWeight()).isEqualTo(0.25);
        assertThat(compiled.retention().scopeWeight()).isEqualTo(0.2);
        assertThat(compiled.retention().trustWeight()).isEqualTo(0.2);
    }

    @Test
    void immersiveWorldCompiles() throws IOException {
        var def = loadWorld(SCENARIO);
        var compiled = worldCompiler.compile(def);

        assertThat(compiled.actions()).hasSize(4);
        assertThat(compiled.actions().get(0).actionType()).isEqualTo("RESEARCH");
        assertThat(compiled.actions().get(3).actionType()).isEqualTo("ASK");

        assertThat(compiled.entities()).hasSize(4);
        assertThat(compiled.entities().get("leonardo").displayName()).isEqualTo("Leonardo da Vinci");
        assertThat(compiled.entities().get("nikola").affordances()).hasSize(2);
        assertThat(compiled.entities().get("codex").displayName()).isEqualTo("Leonardo's Codex");

        assertThat(compiled.sections()).hasSize(5);

        assertThat(compiled.sections().get(0)).isInstanceOf(AnnotatedSection.class);
        var setting = (AnnotatedSection) compiled.sections().get(0);
        assertThat(setting.header()).isEqualTo("The Setting");
        assertThat(setting.interpretiveFrame()).isNotNull();

        assertThat(compiled.sections().get(3)).isInstanceOf(AnnotatedSection.class);
        var journals = (AnnotatedSection) compiled.sections().get(3);
        assertThat(journals.requiredTags()).containsExactly("deep-trust");
        assertThat(journals.resolutions()).containsKey(
                io.casehub.blocks.summarisation.observation.affordance.ResolutionTier.REDUCED);

        var seeds = (ObservationSection.ItemList) compiled.sections().get(4);
        assertThat(seeds.items()).hasSize(5);

        CompiledWorld.RendererThresholds thresholds = compiled.rendererThresholds();
        assertThat(thresholds).isNotNull();
        assertThat(thresholds.verbatimThreshold()).isEqualTo(12);
    }

    @Test
    void worldRendersWithVisibilityGating() throws IOException {
        var def = loadWorld(SCENARIO);
        var compiled = worldCompiler.compile(def);
        var renderer = new AffordanceRenderer();

        var allSections = compiled.sections();
        var withTrust = compiled.pipeline().apply(allSections, Set.of("deep-trust"));
        var withoutTrust = compiled.pipeline().apply(allSections, Set.of());

        System.out.println("=== Historical Encounter — World Observation ===");
        System.out.println();
        System.out.println("--- What an agent sees (no deep-trust tag) ---");
        String restrictedView = renderer.renderObservation(withoutTrust);
        System.out.println(restrictedView);
        System.out.println();
        System.out.println("--- What an agent sees (with deep-trust tag) ---");
        String fullView = renderer.renderObservation(withTrust);
        System.out.println(fullView);
        System.out.println();
        System.out.println("--- Action vocabulary ---");
        System.out.println(renderer.renderActionVocabulary("Available actions:", compiled.actions()));

        assertThat(fullView).as("With trust: sees Tesla's Private Diary")
                .contains("Tesla's Private Diary");
        assertThat(restrictedView).as("Without trust: diary degraded to fallback text")
                .doesNotContain("Tesla's Private Diary");
        assertThat(restrictedView).as("Without trust: sees reduced-fidelity fallback")
                .contains("deeper trust is needed");
    }

    @Test
    void conversationSpecCompiles() throws IOException {
        var spec = load(SCENARIO, "conversation.yaml", ConversationSpec.class);

        assertThat(spec.turnPolicy()).isNotNull();
        var turnPolicy = new TurnPolicyRegistry().resolve(spec.turnPolicy());
        assertThat(turnPolicy).isInstanceOf(RoundRobinTurnPolicy.class);

        assertThat(spec.epistemicRule()).isNotNull();
        var epistemicRule = new EpistemicRuleRegistry().resolve(spec.epistemicRule());
        assertThat(epistemicRule).isNotNull();

        assertThat(spec.convergencePolicy()).isNotNull();
        var convergencePolicy = new ConvergencePolicyRegistry().resolve(spec.convergencePolicy());
        assertThat(convergencePolicy).isNotNull();
    }

    @Test
    void jointIntentionParses() throws IOException {
        var spec = load(SCENARIO, "joint-intention.yaml", JointIntentionSpec.class);

        assertThat(spec.intentionId()).isEqualTo("mutual-understanding");
        assertThat(spec.planDescription()).contains("shared understanding");
        assertThat(spec.parties()).containsExactlyInAnyOrder("leonardo", "nikola");
    }
}
