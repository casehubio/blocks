package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class OutcomeAwareDiversityStrategy implements DiversityStrategy {

    private final double diversityWeight;

    public OutcomeAwareDiversityStrategy(double diversityWeight) {
        if (diversityWeight < 0 || diversityWeight > 1)
            throw new IllegalArgumentException("diversityWeight must be in [0, 1]");
        this.diversityWeight = diversityWeight;
    }

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        if (shortlist.isEmpty() || maxExamples <= 0) return List.of();
        if (shortlist.size() <= maxExamples) return shortlist;

        var selected = new ArrayList<ExampleCandidate>();

        if (diversityWeight > 0) {
            var byOutcome = new LinkedHashMap<String, List<ExampleCandidate>>();
            for (var c : shortlist) {
                byOutcome.computeIfAbsent(c.outcome().toLowerCase(), k -> new ArrayList<>()).add(c);
            }
            for (var group : byOutcome.values()) {
                if (selected.size() >= maxExamples) break;
                group.stream()
                        .max(Comparator.comparingDouble(c -> c.qualityScore() * c.similarityScore()))
                        .ifPresent(selected::add);
            }
        }

        var remaining = new ArrayList<>(shortlist);
        remaining.removeAll(selected);

        while (selected.size() < maxExamples && !remaining.isEmpty()) {
            ExampleCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (var candidate : remaining) {
                double relevance = candidate.qualityScore() * candidate.similarityScore();
                double maxSim = selected.stream()
                        .mapToDouble(s -> jaccard(candidate, s))
                        .max().orElse(0.0);
                double score = (1 - diversityWeight) * relevance - diversityWeight * maxSim;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            selected.add(best);
            remaining.remove(best);
        }

        selected.sort(Comparator.comparingDouble(
                (ExampleCandidate c) -> c.qualityScore() * c.similarityScore()).reversed());
        return List.copyOf(selected);
    }

    private static double jaccard(ExampleCandidate a, ExampleCandidate b) {
        Set<String> tokensA = tokenise(a);
        Set<String> tokensB = tokenise(b);
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 0.0;
        var intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        var union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokenise(ExampleCandidate c) {
        String text = c.input() + " " + c.output();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Set.of();
        return new HashSet<>(List.of(trimmed.split("\\s+")));
    }
}
