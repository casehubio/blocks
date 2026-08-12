package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ChoreographedDriverTest {

    private ExecutionModel<String> model(int maxIterations) {
        return model(maxIterations, AgentRef.external((Object input) ->
                                                              CompletableFuture.completedFuture(AgentResult.success(null, "done"))));
    }

    private ExecutionModel<String> model(int maxIterations, AgentRef agent) {
        return new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(maxIterations),
                () -> List.of(new RoutingCandidate(agent, null)),
                FailurePolicy.defaults(),
                List.of(), "test");
    }

    @Test
    void executesReactivelyOnEvents() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "reactive"));
        });

        var driver = new ChoreographedDriver<String>();
        var result = driver.execute(model(3, agent), "state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void eventDrivenModeWaitsForEvents() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
        });

        var driver = new ChoreographedDriver<>(
                AgentInvoker.<String>defaultInvoker(),
                EventConcurrencyPolicy.serialize(),
                sink -> {
                    new Thread(() -> {
                        try {Thread.sleep(50);} catch (InterruptedException ignored) {}
                        sink.accept(DriverEvent.signal("trigger-1"));
                        try {Thread.sleep(50);} catch (InterruptedException ignored) {}
                        sink.accept(DriverEvent.signal("trigger-2"));
                    }).start();
                    return EventSource.Cancellation.of(() -> {});
                });

        var result = driver.execute(model(2, agent), "state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void signalWakesDriverDirectly() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
        });

        var driver = new ChoreographedDriver<>(
                AgentInvoker.<String>defaultInvoker(),
                EventConcurrencyPolicy.serialize());

        var future = CompletableFuture.supplyAsync(() ->
                                                           driver.execute(model(1, agent), "state").await().indefinitely());

        try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        driver.signal("manual");

        var result = future.join();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void cancelDuringEventWaitExitsCleanly() {
        var driver = new ChoreographedDriver<>(
                AgentInvoker.<String>defaultInvoker(),
                EventConcurrencyPolicy.serialize(),
                sink -> EventSource.Cancellation.of(() -> {}));

        var future = CompletableFuture.supplyAsync(() ->
                                                           driver.execute(model(100), "state").await().indefinitely());

        try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        driver.cancel();

        var result = future.join();
        assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
    }

    @Test
    void eventSourceCancelledOnDriverCompletion() {
        var sourceCancel = new AtomicBoolean(false);
        var agent = AgentRef.external((Object input) ->
                                              CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        var driver = new ChoreographedDriver<>(
                AgentInvoker.<String>defaultInvoker(),
                EventConcurrencyPolicy.serialize(),
                sink -> {
                    sink.accept(DriverEvent.signal("go"));
                    return EventSource.Cancellation.of(() -> sourceCancel.set(true));
                });

        driver.execute(model(1, agent), "state").await().indefinitely();
        assertThat(sourceCancel.get()).isTrue();
    }

    @Test
    void cancelDuringAgentDispatchExitsAfterIteration() {
        var dispatchStarted = new AtomicBoolean(false);
        var agent = AgentRef.external((Object input) -> {
            dispatchStarted.set(true);
            var future = new CompletableFuture<AgentResult>();
            new Thread(() -> {
                try {Thread.sleep(300);} catch (InterruptedException ignored) {}
                future.complete(AgentResult.success(null, "done"));
            }).start();
            return future;
        });

        var driver = new ChoreographedDriver<>(
                AgentInvoker.<String>defaultInvoker(),
                EventConcurrencyPolicy.serialize(),
                sink -> {
                    sink.accept(DriverEvent.signal("go"));
                    return EventSource.Cancellation.of(() -> {});
                });

        var future = CompletableFuture.supplyAsync(() ->
                                                           driver.execute(model(100, agent), "state").await().indefinitely());

        await().atMost(Duration.ofSeconds(2)).untilTrue(dispatchStarted);
        driver.cancel();

        var result = future.join();
        assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
    }

    @Test
    void tickerDrivesPeriodicIterations() {
        var iterationCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            iterationCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
        });

        var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
            var driver = new ChoreographedDriver<>(
                    AgentInvoker.<String>defaultInvoker(),
                    EventConcurrencyPolicy.serialize(),
                    EventSource.ticker(Duration.ofMillis(50), executor));

            var result = driver.execute(model(3, agent), "state").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
            assertThat(iterationCount.get()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }
}
