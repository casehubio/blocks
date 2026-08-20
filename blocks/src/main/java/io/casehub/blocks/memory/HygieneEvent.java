package io.casehub.blocks.memory;

import java.util.List;

public sealed interface HygieneEvent {
    record MemoryEvicted(String caseId, RetentionScore score) implements HygieneEvent {}
    record MemoryConsolidated(String mergedCaseId,
                               List<String> sourceCaseIds) implements HygieneEvent {
        public MemoryConsolidated { sourceCaseIds = List.copyOf(sourceCaseIds); }
    }
    record ReflectionGenerated(String agentId, String insight) implements HygieneEvent {}
    record IntegrityViolationDetected(IntegrityViolation violation) implements HygieneEvent {}
}
