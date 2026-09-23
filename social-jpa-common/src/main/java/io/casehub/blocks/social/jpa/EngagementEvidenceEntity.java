package io.casehub.blocks.social.jpa;

import io.casehub.blocks.social.jpa.converter.StringDoubleMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "social_engagement_evidence")
public class EngagementEvidenceEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    public String id;

    @Column(name = "agent_id", nullable = false)
    public String agentId;

    @Column(name = "subject_id", nullable = false)
    public String subjectId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "conversation_id")
    public String conversationId;

    @Column(name = "conversation_summary", columnDefinition = "TEXT")
    public String conversationSummary;

    @Column(name = "turn_count", nullable = false)
    public int turnCount;

    @Column(name = "continuation_rate", nullable = false)
    public double continuationRate;

    @Column(name = "avg_response_length", nullable = false)
    public double avgResponseLength;

    @Column(name = "mean_affect_shift", nullable = false)
    public double meanAffectShift;

    @Convert(converter = StringDoubleMapConverter.class)
    @Column(name = "dimension_snapshots", nullable = false, columnDefinition = "TEXT")
    public Map<String, Double> dimensionSnapshots;

    @Column(name = "recorded_at", nullable = false)
    public Instant recordedAt;
}
