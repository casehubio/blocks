package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.social.jpa.NarrativeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NarrativeEntityRepository extends JpaRepository<NarrativeEntity, String> {
    Optional<NarrativeEntity> findByScopeIdAndTenantId(String scopeId, String tenantId);
}
