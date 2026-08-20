package io.casehub.blocks.agentic.social;

import java.util.List;
import java.util.Optional;

public interface UserProfileStore {

    void store(UserProfile profile);

    Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId);

    List<UserProfile> findByAgent(String agentId, String tenantId);

    void eraseSubject(String subjectId, String tenantId);
}
