package io.casehub.blocks.agentic.social;

import java.util.Set;

@FunctionalInterface
public interface SubjectResolver {
    Set<String> relevantSubjects(String agentId, String tenantId);
}
