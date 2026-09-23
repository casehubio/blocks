package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.social.jpa.MentalModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentalModelEntityRepository extends JpaRepository<MentalModelEntity, String> {
    Optional<MentalModelEntity> findByAgentIdAndSubjectIdAndTenantId(String agentId, String subjectId, String tenantId);
    List<MentalModelEntity> findByAgentIdAndTenantId(String agentId, String tenantId);
    void deleteBySubjectIdAndTenantId(String subjectId, String tenantId);
}
