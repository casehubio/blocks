package io.casehub.blocks.agentic.aggregation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AggregationResultTest {

    @Test
    void resolvedCarriesValue() {
        var result = new AggregationResult.Resolved("output");
        assertThat(result.value()).isEqualTo("output");
    }

    @Test
    void partialCarriesCollectedAndRemaining() {
        var result = new AggregationResult.Partial("partial", 2);
        assertThat(result.collected()).isEqualTo("partial");
        assertThat(result.remaining()).isEqualTo(2);
    }

    @Test
    void deadlockedCarriesReason() {
        var result = new AggregationResult.Deadlocked("tied vote");
        assertThat(result.reason()).isEqualTo("tied vote");
    }

    @Test
    void sealedPermitsThreeVariants() {
        assertThat(AggregationResult.class.getPermittedSubclasses()).hasSize(3);
    }
}
