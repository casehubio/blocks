package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;

import java.util.List;

public record JudgmentResponse(
        Object decision,
        List<Evidence> evidence,
        CallerIdentity callerIdentity) {

    public JudgmentResponse {
        evidence = List.copyOf(evidence);
    }
}
