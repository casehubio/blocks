package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceOnPatternSpecTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void pattern_without_governance_deserialises_with_null() throws Exception {
        var yaml = """
                type: supervisor
                agents:
                  - type: external
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.governance()).isNull();
    }

    @Test
    void pattern_with_oversight_deserialises() throws Exception {
        var yaml = """
                type: supervisor
                agents:
                  - type: external
                    name: agent-1
                governance:
                  oversight:
                    classifier: com.example.MyClassifier
                    reversible: false
                    candidateGroups:
                      - mlro
                      - compliance
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.governance().oversight()).isNotNull();
        assertThat(spec.governance().oversight().classifier()).isEqualTo("com.example.MyClassifier");
        assertThat(spec.governance().oversight().reversible()).isFalse();
        assertThat(spec.governance().oversight().candidateGroups()).containsExactly("mlro", "compliance");
    }

    @Test
    void pattern_with_trust_routing_deserialises() throws Exception {
        var yaml = """
                type: parallel
                agents:
                  - type: external
                    name: agent-1
                governance:
                  trustRouting:
                    threshold: 0.8
                    minimumObservations: 20
                    borderlineMargin: 0.15
                    blendFactor: 0.5
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.governance().trustRouting()).isNotNull();
        assertThat(spec.governance().trustRouting().threshold()).isEqualTo(0.8);
        assertThat(spec.governance().trustRouting().minimumObservations()).isEqualTo(20);
    }

    @Test
    void pattern_with_cbr_routing_deserialises() throws Exception {
        var yaml = """
                type: voting
                agents:
                  - type: external
                    name: agent-1
                governance:
                  cbrRouting:
                    successWeight: 1.0
                    failureWeight: 0.0
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.governance().cbrRouting()).isNotNull();
        assertThat(spec.governance().cbrRouting().successWeight()).isEqualTo(1.0);
        assertThat(spec.governance().cbrRouting().failureWeight()).isEqualTo(0.0);
    }

    @Test
    void pattern_with_attestation_deserialises() throws Exception {
        var yaml = """
                type: sequence
                agents:
                  - type: external
                    name: agent-1
                governance:
                  attestation:
                    observer: com.example.MyObserver
                    capabilityTag: investigation
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.governance().attestation()).isNotNull();
        assertThat(spec.governance().attestation().observer()).isEqualTo("com.example.MyObserver");
        assertThat(spec.governance().attestation().capabilityTag()).isEqualTo("investigation");
    }

    @Test
    void pattern_with_full_governance_deserialises() throws Exception {
        var yaml = """
                type: debate
                maxRounds: 5
                agents:
                  - type: external
                    name: agent-1
                governance:
                  oversight:
                    classifier: com.example.Classifier
                  trustRouting:
                    threshold: 0.9
                  cbrRouting:
                    successWeight: 1.0
                  attestation:
                    observer: com.example.Observer
                    capabilityTag: sar
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var gov = spec.governance();
        assertThat(gov).isNotNull();
        assertThat(gov.oversight()).isNotNull();
        assertThat(gov.trustRouting()).isNotNull();
        assertThat(gov.cbrRouting()).isNotNull();
        assertThat(gov.attestation()).isNotNull();
    }

    @Test
    void governance_with_only_attestation_leaves_others_null() throws Exception {
        var yaml = """
                type: loop
                maxIterations: 10
                agents:
                  - type: external
                    name: agent-1
                governance:
                  attestation:
                    observer: com.example.Observer
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var gov = spec.governance();
        assertThat(gov).isNotNull();
        assertThat(gov.oversight()).isNull();
        assertThat(gov.trustRouting()).isNull();
        assertThat(gov.cbrRouting()).isNull();
        assertThat(gov.attestation()).isNotNull();
    }

    @Test
    void attestation_rejects_null_observer() {
        assertThatThrownBy(() -> new GovernanceSpec.AttestationSpec(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void oversight_rejects_null_classifier() {
        assertThatThrownBy(() -> new GovernanceSpec.OversightSpec(null, true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
