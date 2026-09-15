package io.casehub.blocks.summarisation.narrative;

import java.util.Map;
import java.util.Objects;

public record SignalDigest(
        String signalType, String summary,
        Map<String, String> keyFacts, double confidence
) {
    public SignalDigest {
        Objects.requireNonNull(signalType);
        Objects.requireNonNull(summary);
        keyFacts = Map.copyOf(keyFacts);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
    }
}
