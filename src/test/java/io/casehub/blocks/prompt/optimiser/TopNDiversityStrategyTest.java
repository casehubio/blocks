package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopNDiversityStrategyTest {

    private ExampleCandidate candidate(String outcome, double quality) {
        return new ExampleCandidate("input", "output", outcome, quality, 0.8,
                "v1", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void returnsFirstNFromShortlist() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var shortlist = List.of(
                candidate("SUCCESS", 0.9),
                candidate("SUCCESS", 0.8),
                candidate("SUCCESS", 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.get(1).qualityScore()).isEqualTo(0.8);
    }

    @Test
    void shortlistSmallerThanMaxReturnsAll() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var shortlist = List.of(candidate("SUCCESS", 0.9));
        var result = strategy.select(shortlist, 5);
        assertThat(result).hasSize(1);
    }

    @Test
    void emptyShortlistReturnsEmpty() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var result = strategy.select(List.of(), 5);
        assertThat(result).isEmpty();
    }
}
