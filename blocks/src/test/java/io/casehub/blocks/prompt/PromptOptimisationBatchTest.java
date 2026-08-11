package io.casehub.blocks.prompt;

import io.casehub.blocks.prompt.runtime.InMemoryPromptVariantStore;
import io.casehub.blocks.prompt.runtime.WeightedOutcomeMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class PromptOptimisationBatchTest {

    private InMemoryPromptVariantStore store;
    private WeightedOutcomeMetric metric;
    private PromptSignature signature;

    @BeforeEach
    void setUp() {
        store = new InMemoryPromptVariantStore();
        metric = new WeightedOutcomeMetric();
        signature = new PromptSignature("test-sig", "Test", "Base prompt.", Object.class, Object.class);
    }

    private VariantOutcome outcome(String variantId, String result) {
        return new VariantOutcome(variantId, "test-sig", result, 0.8, null, Instant.now());
    }

    private PromptOptimiser stubOptimiser(List<FewShotExample> examples, String delta) {
        return new PromptOptimiser() {
            @Override
            public String id() { return "stub"; }

            @Override
            public java.util.concurrent.CompletionStage<OptimiserResult> optimise(
                    PromptSignature sig, PromptVariant current, OptimisationDataset ds, OptimiserConfig cfg) {
                return CompletableFuture.completedFuture(new OptimiserResult(examples, delta, 0.0));
            }
        };
    }

    @Test
    void insufficientDataReturnsBelowMinimum() {
        var batch = new PromptOptimisationBatch(List.of(), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.7, 50, 20);
        var dataset = new OptimisationDataset(
                List.of(outcome("v1", "SUCCESS")), List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.InsufficientData.class);
        var insufficient = (BatchResult.InsufficientData) result;
        assertThat(insufficient.count()).isEqualTo(1);
        assertThat(insufficient.minimum()).isEqualTo(50);
    }

    @Test
    void excludesEmptyVariantIdOutcomesFromCount() {
        var batch = new PromptOptimisationBatch(List.of(), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.7, 2, 1);
        var dataset = new OptimisationDataset(
                List.of(new VariantOutcome("", "test-sig", "SUCCESS", 0.8, null, Instant.now()),
                        outcome("v1", "SUCCESS")), List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.InsufficientData.class);
        assertThat(((BatchResult.InsufficientData) result).count()).isEqualTo(1);
    }

    @Test
    void createsExperimentVariantWhenNoExperimentExists() {
        var example = new FewShotExample("in", "out", "SUCCESS", 0.9, null);
        var batch = new PromptOptimisationBatch(
                List.of(stubOptimiser(List.of(example), null)), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var dataset = new OptimisationDataset(
                List.of(outcome("v-control", "SUCCESS")), List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.VariantCreated.class);
        var created = (BatchResult.VariantCreated) result;
        assertThat(created.assignedSlot()).isEqualTo("experiment");
        assertThat(created.variant().examples()).hasSize(1);
    }

    @Test
    void promotesExperimentAfterConsecutiveWins() {
        var batch = new PromptOptimisationBatch(
                List.of(stubOptimiser(List.of(), null)), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 5, 1);

        var control = new PromptVariant("test-sig", "v-control", List.of(), null, 0.5,
                Instant.now(), null, 0);
        store.store(control);
        store.activate("test-sig", "v-control", "control");

        var experiment = new PromptVariant("test-sig", "v-exp", List.of(), null, 0.9,
                Instant.now(), "v-control", 1);
        store.store(experiment);
        store.activate("test-sig", "v-exp", "experiment");

        var outcomes = List.of(
                outcome("v-control", "SUCCESS"), outcome("v-control", "FAILURE"),
                outcome("v-control", "FAILURE"), outcome("v-control", "FAILURE"),
                outcome("v-exp", "SUCCESS"), outcome("v-exp", "SUCCESS"),
                outcome("v-exp", "SUCCESS"), outcome("v-exp", "SUCCESS"),
                outcome("v-exp", "SUCCESS"));
        var dataset = new OptimisationDataset(outcomes, List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.VariantPromoted.class);
    }

    @Test
    void discardsExperimentBelowQualityFloor() {
        var safetyConfig = new SafetyConfig(0.3, 5, Duration.ofDays(30), 5, true);
        var batch = new PromptOptimisationBatch(
                List.of(stubOptimiser(List.of(), null)), metric, store, safetyConfig);
        var config = new OptimiserConfig(5, 0.0, 3, 1);

        var control = new PromptVariant("test-sig", "v-control", List.of(), null, 0.8,
                Instant.now(), null, 0);
        store.store(control);
        store.activate("test-sig", "v-control", "control");

        var experiment = new PromptVariant("test-sig", "v-exp", List.of(), null, 0.1,
                Instant.now(), "v-control", 0);
        store.store(experiment);
        store.activate("test-sig", "v-exp", "experiment");

        var outcomes = List.of(
                outcome("v-control", "SUCCESS"),
                outcome("v-exp", "FAILURE"), outcome("v-exp", "FAILURE"), outcome("v-exp", "FAILURE"));
        var dataset = new OptimisationDataset(outcomes, List.of());
        batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(store.getActive("test-sig", "experiment")).isNull();
    }

    @Test
    void noImprovementWhenOptimiserProducesNothingAndExperimentActive() {
        var batch = new PromptOptimisationBatch(
                List.of(stubOptimiser(List.of(), null)), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 2, 1);

        var control = new PromptVariant("test-sig", "v-control", List.of(), null, 0.8,
                Instant.now(), null, 0);
        store.store(control);
        store.activate("test-sig", "v-control", "control");

        var experiment = new PromptVariant("test-sig", "v-exp", List.of(), null, 0.8,
                Instant.now(), "v-control", 0);
        store.store(experiment);
        store.activate("test-sig", "v-exp", "experiment");

        var outcomes = List.of(
                outcome("v-control", "SUCCESS"), outcome("v-exp", "SUCCESS"));
        var dataset = new OptimisationDataset(outcomes, List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.NoImprovement.class);
    }

    @Test
    void alreadyRunningPreventsParallelExecution() throws Exception {
        var slowOptimiser = new PromptOptimiser() {
            @Override
            public String id() { return "slow"; }

            @Override
            public java.util.concurrent.CompletionStage<OptimiserResult> optimise(
                    PromptSignature sig, PromptVariant current, OptimisationDataset ds, OptimiserConfig cfg) {
                return CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return new OptimiserResult(List.of(), null, 0.0);
                });
            }
        };
        var batch = new PromptOptimisationBatch(List.of(slowOptimiser), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var dataset = new OptimisationDataset(
                List.of(outcome("v1", "SUCCESS")), List.of());
        var first = batch.run(signature, dataset, config);
        Thread.sleep(50);
        var second = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(second).isInstanceOf(BatchResult.AlreadyRunning.class);
        first.toCompletableFuture().join();
    }

    @Test
    void handlesOptimiserFailureGracefully() {
        var failingOptimiser = new PromptOptimiser() {
            @Override
            public String id() { return "failing"; }

            @Override
            public java.util.concurrent.CompletionStage<OptimiserResult> optimise(
                    PromptSignature sig, PromptVariant current, OptimisationDataset ds, OptimiserConfig cfg) {
                return CompletableFuture.failedFuture(new RuntimeException("LLM down"));
            }
        };
        var example = new FewShotExample("in", "out", "SUCCESS", 0.9, null);
        var batch = new PromptOptimisationBatch(
                List.of(failingOptimiser, stubOptimiser(List.of(example), null)),
                metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var dataset = new OptimisationDataset(
                List.of(outcome("v1", "SUCCESS")), List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.VariantCreated.class);
    }

    @Test
    void noImprovementWhenAllOptimisersFail() {
        var failingOptimiser = new PromptOptimiser() {
            @Override
            public String id() { return "failing"; }

            @Override
            public java.util.concurrent.CompletionStage<OptimiserResult> optimise(
                    PromptSignature sig, PromptVariant current, OptimisationDataset ds, OptimiserConfig cfg) {
                return CompletableFuture.failedFuture(new RuntimeException("LLM down"));
            }
        };
        var batch = new PromptOptimisationBatch(
                List.of(failingOptimiser), metric, store, SafetyConfig.defaults());
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var dataset = new OptimisationDataset(
                List.of(outcome("v1", "SUCCESS")), List.of());
        var result = batch.run(signature, dataset, config).toCompletableFuture().join();
        assertThat(result).isInstanceOf(BatchResult.NoImprovement.class);
    }
}
