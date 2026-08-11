package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PromptOptimisationBatch {

    private static final System.Logger LOG = System.getLogger(PromptOptimisationBatch.class.getName());
    private static final double DEFAULT_PROMOTION_MARGIN = 0.05;
    private static final int DEFAULT_MIN_PROMOTION_CYCLES = 2;

    private final List<PromptOptimiser> optimisers;
    private final PromptQualityMetric metric;
    private final PromptVariantStore store;
    private final SafetyConfig safetyConfig;
    private final Set<String> runningSignatures = ConcurrentHashMap.newKeySet();

    public PromptOptimisationBatch(
            List<PromptOptimiser> optimisers,
            PromptQualityMetric metric,
            PromptVariantStore store,
            SafetyConfig safetyConfig) {
        this.optimisers = List.copyOf(optimisers);
        this.metric = metric;
        this.store = store;
        this.safetyConfig = safetyConfig;
    }

    public CompletionStage<BatchResult> run(
            PromptSignature signature,
            OptimisationDataset dataset,
            OptimiserConfig config) {

        if (!runningSignatures.add(signature.id())) {
            return CompletableFuture.completedFuture(new BatchResult.AlreadyRunning(signature.id()));
        }

        try {
            return doRun(signature, dataset, config)
                    .whenComplete((r, t) -> runningSignatures.remove(signature.id()));
        } catch (Exception e) {
            runningSignatures.remove(signature.id());
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletionStage<BatchResult> doRun(
            PromptSignature signature,
            OptimisationDataset dataset,
            OptimiserConfig config) {

        var outcomes = dataset.outcomes().stream()
                .filter(o -> o.variantId() != null && !o.variantId().isEmpty())
                .toList();

        if (outcomes.size() < config.minOutcomeCount()) {
            return CompletableFuture.completedFuture(
                    new BatchResult.InsufficientData(outcomes.size(), config.minOutcomeCount()));
        }

        final var control = store.getActive(signature.id(), "control");
        var currentExperiment = store.getActive(signature.id(), "experiment");

        var byVariant = outcomes.stream().collect(Collectors.groupingBy(VariantOutcome::variantId));
        double controlScore = scoreVariant(control, byVariant);
        double experimentScore = scoreVariant(currentExperiment, byVariant);

        if (currentExperiment != null) {
            var expOutcomes = byVariant.getOrDefault(currentExperiment.variantId(), List.of());
            if (!expOutcomes.isEmpty() && experimentScore < safetyConfig.qualityFloor()) {
                store.activate(signature.id(), null, "experiment");
                LOG.log(System.Logger.Level.INFO,
                        "Experiment {0} deactivated: quality {1} below floor {2}",
                        currentExperiment.variantId(), experimentScore, safetyConfig.qualityFloor());
                currentExperiment = null;
            }
        }

        if (currentExperiment != null) {
            var age = Duration.between(currentExperiment.createdAt(), Instant.now());
            if (age.compareTo(safetyConfig.maxExperimentAge()) > 0) {
                store.activate(signature.id(), null, "experiment");
                currentExperiment = null;
            }
        }

        if (currentExperiment != null && control != null) {
            final var experiment = currentExperiment;
            var expOutcomes = byVariant.getOrDefault(experiment.variantId(), List.of());
            if (expOutcomes.size() >= config.minVariantOutcomes()) {
                if (experimentScore > controlScore + DEFAULT_PROMOTION_MARGIN) {
                    int wins = experiment.consecutiveWins() + 1;
                    if (wins >= DEFAULT_MIN_PROMOTION_CYCLES) {
                        store.activate(signature.id(), experiment.variantId(), "control");
                        store.activate(signature.id(), null, "experiment");
                        return runOptimisersAndCreateVariant(signature, dataset, config, experiment)
                                .thenApply(newVariant -> {
                                    if (newVariant != null) {
                                        store.store(newVariant);
                                        store.activate(signature.id(), newVariant.variantId(), "experiment");
                                    }
                                    return (BatchResult) new BatchResult.VariantPromoted(experiment, control);
                                });
                    } else {
                        var updated = new PromptVariant(experiment.signatureId(), experiment.variantId(),
                                experiment.examples(), experiment.instructionDelta(), experimentScore,
                                experiment.createdAt(), experiment.parentVariantId(), wins);
                        store.store(updated);
                        store.activate(signature.id(), updated.variantId(), "experiment");
                    }
                } else if (experimentScore < controlScore - DEFAULT_PROMOTION_MARGIN) {
                    store.activate(signature.id(), null, "experiment");
                    currentExperiment = null;
                } else {
                    if (experiment.consecutiveWins() > 0) {
                        var reset = new PromptVariant(experiment.signatureId(), experiment.variantId(),
                                experiment.examples(), experiment.instructionDelta(), experimentScore,
                                experiment.createdAt(), experiment.parentVariantId(), 0);
                        store.store(reset);
                        store.activate(signature.id(), reset.variantId(), "experiment");
                    }
                }
            }
        }

        if (store.getActive(signature.id(), "experiment") == null) {
            return runOptimisersAndCreateVariant(signature, dataset, config, control)
                    .thenApply(newVariant -> {
                        if (newVariant == null) {
                            return (BatchResult) new BatchResult.NoImprovement(controlScore, 0.0);
                        }
                        store.store(newVariant);
                        store.activate(signature.id(), newVariant.variantId(), "experiment");
                        return (BatchResult) new BatchResult.VariantCreated(newVariant, "experiment");
                    });
        }

        return CompletableFuture.completedFuture(
                new BatchResult.NoImprovement(controlScore, experimentScore));
    }

    private double scoreVariant(@Nullable PromptVariant variant,
                                Map<String, List<VariantOutcome>> byVariant) {
        if (variant == null) return 0.0;
        var outcomes = byVariant.getOrDefault(variant.variantId(), List.of());
        return outcomes.isEmpty() ? 0.0 : metric.score(outcomes);
    }

    private CompletionStage<@Nullable PromptVariant> runOptimisersAndCreateVariant(
            PromptSignature signature,
            OptimisationDataset dataset,
            OptimiserConfig config,
            @Nullable PromptVariant parent) {

        List<FewShotExample> allExamples = new ArrayList<>();
        List<String> allDeltas = new ArrayList<>();

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);

        for (var optimiser : optimisers) {
            chain = chain.thenCompose(ignored -> {
                try {
                    return optimiser.optimise(signature, parent, dataset, config)
                            .thenAccept(result -> {
                                synchronized (allExamples) {
                                    allExamples.addAll(result.examples());
                                    if (result.instructionDelta() != null) {
                                        allDeltas.add(result.instructionDelta());
                                    }
                                }
                            })
                            .exceptionally(e -> {
                                LOG.log(System.Logger.Level.WARNING,
                                        "Optimiser {0} failed", optimiser.id(), e);
                                return null;
                            });
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Optimiser {0} threw", optimiser.id(), e);
                    return CompletableFuture.completedFuture(null);
                }
            });
        }

        return chain.thenApply(ignored -> {
            if (allExamples.isEmpty() && allDeltas.isEmpty()) return null;

            var deduped = allExamples.stream()
                    .collect(Collectors.toMap(FewShotExample::input, e -> e, (a, b) -> a,
                            LinkedHashMap::new))
                    .values().stream()
                    .limit(config.maxExamples())
                    .toList();

            var delta = allDeltas.isEmpty() ? null : String.join("\n\n", allDeltas);
            var variantId = "v-" + System.currentTimeMillis() + "-"
                    + Integer.toHexString(ThreadLocalRandom.current().nextInt(0xFFFF));

            return new PromptVariant(
                    signature.id(), variantId, deduped, delta, 0.0,
                    Instant.now(), parent != null ? parent.variantId() : null, 0);
        });
    }
}
