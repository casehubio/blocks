package io.casehub.blocks.memory;

import java.util.Objects;

public record IntegrityViolation(String caseId, ViolationType type,
                                  String detail, boolean escalateToSemantic) {
    public IntegrityViolation {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(detail, "detail required");
    }
}
