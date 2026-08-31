package io.casehub.blocks.agentic.judgment;

public enum VerifierFailurePolicy {
    RETRY_WITH_FEEDBACK,
    ESCALATE,
    FAIL
}
