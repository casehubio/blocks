package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StrategySpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class RoutingSpecs {
        @Test
        void firstMatchWithGuard() throws Exception {
            var yaml = """
                    type: first-match
                    guard: "priority > 5"
                    """;
            var spec = mapper.readValue(yaml, RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.FirstMatch.class);
            assertThat(((RoutingSpec.FirstMatch) spec).guard()).isEqualTo("priority > 5");
        }

        @Test
        void roundRobin() throws Exception {
            var spec = mapper.readValue("type: round-robin", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.RoundRobin.class);
        }

        @Test
        void sequential() throws Exception {
            var spec = mapper.readValue("type: sequential", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.Sequential.class);
        }

        @Test
        void llmSelected() throws Exception {
            var spec = mapper.readValue("type: llm-selected", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.LlmSelected.class);
        }

        @Test
        void selectAll() throws Exception {
            var spec = mapper.readValue("type: select-all", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.SelectAll.class);
        }
    }

    @Nested
    class TerminationSpecs {
        @Test
        void maxIterations() throws Exception {
            var yaml = """
                    type: max-iterations
                    iterations: 20
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.MaxIterations.class);
            assertThat(((TerminationSpec.MaxIterations) spec).iterations()).isEqualTo(20);
        }

        @Test
        void goalReached() throws Exception {
            var yaml = """
                    type: goal-reached
                    when: "qualityScore > 0.9"
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.GoalReached.class);
            assertThat(((TerminationSpec.GoalReached) spec).when()).isEqualTo("qualityScore > 0.9");
        }

        @Test
        void singlePass() throws Exception {
            var spec = mapper.readValue("type: single-pass", TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.SinglePass.class);
        }

        @Test
        void agentCount() throws Exception {
            var spec = mapper.readValue("type: agent-count", TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.AgentCount.class);
        }

        @Test
        void allAgreed() throws Exception {
            var yaml = """
                    type: all-agreed
                    resolvedStatuses:
                      - RESOLVED
                      - ACCEPTED
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.AllAgreed.class);
            assertThat(((TerminationSpec.AllAgreed) spec).resolvedStatuses())
                    .containsExactlyInAnyOrder("RESOLVED", "ACCEPTED");
        }

        @Test
        void supervisor() throws Exception {
            var yaml = """
                    type: supervisor
                    role: moderator
                    signalType: END_DISCUSSION
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.Supervisor.class);
            var sv = (TerminationSpec.Supervisor) spec;
            assertThat(sv.role()).isEqualTo("moderator");
            assertThat(sv.signalType()).isEqualTo("END_DISCUSSION");
        }

        @Test
        void contested() throws Exception {
            var yaml = """
                    type: contested
                    maxDisputeRounds: 3
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.Contested.class);
            assertThat(((TerminationSpec.Contested) spec).maxDisputeRounds()).isEqualTo(3);
        }

        @Test
        void convergence() throws Exception {
            var yaml = """
                    type: convergence
                    threshold: 0.8
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.Convergence.class);
            assertThat(((TerminationSpec.Convergence) spec).threshold()).isEqualTo(0.8);
        }
    }

    @Nested
    class AggregationSpecs {
        @Test
        void passThrough() throws Exception {
            var spec = mapper.readValue("type: pass-through", AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.PassThrough.class);
        }

        @Test
        void collectAll() throws Exception {
            var spec = mapper.readValue("type: collect-all", AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.CollectAll.class);
        }

        @Test
        void majorityVote() throws Exception {
            var spec = mapper.readValue("type: majority-vote", AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.MajorityVote.class);
        }

        @Test
        void auction() throws Exception {
            var yaml = """
                    type: auction
                    auctionType: ENGLISH
                    """;
            var spec = mapper.readValue(yaml, AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.Auction.class);
            assertThat(((AggregationSpec.Auction) spec).auctionType()).isEqualTo("ENGLISH");
        }
    }

    @Nested
    class ActivationSpecs {
        @Test
        void onDispatch() throws Exception {
            var spec = mapper.readValue("type: on-dispatch", ActivationSpec.class);
            assertThat(spec).isInstanceOf(ActivationSpec.OnDispatch.class);
        }

        @Test
        void maxIterationsGuard() throws Exception {
            var yaml = """
                    type: max-iterations
                    maxIterations: 5
                    """;
            var spec = mapper.readValue(yaml, ActivationSpec.class);
            assertThat(spec).isInstanceOf(ActivationSpec.MaxIterationsGuard.class);
            assertThat(((ActivationSpec.MaxIterationsGuard) spec).maxIterations()).isEqualTo(5);
        }
    }

    @Nested
    class DecompositionSpecs {
        @Test
        void identity() throws Exception {
            var spec = mapper.readValue("type: identity", DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Identity.class);
        }

        @Test
        void llmWithMaxDepth() throws Exception {
            var yaml = """
                    type: llm
                    maxDepth: 3
                    """;
            var spec = mapper.readValue(yaml, DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Llm.class);
            assertThat(((DecompositionSpec.Llm) spec).maxDepth()).isEqualTo(3);
        }

        @Test
        void goap() throws Exception {
            var spec = mapper.readValue("type: goap", DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Goap.class);
        }

        @Test
        void capabilityDependency() throws Exception {
            var spec = mapper.readValue("type: capability-dependency", DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.CapabilityDependency.class);
        }

        @Test
        void hybrid() throws Exception {
            var yaml = """
                    type: hybrid
                    maxDepth: 2
                    """;
            var spec = mapper.readValue(yaml, DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Hybrid.class);
            assertThat(((DecompositionSpec.Hybrid) spec).maxDepth()).isEqualTo(2);
        }

        @Test
        void forwardReasoning() throws Exception {
            var spec = mapper.readValue("type: forward-reasoning", DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.ForwardReasoning.class);
        }
    }

    @Nested
    class AgentRefSpecs {
        @Test
        void workerAgent() throws Exception {
            var yaml = """
                    type: worker
                    name: analyst
                    description: "Analyses input data"
                    capabilities:
                      - data-analysis
                      - pattern-recognition
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.Worker.class);
            assertThat(spec.name()).isEqualTo("analyst");
            assertThat(spec.description()).isEqualTo("Analyses input data");
            assertThat(spec.capabilities()).containsExactly("data-analysis", "pattern-recognition");
        }

        @Test
        void externalAgent() throws Exception {
            var yaml = """
                    type: external
                    name: ext-service
                    description: "External API"
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.External.class);
            assertThat(spec.name()).isEqualTo("ext-service");
        }

        @Test
        void humanAgent() throws Exception {
            var yaml = """
                    type: human
                    name: reviewer
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.Human.class);
            assertThat(spec.name()).isEqualTo("reviewer");
        }

        @Test
        void channelAgent() throws Exception {
            var yaml = """
                    type: channel
                    name: data-channel
                    channelId: ch-123
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.Channel.class);
            assertThat(((AgentRefSpec.Channel) spec).channelId()).isEqualTo("ch-123");
        }
    }

    @Nested
    class NegotiationTerminationSpecs {
        @Test
        void acceptedTermination() throws Exception {
            var yaml = "type: accepted";
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.Accepted.class);
        }

        @Test
        void terminalOutcome() throws Exception {
            var yaml = "type: terminal-outcome";
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.TerminalOutcome.class);
        }

        @Test
        void deadlineWithTimeout() throws Exception {
            var yaml = """
                    type: deadline
                    timeout: PT30M
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.Deadline.class);
            assertThat(((TerminationSpec.Deadline) spec).timeout())
                    .isEqualTo(Duration.ofMinutes(30));
        }
    }
}
