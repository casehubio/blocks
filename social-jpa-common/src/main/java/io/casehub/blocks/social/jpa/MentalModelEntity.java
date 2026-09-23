package io.casehub.blocks.social.jpa;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.social.jpa.converter.AttributedStateListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "social_mental_model",
        uniqueConstraints = @UniqueConstraint(name = "social_mental_model_natural_key_uq",
                columnNames = {"agent_id", "subject_id", "tenant_id"}))
public class MentalModelEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    public String id;

    @Column(name = "agent_id", nullable = false)
    public String agentId;

    @Column(name = "subject_id", nullable = false)
    public String subjectId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Convert(converter = AttributedStateListConverter.class)
    @Column(name = "beliefs", nullable = false, columnDefinition = "TEXT")
    public List<AttributedState> beliefs;

    @Convert(converter = AttributedStateListConverter.class)
    @Column(name = "desires", nullable = false, columnDefinition = "TEXT")
    public List<AttributedState> desires;

    @Convert(converter = AttributedStateListConverter.class)
    @Column(name = "intentions", nullable = false, columnDefinition = "TEXT")
    public List<AttributedState> intentions;

    @Column(name = "last_signal", nullable = false)
    public Instant lastSignal;

    @Column(name = "last_inference")
    public Instant lastInference;

    @Column(name = "snapshot_created", nullable = false)
    public Instant snapshotCreated;
}
