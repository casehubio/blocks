package io.casehub.blocks.agentic;

import io.casehub.worker.api.WorkerResult;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.worker.api.Worker;
import io.casehub.work.api.WorkItemCreateRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentRefTest {

    @Nested
    class WorkerAgentVariant {
        @Test
        void carriesWorker() {
            var worker = Worker.builder()
                    .name("w1")
                    .capabilityNames("test")
                    .function(x -> WorkerResult.of(Map.of()))
                    .build();
            var ref = new AgentRef.WorkerAgent(worker);
            assertThat(ref.worker()).isEqualTo(worker);
            assertThat(ref).isInstanceOf(AgentRef.class);
        }
    }

    @Nested
    class ChannelAgentVariant {
        @Test
        void carriesChannelIdAndHandler() {
            var channelId = UUID.randomUUID();
            var handler = mock(ChannelAgentHandler.class);
            var ref = new AgentRef.ChannelAgent(channelId, handler);
            assertThat(ref.channelId()).isEqualTo(channelId);
            assertThat(ref.handler()).isEqualTo(handler);
        }
    }

    @Nested
    class HumanAgentVariant {
        @Test
        void carriesWorkItemTemplate() {
            var template = WorkItemCreateRequest.builder()
                    .title("Review needed").build();
            var ref = new AgentRef.HumanAgent(template);
            assertThat(ref.template().title).isEqualTo("Review needed");
        }
    }

    @Nested
    class ExternalAgentVariant {
        @Test
        void carriesFunction() {
            // Create a simple test AgentRef to avoid circular dependency
            var testWorker = Worker.builder()
                    .name("test")
                    .capabilityNames("test")
                    .function(x -> WorkerResult.of(Map.of()))
                    .build();
            var testAgent = new AgentRef.WorkerAgent(testWorker);

            Function<Object, CompletionStage<AgentResult>> fn =
                    input -> CompletableFuture.completedFuture(
                            AgentResult.success(testAgent, "output"));
            var ref = new AgentRef.ExternalAgent(fn);
            assertThat(ref.fn()).isNotNull();
        }
    }

    @Nested
    class ComposedAgentVariant {
        @Test
        void carriesExecutionModel() {
            // ExecutionModel not yet implemented — test with null placeholder
            var ref = new AgentRef.ComposedAgent(null);
            assertThat(ref).isInstanceOf(AgentRef.class);
        }
    }

    @Test
    void sealedInterfacePermitsAllFiveVariants() {
        assertThat(AgentRef.class.getPermittedSubclasses()).hasSize(5);
    }
}
