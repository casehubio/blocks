package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.social.jpa.EngagementEvidenceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EngagementEvidenceEntityRepository extends JpaRepository<EngagementEvidenceEntity, String> {
    int countByAgentIdAndTenantId(String agentId, String tenantId);
    List<EngagementEvidenceEntity> findByAgentIdAndTenantIdOrderByRecordedAtDesc(String agentId, String tenantId, Pageable pageable);
    List<EngagementEvidenceEntity> findByAgentIdAndSubjectIdAndTenantIdOrderByRecordedAtDesc(String agentId, String subjectId, String tenantId, Pageable pageable);
    void deleteByAgentIdAndTenantId(String agentId, String tenantId);
    void deleteBySubjectIdAndTenantId(String subjectId, String tenantId);
}
