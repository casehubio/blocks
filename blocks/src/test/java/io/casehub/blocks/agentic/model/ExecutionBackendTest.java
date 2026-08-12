package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.pattern.Patterns;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionBackendTest {

    @Test
    void orchestratedBackendExecutesModel() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(new RoutingCandidate(agent, null)),
            io.casehub.blocks.agentic.FailurePolicy.defaults(),
            List.of(), "test", null);

        ExecutionBackend<Object> backend = ExecutionBackend.orchestrated();
        var result = backend.execute(model, "input").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void customBackendIsUsedByBuilder() {
        var callCount = new AtomicInteger(0);
        ExecutionBackend<Object> custom = (model, ctx) -> {
            callCount.incrementAndGet();
            return io.smallrye.mutiny.Uni.createFrom()
                .item(new ExecutionResult.Completed("custom-result"));
        };

        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));

        var result = Patterns.<Object>sequence()
            .agents(agent)
            .backend(custom)
            .execute("input")
            .await().indefinitely();

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(((ExecutionResult.Completed) result).result()).isEqualTo("custom-result");
    }

    @Test
    void defaultBackendUsesOrchestratedDriver() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "output")));

        var result = Patterns.<Object>sequence()
            .agents(agent)
            .execute("input")
            .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void cancelDefaultIsNoOp() {
        ExecutionBackend<Object> backend = (model, ctx) ->
                                                   io.smallrye.mutiny.Uni.createFrom().item(new ExecutionResult.Completed("ok"));
        backend.cancel();
    }

    @Test
    void reactiveBackendCancelStopsExecution() {
        var invokeCount = new AtomicInteger(0);
        var agent = AgentRef.external("pacer", (Object ctx) -> {
            invokeCount.incrementAndGet();
            var future = new CompletableFuture<AgentResult>();
            new Thread(() -> {
                try {Thread.sleep(100);} catch (InterruptedException ignored) {}
                future.complete(AgentResult.success(null, "done"));
            }).start();
            return future;
        });

        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(100),
                () -> List.of(new RoutingCandidate(agent, null)),
                io.casehub.blocks.agentic.FailurePolicy.defaults(),
                List.of(), "test", null);

        ExecutionBackend<Object> backend = ExecutionBackend.reactive();
        var future = CompletableFuture.supplyAsync(() ->
                                                           backend.execute(model, "input").await().indefinitely());

        try {Thread.sleep(500);} catch (InterruptedException ignored) {}
        backend.cancel();

        var result = future.join();
        assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
        assertThat(invokeCount.get()).isGreaterThan(0).isLessThan(100);
    }

    @Test
    void choreographedBackendExecutesOnEvent() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object s) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
        });

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(new RoutingCandidate(agent, null)),
            io.casehub.blocks.agentic.FailurePolicy.defaults(),
            List.of(), "test", null);

        EventSource trigger = sink -> {
            sink.accept(DriverEvent.signal("go"));
            return EventSource.Cancellation.of(() -> {});
        };

        ExecutionBackend<Object> backend = ExecutionBackend.choreographed(
            EventConcurrencyPolicy.serialize(), trigger);
        var result = backend.execute(model, "input").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void choreographedBackendCancelStopsExecution() {
        EventSource trigger = sink -> {
            sink.accept(DriverEvent.signal("go"));
            return EventSource.Cancellation.of(() -> {});
        };

        var agent = AgentRef.external("slow", (Object ctx) -> {
            var future = new CompletableFuture<AgentResult>();
            new Thread(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                future.complete(AgentResult.success(null, "done"));
            }).start();
            return future;
        });

        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(100),
                () -> List.of(new RoutingCandidate(agent, null)),
                io.casehub.blocks.agentic.FailurePolicy.defaults(),
                List.of(), "test", null);

        ExecutionBackend<Object> backend = ExecutionBackend.choreographed(
            EventConcurrencyPolicy.serialize(), trigger);

        var future = CompletableFuture.supplyAsync(() ->
                backend.execute(model, "input").await().indefinitely());

        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        backend.cancel();

        var result = future.join();
        assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
    }

}
