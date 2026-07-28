package io.casehub.blocks.agentic.model;

public enum PatternType {
    SEQUENCE,
    PARALLEL,
    LOOP,
    CONDITIONAL,
    SUPERVISOR,
    DEBATE,
    VOTING,
    HTN;

    public boolean isWorkflowShaped() {
        return this == SEQUENCE || this == PARALLEL || this == LOOP || this == CONDITIONAL;
    }
}
