package io.casehub.blocks.summarisation.observation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationResultTest {

    @Test
    void empty_returnsZeroCountNullTier() {
        var result = ObservationResult.empty(5000);
        assertThat(result.renderedText()).isEmpty();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.eventCount()).isZero();
        assertThat(result.timeSinceLastDrain()).isEqualTo(5000);
        assertThat(result.tier()).isNull();
    }

    @Test
    void chunks_areDefensivelyCopied() {
        var chunk = new ObservationChunk("text", 100, ObservationTier.VERBATIM, 1, Map.of());
        var mutableList = new java.util.ArrayList<>(List.of(chunk));
        var result = new ObservationResult("text", mutableList, 1, 500, ObservationTier.VERBATIM);
        mutableList.add(new ObservationChunk("extra", 200, ObservationTier.VERBATIM, 1, Map.of()));
        assertThat(result.chunks()).hasSize(1);
    }

    @Test
    void chunk_metadata_isDefensivelyCopied() {
        var mutableMap = new java.util.HashMap<>(Map.of("key", "value"));
        var chunk = new ObservationChunk("text", 100, ObservationTier.VERBATIM, 1, mutableMap);
        mutableMap.put("extra", "data");
        assertThat(chunk.metadata()).hasSize(1);
    }

    @Test
    void tier_predefinedConstants() {
        assertThat(ObservationTier.VERBATIM.name()).isEqualTo("verbatim");
        assertThat(ObservationTier.VERBATIM.ordinal()).isZero();
        assertThat(ObservationTier.GROUPED.name()).isEqualTo("grouped");
        assertThat(ObservationTier.GROUPED.ordinal()).isEqualTo(1);
        assertThat(ObservationTier.SUMMARISED.name()).isEqualTo("summarised");
        assertThat(ObservationTier.SUMMARISED.ordinal()).isEqualTo(2);
    }
}
