package io.casehub.blocks.agentic.intention;

public enum ReconsiderationReason {
    PRECONDITION_VIOLATED,
    PARTY_DROPPED,
    CONTEXT_CHANGED,
    DEADLINE_APPROACHING,
    EXTERNAL_EVENT
}
