package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.ExampleCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutcomeAwareDiversityStrategyTest {

    private ExampleCandidate candidate(String input, String output, String outcome,
                                       double quality, double similarity) {
        return new ExampleCandidate(input, output, outcome, quality, similarity,
                "v1", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void seedsFromMultipleOutcomeCategories() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("b", "y", "SUCCESS", 0.85, 0.85),
                candidate("c", "z", "FAILURE", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        var outcomes = result.stream().map(ExampleCandidate::outcome).toList();
        assertThat(outcomes).contains("SUCCESS", "FAILURE");
    }

    @Test
    void penalisesTextuallyIdenticalCandidates() {
        var strategy = new OutcomeAwareDiversityStrategy(0.8);
        var shortlist = List.of(
                candidate("same input", "same output", "SUCCESS", 0.9, 0.9),
                candidate("same input", "same output", "SUCCESS", 0.88, 0.88),
                candidate("different input entirely", "different output", "SUCCESS", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).anyMatch(c -> c.input().equals("different input entirely"));
    }

    @Test
    void zeroWeightDegeneratesToPureRelevance() {
        var strategy = new OutcomeAwareDiversityStrategy(0.0);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("a", "x", "SUCCESS", 0.8, 0.8),
                candidate("b", "y", "FAILURE", 0.5, 0.5));
        var result = strategy.select(shortlist, 2);
        assertThat(result.get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.get(1).qualityScore()).isEqualTo(0.8);
    }

    @Test
    void emptyShortlistReturnsEmpty() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        assertThat(strategy.select(List.of(), 5)).isEmpty();
    }

    @Test
    void shortlistSmallerThanMaxReturnsAll() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(candidate("a", "x", "SUCCESS", 0.9, 0.9));
        assertThat(strategy.select(shortlist, 5)).hasSize(1);
    }

    @Test
    void rejectsWeightOutOfRange() {
        assertThatThrownBy(() -> new OutcomeAwareDiversityStrategy(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutcomeAwareDiversityStrategy(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesEmptyInputOutputWithoutCrashing() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("", "", "SUCCESS", 0.9, 0.9),
                candidate("", "", "FAILURE", 0.8, 0.8),
                candidate("some text", "other text", "SUCCESS", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).hasSize(2);
    }

    @Test
    void outcomeGroupingIsCaseInsensitive() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("b", "y", "success", 0.85, 0.85),
                candidate("c", "z", "FAILURE", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        var outcomes = result.stream().map(c -> c.outcome().toUpperCase()).toList();
        assertThat(outcomes).contains("SUCCESS", "FAILURE");
    }
}
