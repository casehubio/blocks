package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.ExecutionState;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsListenerTest {

    private InMemoryMetricReader reader;
    private MetricsListener listener;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        var meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        listener = new MetricsListener(meterProvider.get("test"));
    }

    @Nested
    class AgentDuration {

        @Test
        void recordsAgentDurationFromResult() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var result = new AgentResult(agent, "ok", Duration.ofMillis(150),
                    AgentResult.AgentResultStatus.SUCCESS);

            listener.onAgentResult(result);

            var metrics = reader.collectAllMetrics();
            var histogram = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.duration"))
                    .findFirst();
            assertThat(histogram).isPresent();
        }
    }

    @Nested
    class RoutingDecisions {

        @Test
        void countsRoutingDecisions() {
            var decision = new RoutingDecision.Selected(List.of(), "test");
            listener.onRoutingDecision(decision, List.of());

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.routing.decisions"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ActivationEvaluations {

        @Test
        void countsActivationEvaluations() {
            var agent = AgentRef.human(null);
            listener.onActivation(agent, true);
            listener.onActivation(agent, false);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.activation.evaluations"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class AgentFailures {

        @Test
        void countsAgentFailures() {
            var agent = AgentRef.human(null);
            listener.onFailure(agent, new RuntimeException("oops"));

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.failures"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ExecutionLifecycle {

        @Test
        void recordsExecutionDurationAndIterations() {
            var result = new ExecutionResult.Completed("done");
            listener.onExecutionComplete(result, Duration.ofSeconds(5), 7);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            var iterations = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.iterations"))
                    .findFirst();
            assertThat(duration).isPresent();
            assertThat(iterations).isPresent();
        }

        @Test
        void cancelledExecutionRecordsMetrics() {
            var result = new ExecutionResult.Cancelled();
            listener.onExecutionComplete(result, Duration.ofMillis(200), 1);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            assertThat(duration).isPresent();
        }
    }

    @Nested
    class NoOpSafety {

        @Test
        void worksWithNoOpMeter() {
            var noOpListener = new MetricsListener(
                    io.opentelemetry.api.OpenTelemetry.noop().getMeter("test"));
            var agent = AgentRef.human(null);
            noOpListener.onActivation(agent, true);
            noOpListener.onAgentResult(AgentResult.success(agent, "ok"));
            noOpListener.onFailure(agent, new RuntimeException("oops"));
            noOpListener.onExecutionComplete(new ExecutionResult.Completed("ok"),
                    Duration.ZERO, 1);
            // no exception = pass
        }
    }
}
