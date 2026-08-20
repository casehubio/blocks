package io.casehub.blocks.memory;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpSemanticIntegrityChecker implements SemanticIntegrityChecker {
    @Override
    public List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                                    String agentId, String tenantId) {
        return List.of();
    }
}
