package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultIntegrityChecker implements IntegrityChecker {

    private static final Logger LOG = Logger.getLogger(DefaultIntegrityChecker.class.getName());

    private final CbrCaseMemoryStore store;
    private final SemanticIntegrityChecker semanticChecker;
    private final List<String> caseTypes;

    public DefaultIntegrityChecker(CbrCaseMemoryStore store,
                                    SemanticIntegrityChecker semanticChecker,
                                    List<String> caseTypes) {
        this.store = store;
        this.semanticChecker = semanticChecker;
        this.caseTypes = List.copyOf(caseTypes);
    }

    @Override
    public List<IntegrityViolation> check(String agentId, String tenantId, MemoryDomain domain) {
        var violations = new ArrayList<IntegrityViolation>();

        checkOrphanedSupersessions(tenantId, domain, violations);

        var flagged = violations.stream()
                .filter(IntegrityViolation::escalateToSemantic)
                .toList();
        if (!flagged.isEmpty()) {
            var semanticResults = semanticChecker.checkSemantic(flagged, agentId, tenantId);
            violations.addAll(semanticResults);
        }

        return List.copyOf(violations);
    }

    private void checkOrphanedSupersessions(String tenantId, MemoryDomain domain,
                                             List<IntegrityViolation> violations) {
        try {
            var superseded = store.findSupersededCases(tenantId, domain);
            for (var status : superseded) {
                if (status.supersedingCaseId() == null) {continue;}
                try {
                    var target = store.getSupersessionStatus(
                            status.supersedingCaseId(), tenantId);
                    if (target == SupersessionStatus.NOT_SUPERSEDED
                            && status.supersedingCaseId() != null) {
                        violations.add(new IntegrityViolation(
                                status.caseId(),
                                ViolationType.ORPHANED_SUPERSESSION,
                                "superseding case " + status.supersedingCaseId() + " not found",
                                true));
                    }
                } catch (Exception e) {
                    violations.add(new IntegrityViolation(
                            status.caseId(),
                            ViolationType.ORPHANED_SUPERSESSION,
                            "failed to verify superseding case: " + e.getMessage(),
                            true));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check supersessions", e);
        }
    }
}
