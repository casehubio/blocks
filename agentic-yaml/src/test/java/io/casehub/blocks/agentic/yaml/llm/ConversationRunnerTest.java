package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationRunnerTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    @Test
    void builderRequiresAgentProvider() {
        assertThatThrownBy(() -> ConversationRunner.builder()
                .cognition(buildCognition())
                .descriptors(DescriptorLoader.load("historical-encounter"))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentProvider");
    }

    @Test
    void builderRequiresDescriptors() {
        assertThatThrownBy(() -> ConversationRunner.builder()
                .agentProvider(stubProvider())
                .cognition(buildCognition())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("descriptors");
    }

    @Test
    void buildsWithAllRequiredComponents() throws IOException {
        var runner = ConversationRunner.builder()
                .agentProvider(stubProvider())
                .cognition(buildCognition())
                .world(buildWorld())
                .descriptors(DescriptorLoader.load("historical-encounter"))
                .maxTurns(2)
                .build();

        assertThat(runner).isNotNull();
    }

    private static CognitionStack buildCognition() {
        try (var is = ConversationRunnerTest.class.getResourceAsStream(
                "/examples/historical-encounter/cognition.yaml")) {
            var def = YAML.readValue(is, CognitionDefinition.class);
            return CognitionStack.from(new CognitionCompiler().compile(def), null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledWorld buildWorld() throws IOException {
        try (var is = ConversationRunnerTest.class.getResourceAsStream(
                "/examples/historical-encounter/world.yaml")) {
            var def = YAML.readValue(is, WorldDefinition.class);
            return new WorldCompiler(new ObservationFilterRegistry()).compile(def);
        }
    }

    private static TestAgentProvider stubProvider() {
        var props = new io.casehub.platform.agent.claude.ClaudeAgentProperties() {
            @Override public java.util.Optional<String> binaryPath() { return java.util.Optional.empty(); }
            @Override public java.time.Duration defaultTimeout() { return java.time.Duration.ofSeconds(30); }
            @Override public int maxConcurrentSessions() { return 1; }
        };
        var client = new io.casehub.platform.agent.claude.ClaudeAgentClient(
                props, config -> io.smallrye.mutiny.Multi.createFrom().item(
                        new io.casehub.platform.agent.AgentEvent.TextDelta("Test response from stub.")));
        return new TestAgentProvider(client);
    }
}
