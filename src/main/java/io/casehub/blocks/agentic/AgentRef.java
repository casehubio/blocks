package io.casehub.blocks.agentic;

import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.worker.api.Worker;
import io.casehub.work.api.WorkItemCreateRequest;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public sealed interface AgentRef
        permits AgentRef.WorkerAgent, AgentRef.ChannelAgent,
                AgentRef.HumanAgent, AgentRef.ExternalAgent,
                AgentRef.ComposedAgent {

    record WorkerAgent(Worker worker) implements AgentRef {}

    record ChannelAgent(UUID channelId, ChannelAgentHandler handler)
            implements AgentRef {}

    record HumanAgent(WorkItemCreateRequest template) implements AgentRef {}

    record ExternalAgent(Function<Object, CompletionStage<AgentResult>> fn)
            implements AgentRef {}

    record ComposedAgent(ExecutionModel<?> model) implements AgentRef {}

    @SuppressWarnings("unchecked")
    static <T> ExternalAgent external(Function<T, CompletionStage<AgentResult>> fn) {
        var erased = (Function<Object, CompletionStage<AgentResult>>) (Function<?, ?>) fn;
        return new ExternalAgent(erased);
    }

    static WorkerAgent worker(Worker worker) {
        return new WorkerAgent(worker);
    }

    static ChannelAgent channel(UUID channelId, ChannelAgentHandler handler) {
        return new ChannelAgent(channelId, handler);
    }

    static HumanAgent human(WorkItemCreateRequest template) {
        return new HumanAgent(template);
    }

    static ComposedAgent composed(ExecutionModel<?> model) {
        return new ComposedAgent(model);
    }
}
