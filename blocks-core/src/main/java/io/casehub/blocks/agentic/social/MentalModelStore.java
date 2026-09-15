package io.casehub.blocks.agentic.social;

import java.util.List;
import java.util.Optional;

public interface MentalModelStore {
    void store(MentalModelSnapshot snapshot);

    Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId);

    List<MentalModelSnapshot> findByAgent(String agentId, String tenantId);

    void eraseSubject(String subjectId, String tenantId);
}
