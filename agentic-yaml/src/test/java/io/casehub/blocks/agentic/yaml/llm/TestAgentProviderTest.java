package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestAgentProviderTest {

    @Test
    void invokeReturnsTextDelta() {
        var provider = TestAgentProvider.claude();
        var config = AgentSessionConfig.of(
                "You are a helpful assistant.",
                "Say hello in exactly one word.",
                Duration.ofSeconds(30));

        String response = provider.invokeBlocking(config);

        assertThat(response).isNotBlank();
    }
}
