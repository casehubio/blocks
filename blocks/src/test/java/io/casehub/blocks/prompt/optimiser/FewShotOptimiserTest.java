package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;
import io.casehub.blocks.prompt.OptimisationDataset;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.PromptSignature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotOptimiserTest {

    private ExampleCandidate candidate(String outcome, double quality, double similarity) {
        return new ExampleCandidate("input", "output", outcome, quality, similarity,
                "v1", Instant.now());
    }

    private OptimisationDataset dataset(List<ExampleCandidate> candidates) {
        return new OptimisationDataset(List.of(), candidates);
    }

    private PromptSignature signature() {
        return new PromptSignature("test", "Test", "prompt", Object.class, Object.class);
    }

    @Test
    void idIsFewShot() {
        assertThat(new FewShotOptimiser().id()).isEqualTo("few-shot");
    }

    @Test
    void selectsTopNByQualityTimesSimilarity() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(2, 0.0, 1, 1);
        var candidates = List.of(
                candidate("SUCCESS", 0.9, 0.8),
                candidate("SUCCESS", 0.5, 0.5),
                candidate("SUCCESS", 0.7, 0.9));
        var result = optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).hasSize(2);
        assertThat(result.examples().get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.examples().get(1).qualityScore()).isEqualTo(0.7);
    }

    @Test
    void filtersBelowQualityThreshold() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(5, 0.7, 1, 1);
        var candidates = List.of(
                candidate("SUCCESS", 0.9, 0.8),
                candidate("FAILURE", 0.3, 0.9),
                candidate("SUCCESS", 0.8, 0.7));
        var result = optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).hasSize(2);
    }

    @Test
    void returnsEmptyExamplesWhenNoCandidates() {
        var optimiser = new FewShotOptimiser();
        var config = OptimiserConfig.defaults();
        var result = optimiser.optimise(signature(), null, dataset(List.of()), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).isEmpty();
        assertThat(result.instructionDelta()).isNull();
    }

    @Test
    void capsAtMaxExamples() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(2, 0.0, 1, 1);
        var candidates = List.of(
                candidate("SUCCESS", 0.9, 0.9),
                candidate("SUCCESS", 0.8, 0.8),
                candidate("SUCCESS", 0.7, 0.7),
                candidate("SUCCESS", 0.6, 0.6));
        var result = optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).hasSize(2);
    }

    @Test
    void instructionDeltaIsAlwaysNull() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var result = optimiser.optimise(signature(), null,
                dataset(List.of(candidate("SUCCESS", 0.9, 0.9))), config)
                .toCompletableFuture().join();
        assertThat(result.instructionDelta()).isNull();
    }

    @Test
    void preservesInputOutputAndOutcomeFromCandidate() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(5, 0.0, 1, 1);
        var candidates = List.of(new ExampleCandidate(
                "Case: patient triage", "Selected: dr-smith", "SUCCESS",
                0.9, 0.8, "v1", Instant.now()));
        var result = optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).hasSize(1);
        var ex = result.examples().getFirst();
        assertThat(ex.input()).isEqualTo("Case: patient triage");
        assertThat(ex.output()).isEqualTo("Selected: dr-smith");
        assertThat(ex.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void noArgConstructorUsesTopNStrategy() {
        var optimiser = new FewShotOptimiser();
        var config = new OptimiserConfig(2, 0.0, 1, 1);
        var candidates = List.of(
                candidate("SUCCESS", 0.9, 0.9),
                candidate("SUCCESS", 0.8, 0.8),
                candidate("SUCCESS", 0.7, 0.7),
                candidate("SUCCESS", 0.6, 0.6));
        var result = optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(result.examples()).hasSize(2);
        assertThat(result.examples().get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.examples().get(1).qualityScore()).isEqualTo(0.8);
    }

    @Test
    void customStrategyReceivesDoubleShortlist() {
        var capturedSize = new AtomicInteger();
        DiversityStrategy spy = (shortlist, max) -> {
            capturedSize.set(shortlist.size());
            return shortlist.stream().limit(max).toList();
        };
        var optimiser = new FewShotOptimiser(spy);
        var config = new OptimiserConfig(2, 0.0, 1, 1);
        var candidates = List.of(
                candidate("SUCCESS", 0.9, 0.9),
                candidate("SUCCESS", 0.8, 0.8),
                candidate("SUCCESS", 0.7, 0.7),
                candidate("SUCCESS", 0.6, 0.6));
        optimiser.optimise(signature(), null, dataset(candidates), config)
                .toCompletableFuture().join();
        assertThat(capturedSize.get()).isEqualTo(4);
    }
}
