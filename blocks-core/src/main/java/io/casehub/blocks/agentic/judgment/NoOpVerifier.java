package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;

public final class NoOpVerifier implements JudgmentVerifier {

    @Override
    public String id() { return "noop"; }

    @Override
    public VerificationResult verify(VerificationContext context) {
        return new VerificationResult.Accepted();
    }
}
