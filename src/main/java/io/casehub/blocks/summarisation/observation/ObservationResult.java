package io.casehub.blocks.summarisation.observation;

import java.util.List;

public record ObservationResult(
        String renderedText,
        List<ObservationChunk> chunks,
        int eventCount,
        long timeSinceLastDrain,
        ObservationTier tier) {
    public ObservationResult {
        chunks = List.copyOf(chunks);
    }

    public static ObservationResult empty(long timeSinceLastDrain) {
        return new ObservationResult("", List.of(), 0, timeSinceLastDrain, null);
    }
}
