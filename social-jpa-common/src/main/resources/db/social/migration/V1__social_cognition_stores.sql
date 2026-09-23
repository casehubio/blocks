CREATE TABLE social_user_profile (
    id                  VARCHAR(36)              NOT NULL,
    agent_id            VARCHAR(255)             NOT NULL,
    subject_id          VARCHAR(255)             NOT NULL,
    tenant_id           VARCHAR(255)             NOT NULL,
    relationship_stage  VARCHAR(50)              NOT NULL,
    familiarity_score   DOUBLE PRECISION         NOT NULL,
    total_interactions  INTEGER                  NOT NULL,
    positive_signals    INTEGER                  NOT NULL,
    negative_signals    INTEGER                  NOT NULL,
    neutral_signals     INTEGER                  NOT NULL,
    last_interaction    TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_created     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_synthesised    TIMESTAMP WITH TIME ZONE,
    communication_style TEXT,
    topics_of_interest  TEXT,
    preferences         TEXT,
    synthesis_notes     TEXT,
    metadata            TEXT                     NOT NULL DEFAULT '{}',
    CONSTRAINT social_user_profile_pk PRIMARY KEY (id),
    CONSTRAINT social_user_profile_natural_key_uq UNIQUE (agent_id, subject_id, tenant_id)
);
CREATE INDEX social_user_profile_agent_tenant_idx ON social_user_profile (agent_id, tenant_id);

CREATE TABLE social_mental_model (
    id                VARCHAR(36)              NOT NULL,
    agent_id          VARCHAR(255)             NOT NULL,
    subject_id        VARCHAR(255)             NOT NULL,
    tenant_id         VARCHAR(255)             NOT NULL,
    beliefs           TEXT                     NOT NULL,
    desires           TEXT                     NOT NULL,
    intentions        TEXT                     NOT NULL,
    last_signal       TIMESTAMP WITH TIME ZONE NOT NULL,
    last_inference    TIMESTAMP WITH TIME ZONE,
    snapshot_created  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT social_mental_model_pk PRIMARY KEY (id),
    CONSTRAINT social_mental_model_natural_key_uq UNIQUE (agent_id, subject_id, tenant_id)
);
CREATE INDEX social_mental_model_agent_tenant_idx ON social_mental_model (agent_id, tenant_id);

CREATE TABLE social_narrative (
    id                               VARCHAR(36)              NOT NULL,
    scope_id                         VARCHAR(255)             NOT NULL,
    tenant_id                        VARCHAR(255)             NOT NULL,
    scope                            VARCHAR(20)              NOT NULL,
    fragments                        TEXT                     NOT NULL,
    synthesised_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    reflection_count_at_synthesis    INTEGER                  NOT NULL,
    CONSTRAINT social_narrative_pk PRIMARY KEY (id),
    CONSTRAINT social_narrative_natural_key_uq UNIQUE (scope_id, tenant_id)
);

CREATE TABLE social_strategy_profile (
    id              VARCHAR(36)              NOT NULL,
    agent_id        VARCHAR(255)             NOT NULL,
    tenant_id       VARCHAR(255)             NOT NULL,
    dimensions      TEXT                     NOT NULL,
    guidelines      TEXT                     NOT NULL,
    last_reflection TIMESTAMP WITH TIME ZONE NOT NULL,
    evidence_count  INTEGER                  NOT NULL,
    CONSTRAINT social_strategy_profile_pk PRIMARY KEY (id),
    CONSTRAINT social_strategy_profile_natural_key_uq UNIQUE (agent_id, tenant_id)
);

CREATE TABLE social_engagement_evidence (
    id                    VARCHAR(36)              NOT NULL,
    agent_id              VARCHAR(255)             NOT NULL,
    subject_id            VARCHAR(255)             NOT NULL,
    tenant_id             VARCHAR(255)             NOT NULL,
    conversation_id       VARCHAR(255),
    conversation_summary  TEXT,
    turn_count            INTEGER                  NOT NULL,
    continuation_rate     DOUBLE PRECISION         NOT NULL,
    avg_response_length   DOUBLE PRECISION         NOT NULL,
    mean_affect_shift     DOUBLE PRECISION         NOT NULL,
    dimension_snapshots   TEXT                     NOT NULL DEFAULT '{}',
    recorded_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT social_engagement_evidence_pk PRIMARY KEY (id)
);
CREATE INDEX social_engagement_agent_tenant_idx ON social_engagement_evidence (agent_id, tenant_id);
CREATE INDEX social_engagement_subject_tenant_idx ON social_engagement_evidence (subject_id, tenant_id);
