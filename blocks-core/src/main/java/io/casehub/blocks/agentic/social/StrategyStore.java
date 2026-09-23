package io.casehub.blocks.agentic.social;

import java.util.List;
import java.util.Optional;

public interface StrategyStore {

    void store(StrategyProfile profile);

    Optional<StrategyProfile> lookup(String agentId, String tenantId);

    List<String> subjectInsights(String agentId, String subjectId, String tenantId);

    void storeEvidence(EngagementEvidence evidence);

    int evidenceCount(String agentId, String tenantId);

    List<EngagementEvidence> recentEvidence(String agentId, String tenantId, int limit);


    void eraseAgent(String agentId, String tenantId);

    void eraseSubject(String subjectId, String tenantId);
}
