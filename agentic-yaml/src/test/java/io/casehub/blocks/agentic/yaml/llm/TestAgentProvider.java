package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.claude.ClaudeAgentClient;
import io.casehub.platform.agent.claude.ClaudeAgentProperties;
import io.smallrye.mutiny.Multi;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

public class TestAgentProvider implements AgentProvider {

    private final ClaudeAgentClient client;

    TestAgentProvider(ClaudeAgentClient client) {
        this.client = client;
    }

    public static TestAgentProvider claude() {
        var props = new ClaudeAgentProperties() {
            @Override public Optional<String> binaryPath() { return Optional.empty(); }
            @Override public Duration defaultTimeout() { return Duration.ofSeconds(300); }
            @Override public int maxConcurrentSessions() { return 4; }
        };
        var agentClient = new ClaudeAgentClient(props);
        return new TestAgentProvider(agentClient);
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return client.run(config);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return client.openSession(init);
    }

    public String invokeBlocking(AgentSessionConfig config) {
        return invoke(config)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(config.timeout() != null
                        ? config.timeout() : Duration.ofSeconds(300));
    }
}
