package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void agentParticipantSpec() throws Exception {
        var yaml = """
                agent:
                  type: worker
                  name: analyst
                role: analyst
                systemPrompt: You are a financial analyst.
                """;
        var spec = mapper.readValue(yaml, AgentParticipantSpec.class);
        assertThat(spec.agent()).isNotNull();
        assertThat(spec.role()).isEqualTo("analyst");
        assertThat(spec.systemPrompt()).isEqualTo("You are a financial analyst.");
    }

    @Test
    void channelBindingSpecWithId() throws Exception {
        var yaml = """
                channelId: "550e8400-e29b-41d4-a716-446655440000"
                semantic: DELIBERATION
                """;
        var spec = mapper.readValue(yaml, ChannelBindingSpec.class);
        assertThat(spec.channelId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(spec.semantic()).isEqualTo("DELIBERATION");
    }

    @Test
    void channelBindingSpecWithoutId() throws Exception {
        var yaml = "semantic: NEGOTIATION";
        var spec = mapper.readValue(yaml, ChannelBindingSpec.class);
        assertThat(spec.channelId()).isNull();
        assertThat(spec.semantic()).isEqualTo("NEGOTIATION");
    }

    @Test
    void conversationProtocolSpec() throws Exception {
        var yaml = """
                sentinel: "[CONV]"
                entryTypes:
                  - PROPOSAL
                  - COUNTER
                """;
        var spec = mapper.readValue(yaml, ConversationProtocolSpec.class);
        assertThat(spec.sentinel()).isEqualTo("[CONV]");
        assertThat(spec.entryTypes()).containsExactlyInAnyOrder("PROPOSAL", "COUNTER");
    }

    @Test
    void progressRendererSpec() throws Exception {
        var yaml = "shape: percentage";
        var spec = mapper.readValue(yaml, ProgressRendererSpec.class);
        assertThat(spec.shape()).isEqualTo("percentage");
    }

    @Test
    void conversationStrategySpec() throws Exception {
        var yaml = """
                type: conversation
                turnPolicy:
                  type: round-robin
                termination:
                  - type: max-iterations
                    iterations: 10
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: You are Alice.
                  - agent:
                      type: worker
                      name: bob
                    role: reviewer
                    systemPrompt: You are Bob.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        assertThat(spec).isInstanceOf(ChannelExecutionStrategySpec.ConversationStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        assertThat(conv.participants()).hasSize(2);
        assertThat(conv.turnPolicy()).isNotNull();
        assertThat(conv.termination()).hasSize(1);
    }

    @Test
    void conversationStrategySpecMinimal() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: solo
                    role: agent
                    systemPrompt: You are an agent.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        assertThat(conv.turnPolicy()).isNull();
        assertThat(conv.termination()).isNull();
        assertThat(conv.epistemicRule()).isNull();
        assertThat(conv.convergencePolicy()).isNull();
    }

    @Test
    void fanInStrategySpec() throws Exception {
        var yaml = """
                   type: fan-in
                   executionTimeout: PT30S
                   """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        assertThat(spec).isInstanceOf(ChannelExecutionStrategySpec.FanInStrategySpec.class);
        var fanIn = (ChannelExecutionStrategySpec.FanInStrategySpec) spec;
        assertThat(fanIn.executionTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void fanInStrategySpecWithoutTimeout() throws Exception {
        var yaml = "type: fan-in";
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        assertThat(spec).isInstanceOf(ChannelExecutionStrategySpec.FanInStrategySpec.class);
        var fanIn = (ChannelExecutionStrategySpec.FanInStrategySpec) spec;
        assertThat(fanIn.executionTimeout()).isNull();
    }

    @Test
    void barrierStrategySpec() throws Exception {
        var yaml = """
                   type: barrier
                   executionTimeout: PT1M
                   """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        assertThat(spec).isInstanceOf(ChannelExecutionStrategySpec.BarrierStrategySpec.class);
        var barrier = (ChannelExecutionStrategySpec.BarrierStrategySpec) spec;
        assertThat(barrier.executionTimeout()).isEqualTo(java.time.Duration.ofMinutes(1));
    }


}
