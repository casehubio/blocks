package io.casehub.blocks.memory;

import java.util.List;

public class NoOpSemanticIntegrityChecker implements SemanticIntegrityChecker {
    @Override
    public List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                                    String agentId, String tenantId) {
        return List.of();
    }
}
