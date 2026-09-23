package io.casehub.blocks.social.jpa;

import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.social.jpa.converter.NarrativeFragmentListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "social_narrative",
        uniqueConstraints = @UniqueConstraint(name = "social_narrative_natural_key_uq",
                columnNames = {"scope_id", "tenant_id"}))
public class NarrativeEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    public String id;

    @Column(name = "scope_id", nullable = false)
    public String scopeId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    public NarrativeScope scope;

    @Convert(converter = NarrativeFragmentListConverter.class)
    @Column(name = "fragments", nullable = false, columnDefinition = "TEXT")
    public List<NarrativeFragment> fragments;

    @Column(name = "synthesised_at", nullable = false)
    public Instant synthesisedAt;

    @Column(name = "reflection_count_at_synthesis", nullable = false)
    public int reflectionCountAtSynthesis;
}
