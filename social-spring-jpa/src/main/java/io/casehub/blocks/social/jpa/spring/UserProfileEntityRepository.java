package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.social.jpa.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileEntityRepository extends JpaRepository<UserProfileEntity, String> {
    Optional<UserProfileEntity> findByAgentIdAndSubjectIdAndTenantId(String agentId, String subjectId, String tenantId);
    List<UserProfileEntity> findByAgentIdAndTenantId(String agentId, String tenantId);
    void deleteBySubjectIdAndTenantId(String subjectId, String tenantId);
}
