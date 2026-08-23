package io.casehub.blocks.annotations.runtime;

import io.casehub.blocks.agentic.model.PatternType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorTest {

    @Test
    void patternDescriptor_holds_type_and_attributes() {
        var attrs = Map.<String, Object>of("maxRounds", 5);
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs,
                List.of(), "review");

        assertThat(desc.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(desc.attributes().get("maxRounds")).isEqualTo(5);
        assertThat(desc.beanName()).isEqualTo("review");
        assertThat(desc.participants()).isEmpty();
    }

    @Test
    void patternDescriptor_holds_participants() {
        var participant = new PatternDescriptor.AgentParticipant(
                "critic", "critic", "Challenge claims", "", false);
        var desc = new PatternDescriptor(PatternType.DEBATE,
                Map.of(), List.of(participant), "review");

        assertThat(desc.participants()).hasSize(1);
        assertThat(desc.participants().get(0).label()).isEqualTo("critic");
        assertThat(desc.participants().get(0).systemPrompt()).isEqualTo("Challenge claims");
        assertThat(desc.participants().get(0).isJudge()).isFalse();
    }

    @Test
    void governanceDescriptor_oversightGate() {
        var config = Map.<String, Object>of(
                "classifierClass", "com.example.MyClassifier",
                "reversible", true);
        var desc = new GovernanceDescriptor.OversightGateDescriptor(config);

        assertThat(desc.governanceType()).isEqualTo("OversightGate");
        assertThat(desc.config().get("reversible")).isEqualTo(true);
    }

    @Test
    void governanceDescriptor_trustRouted() {
        var config = Map.<String, Object>of("threshold", 0.8);
        var desc = new GovernanceDescriptor.TrustRoutedDescriptor(config);

        assertThat(desc.governanceType()).isEqualTo("TrustRouted");
        assertThat(desc.config().get("threshold")).isEqualTo(0.8);
    }

    @Test
    void governanceDescriptor_cbrRouted() {
        var config = Map.<String, Object>of("successWeight", 1.0, "failureWeight", 0.0);
        var desc = new GovernanceDescriptor.CbrRoutedDescriptor(config);

        assertThat(desc.governanceType()).isEqualTo("CbrRouted");
    }

    @Test
    void governanceDescriptor_attestation() {
        var config = Map.<String, Object>of(
                "observerClass", "com.example.MyObserver",
                "capabilityTag", "triage");
        var desc = new GovernanceDescriptor.AttestationDescriptor(config);

        assertThat(desc.governanceType()).isEqualTo("Attestation");
        assertThat(desc.config().get("capabilityTag")).isEqualTo("triage");
    }

    @Test
    void governanceDescriptor_sealed_hierarchy_has_4_variants() {
        var permitted = GovernanceDescriptor.class.getPermittedSubclasses();
        assertThat(permitted).hasSize(4);
    }
}
