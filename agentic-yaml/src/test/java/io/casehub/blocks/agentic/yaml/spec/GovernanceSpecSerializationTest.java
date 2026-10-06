package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.blocks.routing.agent.DispositionProfile;
import io.casehub.eidos.api.DispositionAxis;
import io.casehub.blocks.agentic.yaml.registry.CandidateSetStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.CbrOutcomeWeightsRegistry;
import io.casehub.blocks.agentic.yaml.registry.CoordinationOutcomeWeightsRegistry;
import io.casehub.blocks.agentic.yaml.registry.RiskDecisionRegistry;
import io.casehub.blocks.agentic.yaml.registry.TrustRoutingPolicyKeysRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class CbrOutcomeWeightsSpecs {

        @Test
        void deserialises_weight_map() throws Exception {
            var yaml = """
                    weights:
                      SUCCESS: 1.0
                      GATE_EXPIRED: 0.5
                      FAILURE: 0.0
                    """;
            var spec = mapper.readValue(yaml, CbrOutcomeWeightsSpec.class);
            assertThat(spec.weights()).hasSize(3);
            assertThat(spec.weights().get(RoutingOutcome.SUCCESS)).isEqualTo(1.0);
            assertThat(spec.weights().get(RoutingOutcome.GATE_EXPIRED)).isEqualTo(0.5);
            assertThat(spec.weights().get(RoutingOutcome.FAILURE)).isEqualTo(0.0);
        }

        @Test
        void rejects_null_weights() {
            assertThatThrownBy(() -> new CbrOutcomeWeightsSpec(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registry_resolves_to_spi() {
            var spec = new CbrOutcomeWeightsSpec(Map.of(
                    RoutingOutcome.SUCCESS, 1.0,
                    RoutingOutcome.FAILURE, 0.0));
            var registry = new CbrOutcomeWeightsRegistry();
            var result = registry.resolve(spec);
            assertThat(result.weights()).containsEntry(RoutingOutcome.SUCCESS, 1.0);
            assertThat(result.weights()).containsEntry(RoutingOutcome.FAILURE, 0.0);
        }
    }

    @Nested
    class CoordinationOutcomeWeightsSpecs {

        @Test
        void deserialises_string_key_weight_map() throws Exception {
            var yaml = """
                    weights:
                      COMPLETED: 1.0
                      FAULTED: 0.2
                      CANCELLED: 0.0
                    """;
            var spec = mapper.readValue(yaml, CoordinationOutcomeWeightsSpec.class);
            assertThat(spec.weights()).hasSize(3);
            assertThat(spec.weights().get("COMPLETED")).isEqualTo(1.0);
            assertThat(spec.weights().get("FAULTED")).isEqualTo(0.2);
        }

        @Test
        void rejects_null_weights() {
            assertThatThrownBy(() -> new CoordinationOutcomeWeightsSpec(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registry_resolves_to_spi() {
            var spec = new CoordinationOutcomeWeightsSpec(Map.of(
                    "COMPLETED", 1.0,
                    "CANCELLED", 0.0));
            var registry = new CoordinationOutcomeWeightsRegistry();
            var result = registry.resolve(spec);
            assertThat(result.weights()).containsEntry("COMPLETED", 1.0);
            assertThat(result.weights()).containsEntry("CANCELLED", 0.0);
        }
    }

    @Nested
    class TrustRoutingPolicyKeysSpecs {

        @Test
        void deserialises_with_floors() throws Exception {
            var yaml = """
                    scopePrefix: aml
                    floors:
                      investigation-accuracy: investigation-accuracy-floor
                      sar-quality: sar-quality-floor
                    """;
            var spec = mapper.readValue(yaml, TrustRoutingPolicyKeysSpec.class);
            assertThat(spec.scopePrefix()).isEqualTo("aml");
            assertThat(spec.floors()).hasSize(2);
            assertThat(spec.floors()).containsEntry("investigation-accuracy",
                    "investigation-accuracy-floor");
        }

        @Test
        void deserialises_without_floors() throws Exception {
            var yaml = """
                    scopePrefix: clinical
                    """;
            var spec = mapper.readValue(yaml, TrustRoutingPolicyKeysSpec.class);
            assertThat(spec.scopePrefix()).isEqualTo("clinical");
            assertThat(spec.floors()).isNull();
        }

        @Test
        void rejects_null_scope_prefix() {
            assertThatThrownBy(() -> new TrustRoutingPolicyKeysSpec(null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registry_resolves_with_scope() {
            var spec = new TrustRoutingPolicyKeysSpec("aml", null);
            var registry = new TrustRoutingPolicyKeysRegistry();
            var result = registry.resolve(spec);
            assertThat(result).isNotNull();
            assertThat(result.threshold()).isNotNull();
        }

        @Test
        void registry_resolves_with_floors() {
            var spec = new TrustRoutingPolicyKeysSpec("aml",
                    Map.of("accuracy", "accuracy-floor"));
            var registry = new TrustRoutingPolicyKeysRegistry();
            var result = registry.resolve(spec);
            assertThat(result.allFloorKeys()).containsKey("accuracy");
        }
    }

    @Nested
    class RiskDecisionSpecs {

        @Test
        void deserialises_autonomous() throws Exception {
            var yaml = """
                    type: autonomous
                    """;
            var spec = mapper.readValue(yaml, RiskDecisionSpec.class);
            assertThat(spec).isInstanceOf(RiskDecisionSpec.Autonomous.class);
        }

        @Test
        void deserialises_gate_required() throws Exception {
            var yaml = """
                    type: gate-required
                    reason: SAR filing requires human review
                    reversible: false
                    candidateGroups:
                      type: static
                      groups:
                        - mlro
                    expiresIn: PT24H
                    """;
            var spec = mapper.readValue(yaml, RiskDecisionSpec.class);
            assertThat(spec).isInstanceOf(RiskDecisionSpec.GateRequired.class);
            var g = (RiskDecisionSpec.GateRequired) spec;
            assertThat(g.reason()).isEqualTo("SAR filing requires human review");
            assertThat(g.reversible()).isFalse();
            assertThat(g.expiresIn()).isEqualTo(Duration.ofHours(24));
            assertThat(g.scope()).isNull();
            assertThat(g.resolutionType()).isNull();
            assertThat(g.quorum()).isNull();
        }

        @Test
        void rejects_null_reason() {
            assertThatThrownBy(() -> new RiskDecisionSpec.GateRequired(
                    null, false, new CandidateSetStrategySpec.Static(Set.of("g")),
                    Duration.ofHours(1), null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registry_resolves_autonomous() {
            var spec = new RiskDecisionSpec.Autonomous();
            var registry = new RiskDecisionRegistry(new CandidateSetStrategyRegistry());
            var result = registry.resolve(spec);
            assertThat(result).isInstanceOf(RiskDecision.Autonomous.class);
        }

        @Test
        void registry_resolves_gate_required() {
            var spec = new RiskDecisionSpec.GateRequired(
                    "needs review", true,
                    new CandidateSetStrategySpec.Static(Set.of("mlro")),
                    Duration.ofHours(1), "test-scope", null, null);
            var registry = new RiskDecisionRegistry(new CandidateSetStrategyRegistry());
            var result = registry.resolve(spec);
            assertThat(result).isInstanceOf(RiskDecision.GateRequired.class);
            var g = (RiskDecision.GateRequired) result;
            assertThat(g.reason()).isEqualTo("needs review");
            assertThat(g.reversible()).isTrue();
            assertThat(g.scope()).isEqualTo("test-scope");
        }

        @Test
        void registry_resolves_resolution_type() {
            var spec = new RiskDecisionSpec.GateRequired(
                    "typed gate", false,
                    new CandidateSetStrategySpec.Static(Set.of("g")),
                    Duration.ofMinutes(30), null,
                    "java.lang.String", null);
            var registry = new RiskDecisionRegistry(new CandidateSetStrategyRegistry());
            var result = registry.resolve(spec);
            var g = (RiskDecision.GateRequired) result;
            assertThat(g.resolutionType()).isEqualTo(String.class);
        }

        @Test
        void registry_rejects_unknown_class() {
            var spec = new RiskDecisionSpec.GateRequired(
                    "bad type", false,
                    new CandidateSetStrategySpec.Static(Set.of("g")),
                    Duration.ofMinutes(30), null,
                    "com.nonexistent.FakeClass", null);
            var registry = new RiskDecisionRegistry(new CandidateSetStrategyRegistry());
            assertThatThrownBy(() -> registry.resolve(spec))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown resolutionType");
        }
    }

    @Nested
    class DispositionProfileSpecs {

        @Test
        void deserialises_with_desired_and_weights() throws Exception {
            var yaml = """
                    desired:
                      SOCIAL_ORIENTATION: collaborative
                      RISK_APPETITE: conservative
                    weights:
                      SOCIAL_ORIENTATION: 0.8
                      RISK_APPETITE: 0.6
                    """;
            var profile = mapper.readValue(yaml, DispositionProfile.class);
            assertThat(profile.desired()).hasSize(2);
            assertThat(profile.desired().get(DispositionAxis.SOCIAL_ORIENTATION))
                    .isEqualTo("collaborative");
            assertThat(profile.desired().get(DispositionAxis.RISK_APPETITE))
                    .isEqualTo("conservative");
            assertThat(profile.weights().get(DispositionAxis.SOCIAL_ORIENTATION))
                    .isEqualTo(0.8);
            assertThat(profile.weights().get(DispositionAxis.RISK_APPETITE))
                    .isEqualTo(0.6);
        }

        @Test
        void deserialises_without_weights() throws Exception {
            var yaml = """
                    desired:
                      RULE_FOLLOWING: strict
                    """;
            var profile = mapper.readValue(yaml, DispositionProfile.class);
            assertThat(profile.desired()).hasSize(1);
            assertThat(profile.desired().get(DispositionAxis.RULE_FOLLOWING))
                    .isEqualTo("strict");
            assertThat(profile.weights()).isEmpty();
        }
    }
}
