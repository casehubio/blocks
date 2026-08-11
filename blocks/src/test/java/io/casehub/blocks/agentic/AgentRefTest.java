package io.casehub.blocks.agentic;

import io.casehub.api.model.ExecutorRef;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.channel.ChannelAgentHandler;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
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
    class ExecutorRefContract {
        @Test
        void workerAgentDelegatesNameAndDescriptionToWorker() {
            var worker = Worker.builder()
                               .name("clinical-reviewer")
                               .capabilityNames("review")
                               .description("Reviews clinical data")
                               .function(x -> WorkerResult.of(Map.of()))
                               .build();
            AgentRef ref = new AgentRef.WorkerAgent(worker);
            assertThat(ref).isInstanceOf(ExecutorRef.class);
            assertThat(ref.name()).isEqualTo("clinical-reviewer");
            assertThat(ref.description()).isEqualTo("Reviews clinical data");
        }

        @Test
        void workerAgentNullDescriptionWhenWorkerHasNone() {
            var worker = Worker.builder()
                               .name("w1")
                               .capabilityNames("test")
                               .function(x -> WorkerResult.of(Map.of()))
                               .build();
            assertThat(new AgentRef.WorkerAgent(worker).description()).isNull();
        }

        @Test
        void channelAgentNameIncludesChannelId() {
            var channelId = UUID.randomUUID();
            var ref       = new AgentRef.ChannelAgent(channelId, mock(ChannelAgentHandler.class));
            assertThat(ref.name()).isEqualTo("channel:" + channelId);
            assertThat(ref.description()).isNull();
        }

        @Test
        void humanAgentNameComesFromTemplateTitle() {
            var template = WorkItemCreateRequest.builder()
                                                .title("Review needed").build();
            var ref = new AgentRef.HumanAgent(template);
            assertThat(ref.name()).isEqualTo("Review needed");
            assertThat(ref.description()).isNull();
        }

        @Test
        void humanAgentFallsBackToHumanWhenTitleNull() {
            var template = WorkItemCreateRequest.builder()
                                                .templateId(UUID.randomUUID()).build();
            var ref = new AgentRef.HumanAgent(template);
            assertThat(ref.name()).isEqualTo("human");
        }

        @Test
        void externalAgentUsesLabelWhenProvided() {
            var ref = AgentRef.external("my-tool", x -> CompletableFuture.completedFuture(
                    AgentResult.success(null, "ok")));
            assertThat(ref.name()).isEqualTo("my-tool");
            assertThat(ref.description()).isNull();
        }

        @Test
        void externalAgentFallsBackToExternalWhenNoLabel() {
            var ref = AgentRef.external(x -> CompletableFuture.completedFuture(
                    AgentResult.success(null, "ok")));
            assertThat(ref.name()).isEqualTo("external");
        }

        @Test
        void composedAgentUsesModelTaskName() {
            var model = mock(ExecutionModel.class);
            org.mockito.Mockito.when(model.task()).thenReturn("analyse-workflow");
            var ref = new AgentRef.ComposedAgent(model);
            assertThat(ref.name()).isEqualTo("analyse-workflow");
            assertThat(ref.description()).isNull();
        }

        @Test
        void composedAgentFallsBackWhenModelNull() {
            var ref = new AgentRef.ComposedAgent(null);
            assertThat(ref.name()).isEqualTo("composed");
        }

        @Test
        void composedAgentFallsBackWhenTaskNameNull() {
            var model = mock(ExecutionModel.class);
            org.mockito.Mockito.when(model.task()).thenReturn(null);
            var ref = new AgentRef.ComposedAgent(model);
            assertThat(ref.name()).isEqualTo("composed");
        }

        @Test
        void allVariantsAreExecutorRef() {
            var worker = Worker.builder().name("w").capabilityNames("c")
                               .function(x -> WorkerResult.of(Map.of())).build();
            assertThat((ExecutorRef) new AgentRef.WorkerAgent(worker)).isNotNull();
            assertThat((ExecutorRef) new AgentRef.ChannelAgent(UUID.randomUUID(), null)).isNotNull();
            assertThat((ExecutorRef) AgentRef.human(null)).isNotNull();
            assertThat((ExecutorRef) AgentRef.external(x -> CompletableFuture.completedFuture(null))).isNotNull();
            assertThat((ExecutorRef) new AgentRef.ComposedAgent(null)).isNotNull();
        }
    }

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
            var handler   = mock(ChannelAgentHandler.class);
            var ref       = new AgentRef.ChannelAgent(channelId, handler);
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
        void carriesLabelAndFunction() {
            var testWorker = Worker.builder()
                                   .name("test")
                                   .capabilityNames("test")
                                   .function(x -> WorkerResult.of(Map.of()))
                                   .build();
            var testAgent = new AgentRef.WorkerAgent(testWorker);

            Function<Object, CompletionStage<AgentResult>> fn =
                    input -> CompletableFuture.completedFuture(
                            AgentResult.success(testAgent, "output"));
            var ref = new AgentRef.ExternalAgent(null, fn);
            assertThat(ref.fn()).isNotNull();
            assertThat(ref.label()).isNull();
        }

        @Test
        void labeledFactoryPreservesLabel() {
            var ref = AgentRef.external("my-api", x ->
                                                          CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            assertThat(ref.label()).isEqualTo("my-api");
        }
    }

    @Nested
    class ComposedAgentVariant {
        @Test
        void carriesExecutionModel() {
            var ref = new AgentRef.ComposedAgent(null);
            assertThat(ref).isInstanceOf(AgentRef.class);
        }
    }

    @Test
    void sealedInterfacePermitsAllFiveVariants() {
        assertThat(AgentRef.class.getPermittedSubclasses()).hasSize(5);
    }
}
