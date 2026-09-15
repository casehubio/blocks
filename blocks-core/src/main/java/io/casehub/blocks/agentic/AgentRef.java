package io.casehub.blocks.agentic;

import io.casehub.api.model.ExecutorRef;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.worker.api.Worker;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public sealed interface AgentRef extends ExecutorRef
        permits AgentRef.WorkerAgent, AgentRef.ChannelAgent,
                AgentRef.HumanAgent, AgentRef.ExternalAgent,
                AgentRef.ComposedAgent {

    record WorkerAgent(Worker worker) implements AgentRef {
        @Override
        public String name()                  {return worker.name();}

        @Override
        public @Nullable String description() {return worker.description();}
    }

    record ChannelAgent(UUID channelId, ChannelAgentHandler handler)
            implements AgentRef {
        @Override
        public String name()                  {return "channel:" + channelId;}

        @Override
        public @Nullable String description() {return null;}
    }

    record HumanAgent(WorkItemCreateRequest template) implements AgentRef {
        @Override
        public String name()                  {return template != null && template.title != null ? template.title : "human";}

        @Override
        public @Nullable String description() {return null;}
    }

    record ExternalAgent(@Nullable String label,
                         Function<Object, CompletionStage<AgentResult>> fn)
            implements AgentRef {
        @Override
        public String name()                  {return label != null ? label : "external";}

        @Override
        public @Nullable String description() {return null;}
    }

    record ComposedAgent(ExecutionModel<?> model) implements AgentRef {
        @Override
        public String name()                  {return model != null && model.task() != null ? model.task() : "composed";}

        @Override
        public @Nullable String description() {return null;}
    }

    @SuppressWarnings("unchecked")
    static <T> ExternalAgent external(Function<T, CompletionStage<AgentResult>> fn) {
        var erased = (Function<Object, CompletionStage<AgentResult>>) (Function<?, ?>) fn;
        return new ExternalAgent(null, erased);
    }

    @SuppressWarnings("unchecked")
    static <T> ExternalAgent external(String label, Function<T, CompletionStage<AgentResult>> fn) {
        var erased = (Function<Object, CompletionStage<AgentResult>>) (Function<?, ?>) fn;
        return new ExternalAgent(label, erased);
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
