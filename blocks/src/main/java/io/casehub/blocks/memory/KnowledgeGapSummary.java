package io.casehub.blocks.memory;

public record KnowledgeGapSummary(
        int lowRetentionCount,
        int consolidationGroups,
        int totalScored) {
    public KnowledgeGapSummary {
        if (lowRetentionCount < 0) throw new IllegalArgumentException("lowRetentionCount must be >= 0");
        if (consolidationGroups < 0) throw new IllegalArgumentException("consolidationGroups must be >= 0");
        if (totalScored < 0) throw new IllegalArgumentException("totalScored must be >= 0");
    }

    public static KnowledgeGapSummary empty() {
        return new KnowledgeGapSummary(0, 0, 0);
    }
}
