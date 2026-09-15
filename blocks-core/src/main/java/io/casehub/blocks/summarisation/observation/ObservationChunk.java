package io.casehub.blocks.summarisation.observation;

import java.util.Map;

public record ObservationChunk(
        String content,
        long timestamp,
        ObservationTier tier,
        int eventCount,
        Map<String, String> metadata) {
    public ObservationChunk {
        metadata = Map.copyOf(metadata);
    }
}
