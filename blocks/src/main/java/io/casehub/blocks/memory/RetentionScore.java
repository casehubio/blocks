package io.casehub.blocks.memory;

import java.util.Objects;

public record RetentionScore(String caseId, String entityId,
                              double importance, double recencyFactor,
                              double scopeFactor, double trustFactor,
                              double composite) {
    public RetentionScore {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(entityId, "entityId required");
    }

    public static RetentionScore compute(String caseId, String entityId,
                                          double importance, double recencyFactor,
                                          double scopeFactor, double trustFactor,
                                          RetentionConfig config) {
        double num = importance * config.importanceWeight()
                   + recencyFactor * config.recencyWeight()
                   + scopeFactor * config.scopeWeight()
                   + trustFactor * config.trustWeight();
        double den = config.importanceWeight() + config.recencyWeight()
                   + config.scopeWeight() + config.trustWeight();
        double composite = Math.clamp(num / den, 0.0, 1.0);
        return new RetentionScore(caseId, entityId, importance,
                recencyFactor, scopeFactor, trustFactor, composite);
    }
}
