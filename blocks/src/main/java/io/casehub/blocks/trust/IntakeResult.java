package io.casehub.blocks.trust;

import java.util.Map;
import java.util.Objects;

public record IntakeResult(
        String lane,
        double confidence,
        String reason,
        Map<String, Object> metadata) {

    public IntakeResult {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(reason, "reason");
        if (confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        if (metadata == null) metadata = Map.of();
    }

    public IntakeResult(String lane, double confidence, String reason) {
        this(lane, confidence, reason, Map.of());
    }
}
