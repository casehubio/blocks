package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.ConvergencePolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.EpistemicRuleRegistry;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.registry.TurnPolicyRegistry;
import io.casehub.blocks.agentic.yaml.spec.ChannelExecutionStrategySpec;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationCompilerTest {

    private ObjectMapper mapper;
    private ConversationCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
        compiler = new ConversationCompiler(
                new TurnPolicyRegistry(),
                new TerminationConditionRegistry(),
                new EpistemicRuleRegistry(),
                new ConvergencePolicyRegistry(),
                null);
    }

    @Test
    void compilesMinimalConversation() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: You are Alice.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.participants()).hasSize(1);
        assertThat(compiled.participants().get(0).role()).isEqualTo("analyst");
        assertThat(compiled.participants().get(0).agentId()).isEqualTo("alice");
        assertThat(compiled.turnPolicy()).isInstanceOf(RoundRobinTurnPolicy.class);
        assertThat(compiled.termination()).isNull();
        assertThat(compiled.epistemicRule()).isNull();
        assertThat(compiled.convergencePolicy()).isNull();
    }

    @Test
    void compilesWithTurnPolicyAndTermination() throws Exception {
        var yaml = """
                type: conversation
                turnPolicy:
                  type: round-robin
                termination:
                  - type: max-iterations
                    iterations: 5
                participants:
                  - agent:
                      type: worker
                      name: a
                    role: r
                    systemPrompt: p
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.turnPolicy()).isInstanceOf(RoundRobinTurnPolicy.class);
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void compilesWithEpistemicAndConvergence() throws Exception {
        var yaml = """
                type: conversation
                epistemicRule:
                  type: explicit-acknowledgement
                  minParticipants: 2
                convergencePolicy:
                  type: structural
                  similarityThreshold: 0.8
                  staleRounds: 3
                participants:
                  - agent:
                      type: worker
                      name: a
                    role: r
                    systemPrompt: p
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.epistemicRule()).isNotNull();
        assertThat(compiled.convergencePolicy()).isNotNull();
    }

    @Test
    void compilesMultipleParticipants() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: Analyst prompt
                  - agent:
                      type: worker
                      name: bob
                    role: reviewer
                    systemPrompt: Reviewer prompt
                  - agent:
                      type: human
                      name: carol
                    role: supervisor
                    systemPrompt: Supervisor prompt
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.participants()).hasSize(3);
        assertThat(compiled.participants().get(2).agentId()).isEqualTo("carol");
    }
}
