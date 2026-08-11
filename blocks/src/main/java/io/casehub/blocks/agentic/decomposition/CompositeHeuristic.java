package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CompositeHeuristic<T> implements DecompositionHeuristic<T> {

    public record WeightedHeuristic<T>(DecompositionHeuristic<T> heuristic, double weight) {
        public WeightedHeuristic {
            Objects.requireNonNull(heuristic, "heuristic");
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        }
    }

    private final List<WeightedHeuristic<T>> delegates;

    public CompositeHeuristic(List<WeightedHeuristic<T>> delegates) {
        if (delegates.isEmpty()) throw new IllegalArgumentException("delegates must not be empty");
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public List<ScoredMethod<T>> evaluate(TaskNode.CompoundTask<T> task,
                                           List<DecompositionMethod<T>> methods,
                                           DecompositionContext<T> context) {
        var allResults = new ArrayList<List<ScoredMethod<T>>>();

        for (var delegate : delegates) {
            var scored = delegate.heuristic().evaluate(task, methods, context);
            if (scored.size() != methods.size()) {
                throw new IllegalStateException(
                        "Heuristic returned " + scored.size() + " scores for "
                        + methods.size() + " methods — completeness contract violated");
            }
            allResults.add(scored);
        }

        return combine(methods, allResults);
    }

    private List<ScoredMethod<T>> combine(List<DecompositionMethod<T>> methods,
                                           List<List<ScoredMethod<T>>> delegateResults) {
        int n = methods.size();
        double[] compositeScores = new double[n];
        double totalWeight = delegates.stream().mapToDouble(WeightedHeuristic::weight).sum();

        for (int d = 0; d < delegates.size(); d++) {
            var scores = delegateResults.get(d);
            double[] raw = new double[n];
            for (int i = 0; i < n; i++) {
                raw[i] = scores.get(i).score();
            }

            double[] normalized = normalize(raw);

            for (int i = 0; i < n; i++) {
                compositeScores[i] += delegates.get(d).weight() * normalized[i];
            }
        }

        var result = new ArrayList<ScoredMethod<T>>(n);
        for (int i = 0; i < n; i++) {
            result.add(new ScoredMethod<>(methods.get(i), compositeScores[i] / totalWeight));
        }
        return result;
    }

    private static double[] normalize(double[] values) {
        double min = values[0], max = values[0];
        for (double v : values) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double range = max - min;
        double[] result = new double[values.length];
        if (range == 0) {
            Arrays.fill(result, 0.5);
        } else {
            for (int i = 0; i < values.length; i++) {
                result[i] = (values[i] - min) / range;
            }
        }
        return result;
    }
}
