package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.social.jpa.StrategyProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategyProfileEntityRepository extends JpaRepository<StrategyProfileEntity, String> {
    Optional<StrategyProfileEntity> findByAgentIdAndTenantId(String agentId, String tenantId);
    void deleteByAgentIdAndTenantId(String agentId, String tenantId);
}
