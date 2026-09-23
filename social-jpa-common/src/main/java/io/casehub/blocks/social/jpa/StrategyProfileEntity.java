package io.casehub.blocks.social.jpa;

import io.casehub.blocks.social.jpa.converter.StringDoubleMapConverter;
import io.casehub.blocks.social.jpa.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "social_strategy_profile",
        uniqueConstraints = @UniqueConstraint(name = "social_strategy_profile_natural_key_uq",
                columnNames = {"agent_id", "tenant_id"}))
public class StrategyProfileEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    public String id;

    @Column(name = "agent_id", nullable = false)
    public String agentId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Convert(converter = StringDoubleMapConverter.class)
    @Column(name = "dimensions", nullable = false, columnDefinition = "TEXT")
    public Map<String, Double> dimensions;

    @Convert(converter = StringListConverter.class)
    @Column(name = "guidelines", nullable = false, columnDefinition = "TEXT")
    public List<String> guidelines;

    @Column(name = "last_reflection", nullable = false)
    public Instant lastReflection;

    @Column(name = "evidence_count", nullable = false)
    public int evidenceCount;
}
