package io.casehub.blocks.agentic;

import java.time.Duration;

public record AgentResult(
        AgentRef agent,
        Object output,
        Duration duration,
        AgentResultStatus status
) {
    public enum AgentResultStatus { SUCCESS, FAILURE, TIMEOUT, DECLINED }

    public static AgentResult success(AgentRef agent, Object output) {
        return new AgentResult(agent, output, Duration.ZERO, AgentResultStatus.SUCCESS);
    }

    public static AgentResult failure(AgentRef agent, Object output) {
        return new AgentResult(agent, output, Duration.ZERO, AgentResultStatus.FAILURE);
    }

    public static AgentResult timeout(AgentRef agent) {
        return new AgentResult(agent, null, Duration.ZERO, AgentResultStatus.TIMEOUT);
    }

    public static AgentResult declined(AgentRef agent, Object reason) {
        return new AgentResult(agent, reason, Duration.ZERO, AgentResultStatus.DECLINED);
    }
}
