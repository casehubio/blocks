package io.casehub.blocks.social.jpa;

import io.casehub.blocks.social.jpa.converter.StringStringMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "social_user_profile",
        uniqueConstraints = @UniqueConstraint(name = "social_user_profile_natural_key_uq",
                columnNames = {"agent_id", "subject_id", "tenant_id"}))
public class UserProfileEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    public String id;

    @Column(name = "agent_id", nullable = false)
    public String agentId;

    @Column(name = "subject_id", nullable = false)
    public String subjectId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "relationship_stage", nullable = false, length = 50)
    public String relationshipStage;

    @Column(name = "familiarity_score", nullable = false)
    public double familiarityScore;

    @Column(name = "total_interactions", nullable = false)
    public int totalInteractions;

    @Column(name = "positive_signals", nullable = false)
    public int positiveSignals;

    @Column(name = "negative_signals", nullable = false)
    public int negativeSignals;

    @Column(name = "neutral_signals", nullable = false)
    public int neutralSignals;

    @Column(name = "last_interaction", nullable = false)
    public Instant lastInteraction;

    @Column(name = "profile_created", nullable = false)
    public Instant profileCreated;

    @Column(name = "last_synthesised")
    public Instant lastSynthesised;

    @Column(name = "communication_style", columnDefinition = "TEXT")
    public String communicationStyle;

    @Column(name = "topics_of_interest", columnDefinition = "TEXT")
    public String topicsOfInterest;

    @Column(name = "preferences", columnDefinition = "TEXT")
    public String preferences;

    @Column(name = "synthesis_notes", columnDefinition = "TEXT")
    public String synthesisNotes;

    @Convert(converter = StringStringMapConverter.class)
    @Column(name = "metadata", nullable = false, columnDefinition = "TEXT")
    public Map<String, String> metadata;
}
