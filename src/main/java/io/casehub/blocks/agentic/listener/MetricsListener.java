package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class MetricsListener implements ExecutionEventListener {

    private static final AttributeKey<String> AGENT = AttributeKey.stringKey("agent");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> DECISION_TYPE = AttributeKey.stringKey("decision_type");
    private static final AttributeKey<String> ACTIVATED = AttributeKey.stringKey("activated");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    private final DoubleHistogram agentDuration;
    private final LongCounter routingDecisions;
    private final LongCounter activationEvaluations;
    private final LongCounter agentFailures;
    private final DoubleHistogram executionDuration;
    private final DoubleHistogram executionIterations;

    public MetricsListener(Meter meter) {
        Objects.requireNonNull(meter, "meter");

        this.agentDuration = meter.histogramBuilder("casehub.agentic.agent.duration")
                .setUnit("s")
                .setDescription("Agent invocation latency")
                .build();

        this.routingDecisions = meter.counterBuilder("casehub.agentic.routing.decisions")
                .setDescription("Routing decision count by type")
                .build();

        this.activationEvaluations = meter.counterBuilder("casehub.agentic.activation.evaluations")
                .setDescription("Activation evaluation count by agent and outcome")
                .build();

        this.agentFailures = meter.counterBuilder("casehub.agentic.agent.failures")
                .setDescription("Agent invocation exception count")
                .build();

        this.executionDuration = meter.histogramBuilder("casehub.agentic.execution.duration")
                .setUnit("s")
                .setDescription("Total execution wall-clock time")
                .build();

        this.executionIterations = meter.histogramBuilder("casehub.agentic.execution.iterations")
                .setUnit("{iteration}")
                .setDescription("Iteration count per execution")
                .build();
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        routingDecisions.add(1, Attributes.of(
                DECISION_TYPE, decision.getClass().getSimpleName()));
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        activationEvaluations.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent),
                ACTIVATED, String.valueOf(activated)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        agentDuration.record(result.duration().toMillis() / 1000.0, Attributes.of(
                AGENT, ExecutionEventListener.agentName(result.agent()),
                STATUS, result.status().name()));
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        agentFailures.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                    int iterationCount) {
        var outcome = result.getClass().getSimpleName();
        this.executionDuration.record(executionDuration.toMillis() / 1000.0,
                Attributes.of(OUTCOME, outcome));
        this.executionIterations.record(iterationCount,
                Attributes.of(OUTCOME, outcome));
    }
}
