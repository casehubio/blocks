package io.casehub.blocks.prompt;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CoreModelTest {

    @Test
    void promptSignatureRejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptSignature(null, "desc", "prompt", Object.class, Object.class));
    }

    @Test
    void promptSignatureRejectsNullBaseSystemPrompt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptSignature("id", "desc", null, Object.class, Object.class));
    }

    @Test
    void promptSignatureAcceptsValidInputs() {
        var sig = new PromptSignature("llm-routing", "Routes agents", "You are a router.", Object.class, Object.class);
        assertThat(sig.id()).isEqualTo("llm-routing");
        assertThat(sig.baseSystemPrompt()).isEqualTo("You are a router.");
    }

    @Test
    void promptVariantRejectsNullSignatureId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptVariant(null, "v1", List.of(), null, 0.8, Instant.now(), null, 0));
    }

    @Test
    void promptVariantRejectsNullVariantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptVariant("sig", null, List.of(), null, 0.8, Instant.now(), null, 0));
    }

    @Test
    void promptVariantDefensiveCopiesExamples() {
        var examples = new ArrayList<>(List.of(
                new FewShotExample("in", "out", "SUCCESS", 0.9, null)));
        var variant = new PromptVariant("sig", "v1", examples, null, 0.8, Instant.now(), null, 0);
        examples.clear();
        assertThat(variant.examples()).hasSize(1);
    }

    @Test
    void optimiserConfigRejectsZeroMaxExamples() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimiserConfig(0, 0.7, 50, 20));
    }

    @Test
    void optimiserConfigRejectsNegativeThreshold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimiserConfig(5, -0.1, 50, 20));
    }

    @Test
    void optimiserConfigRejectsThresholdAboveOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimiserConfig(5, 1.1, 50, 20));
    }

    @Test
    void optimiserConfigDefaults() {
        var config = OptimiserConfig.defaults();
        assertThat(config.maxExamples()).isEqualTo(5);
        assertThat(config.minQualityThreshold()).isEqualTo(0.7);
        assertThat(config.minOutcomeCount()).isEqualTo(50);
        assertThat(config.minVariantOutcomes()).isEqualTo(20);
    }

    @Test
    void safetyConfigRejectsQualityFloorAboveOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SafetyConfig(1.1, 5, Duration.ofDays(30), 5, true));
    }

    @Test
    void safetyConfigRejectsNegativeMaxCycles() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SafetyConfig(0.3, -1, Duration.ofDays(30), 5, true));
    }

    @Test
    void safetyConfigDefaults() {
        var config = SafetyConfig.defaults();
        assertThat(config.qualityFloor()).isEqualTo(0.3);
        assertThat(config.maxExperimentCycles()).isEqualTo(5);
        assertThat(config.maxExperimentAge()).isEqualTo(Duration.ofDays(30));
        assertThat(config.circuitBreakerThreshold()).isEqualTo(5);
        assertThat(config.enabled()).isTrue();
    }

    @Test
    void batchResultSealedTypes() {
        assertThat(BatchResult.class).isSealed();
        assertThat(BatchResult.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "AlreadyRunning", "InsufficientData", "NoImprovement",
                        "VariantCreated", "VariantPromoted");
    }

    @Test
    void variantOutcomeRejectsNullOutcome() {
        assertThatNullPointerException()
                .isThrownBy(() -> new VariantOutcome("v1", "sig", null, 0.8, null, Instant.now()));
    }

    @Test
    void optimisationDatasetRejectsNullOutcomes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OptimisationDataset(null, List.of()));
    }

    @Test
    void optimisationDatasetDefensiveCopies() {
        var outcomes = new ArrayList<>(List.of(
                new VariantOutcome("v1", "sig", "SUCCESS", 0.8, null, Instant.now())));
        var candidates = new ArrayList<>(List.of(
                new ExampleCandidate("in", "out", "SUCCESS", 0.9, 0.8, "v1", Instant.now())));
        var dataset = new OptimisationDataset(outcomes, candidates);
        outcomes.clear();
        candidates.clear();
        assertThat(dataset.outcomes()).hasSize(1);
        assertThat(dataset.candidates()).hasSize(1);
    }

    @Test
    void fewShotExampleRejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FewShotExample(null, "out", "SUCCESS", 0.9, null));
    }

    @Test
    void exampleCandidateRejectsNullOutput() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ExampleCandidate("in", null, "SUCCESS", 0.9, 0.8, "v1", Instant.now()));
    }

    @Test
    void optimiserResultDefensiveCopiesExamples() {
        var examples = new ArrayList<>(List.of(
                new FewShotExample("in", "out", "SUCCESS", 0.9, null)));
        var result = new OptimiserResult(examples, null, 0.0);
        examples.clear();
        assertThat(result.examples()).hasSize(1);
    }
}
