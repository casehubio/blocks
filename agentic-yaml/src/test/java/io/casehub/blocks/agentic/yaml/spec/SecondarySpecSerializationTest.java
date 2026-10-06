package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.agentic.yaml.registry.AcceptancePolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.ConflictResolutionRegistry;
import io.casehub.blocks.agentic.yaml.registry.ConvergencePolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.EpistemicRuleRegistry;
import io.casehub.blocks.agentic.yaml.registry.TurnPolicyRegistry;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.blocks.negotiation.MajorityAcceptance;
import io.casehub.blocks.negotiation.UnanimousAcceptance;
import io.casehub.blocks.normative.PriorityResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecondarySpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
    }

    @Nested
    class NegotiationSpecs {
        @Test
        void fullNegotiation() throws Exception {
            var yaml = """
                    parties:
                      - buyer
                      - seller
                    acceptance:
                      type: majority
                    termination:
                      - type: max-iterations
                        iterations: 10
                    """;
            var spec = mapper.readValue(yaml, NegotiationSpec.class);
            assertThat(spec.parties()).containsExactlyInAnyOrder("buyer", "seller");
            assertThat(spec.acceptance()).isInstanceOf(AcceptancePolicySpec.Majority.class);
            assertThat(spec.termination()).hasSize(1);
        }

        @Test
        void acceptancePolicyRegistry() {
            var registry = new AcceptancePolicyRegistry();
            assertThat(registry.resolve(new AcceptancePolicySpec.Unanimous()))
                    .isInstanceOf(UnanimousAcceptance.class);
            assertThat(registry.resolve(new AcceptancePolicySpec.Majority()))
                    .isInstanceOf(MajorityAcceptance.class);
        }
    }

    @Nested
    class ConversationSpecs {
        @Test
        void fullConversation() throws Exception {
            var yaml = """
                    turnPolicy:
                      type: round-robin
                    epistemicRule:
                      type: explicit-acknowledgement
                      minParticipants: 2
                    convergencePolicy:
                      type: structural
                      similarityThreshold: 0.8
                      staleRounds: 3
                    """;
            var spec = mapper.readValue(yaml, ConversationSpec.class);
            assertThat(spec.turnPolicy()).isInstanceOf(TurnPolicySpec.RoundRobin.class);
            assertThat(spec.epistemicRule())
                    .isInstanceOf(EpistemicRuleSpec.ExplicitAcknowledgement.class);
            assertThat(((EpistemicRuleSpec.ExplicitAcknowledgement) spec.epistemicRule())
                    .minParticipants()).isEqualTo(2);
            assertThat(spec.convergencePolicy())
                    .isInstanceOf(ConvergencePolicySpec.Structural.class);
        }

        @Test
        void turnPolicyRegistry() {
            var registry = new TurnPolicyRegistry();
            assertThat(registry.resolve(new TurnPolicySpec.RoundRobin()))
                    .isInstanceOf(RoundRobinTurnPolicy.class);
        }

        @Test
        void epistemicRuleRegistry() {
            var registry = new EpistemicRuleRegistry();
            var rule = registry.resolve(
                    new EpistemicRuleSpec.ExplicitAcknowledgement(2));
            assertThat(rule).isNotNull();
        }

        @Test
        void convergencePolicyRegistry() {
            var registry = new ConvergencePolicyRegistry();
            var policy = registry.resolve(
                    new ConvergencePolicySpec.Structural(0.8, 3));
            assertThat(policy).isNotNull();
        }
    }

    @Nested
    class ConflictResolutionSpecs {
        @Test
        void priority() throws Exception {
            var spec = mapper.readValue("type: priority", ConflictResolutionSpec.class);
            assertThat(spec).isInstanceOf(ConflictResolutionSpec.Priority.class);
        }

        @Test
        void mostRestrictive() throws Exception {
            var spec = mapper.readValue("type: most-restrictive", ConflictResolutionSpec.class);
            assertThat(spec).isInstanceOf(ConflictResolutionSpec.MostRestrictive.class);
        }

        @Test
        void escalation() throws Exception {
            var yaml = """
                    type: escalation
                    escalationDecision: ESCALATE_TO_HUMAN
                    """;
            var spec = mapper.readValue(yaml, ConflictResolutionSpec.class);
            assertThat(spec).isInstanceOf(ConflictResolutionSpec.Escalation.class);
            assertThat(((ConflictResolutionSpec.Escalation) spec).escalationDecision())
                    .isEqualTo("ESCALATE_TO_HUMAN");
        }

        @Test
        void registry() {
            var registry = new ConflictResolutionRegistry();
            assertThat(registry.resolve(new ConflictResolutionSpec.Priority()))
                    .isInstanceOf(PriorityResolution.class);
        }
    }
}
