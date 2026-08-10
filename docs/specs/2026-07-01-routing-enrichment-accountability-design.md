# Routing Enrichment & Accountability Design

**Issues:** blocks#8 (batch minor findings), blocks#11 (routing enrichment), blocks#12 (routing accountability)
**Parent:** blocks#10 (supervisor mode epic)
**Branch:** issue-8-routing-enrichment-accountability

## Context

Three issues share a single data flow spine: **RoutingCandidate → RoutingContext → RoutingDecision → ExecutionEventListener**. This spec addresses them together because the data model enrichments (#8, #11) feed directly into the accountability listeners (#12), and the pattern wiring fixes (#8.5) demonstrate the enriched model working end-to-end.

### Architectural Position

Blocks is a compositional incubation layer above the engine. It depends on foundation API modules at compile time (`casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `casehub-work-api`) and foundation runtimes at test time. The type parameter `T` propagates from the execution entry point through all five SPIs and into the driver — serialization to String happens only at external API boundaries (LLM calls), not within the typed data flow.

### Cross-Repo Dependencies

Three cross-repo issues were filed to unblock this work. The design uses interim functional interfaces that the cross-repo changes will fulfil:

| Issue | Repo | What | Status |
|-------|------|------|--------|
| eidos#77 | casehubio/eidos | Add `description` field to `AgentCapability` | Filed |
| engine#626 | casehubio/engine | Extract event write SPI to engine-api + orchestration event types on `CaseHubEventType` | Filed |
| ledger#168 | casehubio/ledger | Extract ledger write SPI (`LedgerAppender`) to ledger-api | Filed |

## Design

### 1. ExecutionModel — task description and null validation

`ExecutionModel<T>` gains a `String task` field — a natural-language description of what the execution is accomplishing. This flows into `RoutingContext<T>` where the driver currently hard-codes `"task"`.

The compact constructor validates all SPI fields. Currently it only does `List.copyOf(listeners)` — a builder that forgets `routing()` produces an NPE at runtime with no indication of what's missing.

```java
public record ExecutionModel<T>(
        RoutingStrategy<T> routing,
        DecompositionStrategy<T> decomposition,
        ActivationRule<T> activation,
        AggregationStrategy<T> aggregation,
        TerminationCondition<T> termination,
        Supplier<List<RoutingCandidate>> candidateSupplier,
        FailurePolicy failurePolicy,
        List<ExecutionEventListener> listeners,
        String task
) {
    public ExecutionModel {
        Objects.requireNonNull(routing, "routing");
        Objects.requireNonNull(decomposition, "decomposition");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(termination, "termination");
        Objects.requireNonNull(candidateSupplier, "candidateSupplier");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        listeners = List.copyOf(listeners);
        Objects.requireNonNull(task, "task");
    }
}
```

`AbstractExecutionDriver.executeIteration()` changes from `new RoutingContext<>("task", ...)` to `new RoutingContext<>(model.task(), ...)`.

`AbstractPatternBuilder` gains a `task(String)` method with a default of `"execution"`. Each pattern builder overrides the default in its constructor (`"debate"`, `"supervisor"`, `"htn"`, etc.). Callers override with `.task("review PR #42 for security issues")` for meaningful descriptions that reach the LLM prompt.

`AbstractPatternBuilder.build()` includes the new field:

```java
public ExecutionModel<T> build() {
    return new ExecutionModel<>(routing, decomposition, activation,
            aggregation, termination, candidateSupplier,
            failurePolicy, listeners, task);
}
```

### 2. RoutingCandidate — @Nullable descriptor

```java
/**
 * A candidate for agent routing — pairs a dispatch reference with an optional identity profile.
 *
 * @param ref        the agent dispatch reference — never null
 * @param descriptor the agent's identity profile for routing metadata.
 *                   Nullable — pattern builders that accept bare {@link AgentRef}
 *                   create candidates without descriptors. Routing strategies
 *                   must handle null descriptors gracefully.
 */
public record RoutingCandidate(AgentRef ref, @Nullable AgentDescriptor descriptor) {
    public RoutingCandidate {
        Objects.requireNonNull(ref, "ref");
    }
}
```

`@Nullable` throughout this spec is `org.jspecify.annotations.Nullable` (JSpecify) — the modern Java standard, designed for records and compatible with NullAway/Error Prone tooling. New dependency: `org.jspecify:jspecify:1.0.0`.

### 3. RoutingDecision.Selected — reason preservation

`Selected` carries the LLM's reasoning alongside the selected agents:

```java
public sealed interface RoutingDecision
        permits RoutingDecision.Selected, RoutingDecision.Unresolvable,
                RoutingDecision.Escalate {

    record Selected(List<AgentRef> agents, @Nullable String reason) implements RoutingDecision {
        public Selected { agents = List.copyOf(agents); }
        public Selected(List<AgentRef> agents) { this(agents, null); }
    }

    record Unresolvable(String reason) implements RoutingDecision {}
    record Escalate(String reason) implements RoutingDecision {}
}
```

`LlmSelectedRouting.parseSelection()` extracts both agent name and reason from the JSON response. An `extractReason()` method parallels the existing `extractAgentName()`.

Non-LLM routing strategies (`FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting`) use the single-arg `Selected(List<AgentRef>)` constructor — reason is null. Only LLM-driven routing produces a reason.

The reason flows into `ExecutionEventListener.onRoutingDecision()` — listeners read it from the `Selected` record. EventLogListener and LedgerExecutionListener (§7) persist it as audit data.

### 4. LlmSelectedRouting — state rendering and capability descriptions

**Type propagation principle:** `T` is typed state propagated from the execution entry point through all five SPIs. `LlmSelectedRouting<T>` converts `T` to String at the LLM API boundary via a `Function<T, String> stateRenderer` — the only point where serialization occurs. This is documented in javadoc on the class, on the constructor parameter, and on `RoutingContext<T>`.

```java
/**
 * LLM-driven agent selection. Builds a prompt from the task description, current typed
 * state, and agent capability cards, then invokes an LLM to select the best agent.
 *
 * <p>The type parameter {@code T} flows typed from {@link ExecutionModel} through
 * {@link RoutingContext} to this strategy. The {@code stateRenderer} converts {@code T}
 * to String at the LLM API boundary — the only point where typed state is serialized.
 * This preserves type safety throughout the execution pipeline while giving the LLM
 * visibility into accumulated execution state.
 *
 * @param <T> the typed execution state — propagated from the entry point, never pre-serialized
 */
public class LlmSelectedRouting<T> implements RoutingStrategy<T> {

    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;

    public LlmSelectedRouting(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this.agentProvider = agentProvider;
        this.stateRenderer = stateRenderer;
    }

    public LlmSelectedRouting(AgentProvider agentProvider) {
        this(agentProvider, Object::toString);
    }
}
```

**Prompt construction** includes state and enriched agent cards:

```java
private String buildUserPrompt(RoutingContext<T> context) {
    var sb = new StringBuilder();
    sb.append("Task: ").append(context.task()).append("\n\n");
    if (context.state() != null) {
        sb.append("Current state:\n").append(stateRenderer.apply(context.state())).append("\n\n");
    }
    sb.append("Available agents:\n");
    for (int i = 0; i < context.candidates().size(); i++) {
        sb.append(buildAgentCard(context.candidates().get(i), i)).append("\n");
    }
    return sb.toString();
}
```

**Agent card enrichment** — capability descriptions from available sources:

1. `Worker.description()` — extracted from `AgentRef.WorkerAgent` on the candidate
2. `AgentDescriptor.briefing()` — already used
3. `AgentCapability.description()` — when eidos#77 ships (field doesn't exist yet)
4. `AgentCapability.epistemicDomains()` — already used

```java
private String buildAgentCard(RoutingCandidate candidate, int index) {
    var sb = new StringBuilder();
    var name = candidateName(candidate, index);
    sb.append("- Agent: \"").append(name).append("\"");

    if (candidate.ref() instanceof AgentRef.WorkerAgent w
            && w.worker().description() != null) {
        sb.append("\n  Description: ").append(w.worker().description());
    }

    var descriptor = candidate.descriptor();
    if (descriptor != null) {
        if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
            sb.append("\n  Briefing: ").append(descriptor.briefing());
        }
        if (descriptor.capabilities() != null && !descriptor.capabilities().isEmpty()) {
            var caps = descriptor.capabilities().stream()
                    .map(this::formatCapability)
                    .collect(Collectors.joining(", "));
            sb.append("\n  Capabilities: ").append(caps);
        }
    }
    return sb.toString();
}
```

**SupervisorBuilder integration:** `SupervisorBuilder(AgentProvider)` gains a `stateRenderer(Function<T, String>)` setter. The renderer is applied eagerly in the setter — not in `build()` — using natural last-writer-wins semantics. If the caller later calls `route(custom)`, it overwrites. If they call `stateRenderer()` after `route()`, it overwrites with a new LlmSelectedRouting. Both orderings are visible at the call site:

```java
public SupervisorBuilder<T> stateRenderer(Function<T, String> stateRenderer) {
    this.stateRenderer = stateRenderer;
    if (agentProvider != null) {
        this.routing = new LlmSelectedRouting<>(agentProvider, stateRenderer);
    }
    return this;
}
```

No `build()` override needed — routing is already set correctly by the setter.

### 5. JudgeConvergence — debate judge wiring

New class: `JudgeConvergence<T> implements TerminationCondition<T>`. Invokes a judge agent with accumulated debate results and maps the response to continue/converge. Composes with a max-iterations safety cap.

**Type design:** The judge receives `List<AgentResult>` as input — the full debate history — not execution state `T`. JudgeConvergence uses its own `AgentInvoker<List<AgentResult>>`, typed to pass debate results. This preserves type safety: the execution state flows typed through the five SPIs; the judge sees debate results, not raw state.

```java
/**
 * Terminates when a judge agent declares convergence over accumulated debate results.
 * Composes with a max-iterations safety cap.
 *
 * <p>Uses its own {@code AgentInvoker<List<AgentResult>>} — typed to pass debate
 * history, not execution state {@code T}. This preserves type safety: the execution
 * state flows typed through the five SPIs; the judge sees debate results, not raw state.
 */
public class JudgeConvergence<T> implements TerminationCondition<T> {

    private final AgentRef judge;
    private final int maxIterations;
    private final AgentInvoker<List<AgentResult>> judgeInvoker;
    private final Predicate<AgentResult> convergencePredicate;

    public JudgeConvergence(AgentRef judge, int maxIterations) {
        this(judge, maxIterations, AgentInvoker.defaultInvoker(),
                result -> result.status() == AgentResult.AgentResultStatus.SUCCESS);
        if (!(judge instanceof AgentRef.ExternalAgent)) {
            throw new IllegalArgumentException(
                    "Convenience constructor requires ExternalAgent — " +
                    "use the full constructor with an appropriate AgentInvoker for other agent types");
        }
    }

    public JudgeConvergence(AgentRef judge, int maxIterations,
                            AgentInvoker<List<AgentResult>> judgeInvoker,
                            Predicate<AgentResult> convergencePredicate) {
        this.judge = judge;
        this.maxIterations = maxIterations;
        this.judgeInvoker = judgeInvoker;
        this.convergencePredicate = convergencePredicate;
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        if (context.iterationCount() >= maxIterations) {
            return Uni.createFrom().item(
                    new TerminationDecision.Complete(context.results()));
        }

        return judgeInvoker.invoke(judge, context.results())
                .map(result -> {
                    if (convergencePredicate.test(result)) {
                        return (TerminationDecision) new TerminationDecision.Complete(result.output());
                    }
                    return TerminationDecision.Continue.INSTANCE;
                });
    }
}
```

**DebateBuilder changes:**

- Default aggregation: `CollectAll` instead of `PassThrough` (PassThrough loses debate history — the judge needs all prior responses)
- Override `build()` to wire judge into termination when set

```java
public class DebateBuilder<T> extends AbstractPatternBuilder<T, DebateBuilder<T>> {

    private int maxRounds = 5;
    private AgentRef judge;
    private boolean convergenceExplicitlySet;

    public DebateBuilder() {
        this.task = "debate";
        this.routing = new RoundRobinRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = new MaxIterationsTermination<>(maxRounds);
    }

    public DebateBuilder<T> judge(AgentRef judge) {
        this.judge = judge;
        return this;
    }

    public DebateBuilder<T> maxRounds(int rounds) {
        this.maxRounds = rounds;
        this.termination = new MaxIterationsTermination<>(rounds);
        this.convergenceExplicitlySet = false;
        return this;
    }

    public DebateBuilder<T> convergence(TerminationCondition<T> convergence) {
        this.termination = convergence;
        this.convergenceExplicitlySet = true;
        return this;
    }

    @Override
    public ExecutionModel<T> build() {
        if (judge != null && convergenceExplicitlySet) {
            throw new IllegalStateException(
                    "judge() and convergence() are mutually exclusive — use one or the other");
        }
        if (judge != null) {
            this.termination = new JudgeConvergence<>(judge, maxRounds);
        }
        return super.build();
    }
}
```

### 6. HTN root task wiring — pre-decomposition at execute()

`HtnBuilder` overrides `execute()` to eagerly decompose the task tree using the initial state, extract primitive task agents as routing candidates, and wire sequential execution with sized termination.

Guard evaluation happens once at execute() time — the initial state `T` determines which decomposition methods fire. For runtime re-decomposition (guards that depend on intermediate state), see the decomposition phase integration tracked in blocks#13.

```java
public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task = "htn";
        this.routing = new SequentialRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = ctx -> Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }

    public HtnBuilder<T> rootTask(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }

    @Override
    public Uni<ExecutionResult> execute(T initialContext) {
        if (rootTask == null) {
            return super.execute(initialContext);
        }

        var primitives = flatten(rootTask, initialContext);
        var candidates = primitives.stream()
                .map(p -> new RoutingCandidate(p.agent(), null))
                .toList();
        var model = new ExecutionModel<>(
                routing, decomposition, activation, aggregation,
                new MaxIterationsTermination<>(primitives.size()),
                () -> candidates,
                failurePolicy, listeners, task);
        return new OrchestratedDriver<T>().execute(model, initialContext);
    }

    /**
     * Pre-decomposition runs without agent context — the task tree structure
     * determines which agents participate. DecompositionContext receives an
     * empty candidates list because candidates ARE the output of decomposition.
     * For agent-aware decomposition at runtime, see blocks#13.
     */
    private List<TaskNode.PrimitiveTask<T>> flatten(TaskNode<T> node, T state) {
        if (node instanceof TaskNode.PrimitiveTask<T> p) {
            return List.of(p);
        }
        var compound = (TaskNode.CompoundTask<T>) node;
        for (var method : compound.methods()) {
            if (method.guard().test(state)) {
                var children = method.strategy()
                        .decompose(compound, new DecompositionContext<>(state, List.of(), 0))
                        .await().indefinitely();
                return children.stream()
                        .flatMap(child -> flatten(child, state).stream())
                        .toList();
            }
        }
        return List.of();
    }
}
```

### 7. Accountability listeners

Two concrete `ExecutionEventListener` implementations. Each takes a functional write sink at construction — the consumer provides the store-specific implementation.

**Agent naming utility:** `ExecutionEventListener` gains a static helper `agentName(AgentRef)` — shared naming logic for all listener implementations:

```java
static String agentName(AgentRef agent) {
    if (agent instanceof AgentRef.WorkerAgent w) return w.worker().name();
    if (agent instanceof AgentRef.ChannelAgent c) return "channel:" + c.channelId();
    if (agent instanceof AgentRef.ExternalAgent e) return "external:" + Integer.toHexString(System.identityHashCode(e));
    if (agent instanceof AgentRef.HumanAgent) return "human";
    if (agent instanceof AgentRef.ComposedAgent) return "composed";
    return agent.getClass().getSimpleName();
}
```

**Blocks-local event taxonomy:**

```java
/**
 * Orchestration-level event types produced by blocks' execution drivers.
 * Maps to {@link CaseHubEventType} at the EventLog sink — the mapping is owned by
 * the sink implementation, not by blocks. When engine#626 adds orchestration types
 * to {@code CaseHubEventType}, the sink maps directly.
 */
public enum OrchestrationEventType {
    EXECUTION_STARTED,
    ROUTING_DECISION,
    ACTIVATION_EVALUATED,
    AGENT_DISPATCHED,
    AGENT_RESULT,
    AGENT_FAILED,
    AGGREGATION_COMPLETED,
    TERMINATION_EVALUATED,
    EXECUTION_COMPLETED
}
```

**EventLogListener — operational audit:**

```java
/**
 * Persists orchestration events to the engine's EventLog via a consumer-provided sink.
 * Records all events for operational observability.
 *
 * <p>Sink implementations must be non-blocking — use in-memory buffers or async
 * queues. The sink is called synchronously from the driver loop; blocking I/O
 * in the sink stalls the execution.
 */
public class EventLogListener implements ExecutionEventListener {

    @FunctionalInterface
    public interface EventSink {
        void record(OrchestrationEventType type, Map<String, Object> payload);
    }

    private final EventSink sink;

    public EventLogListener(EventSink sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("decision", decision.getClass().getSimpleName());
        payload.put("candidateCount", candidates.size());
        if (decision instanceof RoutingDecision.Selected s) {
            payload.put("selectedAgents", s.agents().size());
            if (s.reason() != null) payload.put("reason", s.reason());
        } else if (decision instanceof RoutingDecision.Unresolvable u) {
            payload.put("reason", u.reason());
        } else if (decision instanceof RoutingDecision.Escalate e) {
            payload.put("reason", e.reason());
        }
        sink.record(OrchestrationEventType.ROUTING_DECISION, payload);
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        sink.record(OrchestrationEventType.ACTIVATION_EVALUATED, Map.of(
                "agent", ExecutionEventListener.agentName(agent),
                "activated", activated));
    }

    @Override
    public void onAgentDispatched(AgentRef agent) {
        sink.record(OrchestrationEventType.AGENT_DISPATCHED,
                Map.of("agent", ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agent", ExecutionEventListener.agentName(result.agent()));
        payload.put("status", result.status().name());
        payload.put("durationMs", result.duration().toMillis());
        if (result.output() != null) {
            payload.put("outputType", result.output().getClass().getSimpleName());
            payload.put("outputSummary", truncate(result.output().toString(), 500));
        }
        sink.record(OrchestrationEventType.AGENT_RESULT, payload);
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        sink.record(OrchestrationEventType.AGENT_FAILED, Map.of(
                "agent", ExecutionEventListener.agentName(agent),
                "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName()));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public void onAggregation(AggregationResult result) {
        sink.record(OrchestrationEventType.AGGREGATION_COMPLETED, Map.of(
                "result", result.getClass().getSimpleName()));
    }

    @Override
    public void onTermination(TerminationDecision decision) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("decision", decision.getClass().getSimpleName());
        if (decision instanceof TerminationDecision.Failed f) payload.put("reason", f.reason());
        if (decision instanceof TerminationDecision.Escalate e) payload.put("reason", e.reason());
        sink.record(OrchestrationEventType.TERMINATION_EVALUATED, payload);
    }

    @Override
    public void onExecutionStart(ExecutionModel<?> model) {
        sink.record(OrchestrationEventType.EXECUTION_STARTED, Map.of("task", model.task()));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result) {
        sink.record(OrchestrationEventType.EXECUTION_COMPLETED, Map.of(
                "result", result.getClass().getSimpleName()));
    }
}
```

**LedgerExecutionListener — compliance audit:**

```java
/**
 * Persists compliance-grade audit entries via a consumer-provided sink.
 * Each entry carries actor identity and role for EU AI Act Art.12 regulatory traceability.
 *
 * <p>Records the full decision-dispatch-result chain: routing decisions with LLM reasoning,
 * activation evaluations, agent dispatches and results, termination decisions,
 * escalation events, and failures. This chain closes the Art.12 audit gap — you know
 * what was decided (routing), what was activated, what was dispatched, what each agent
 * returned, and why execution terminated.
 *
 * <p>Sink implementations must be non-blocking — use in-memory buffers or async
 * queues. The sink is called synchronously from the driver loop; blocking I/O
 * in the sink stalls the execution.
 */
public class LedgerExecutionListener implements ExecutionEventListener {

    @FunctionalInterface
    public interface LedgerSink {
        void record(OrchestrationEventType type, String actorId, String actorRole,
                    Map<String, Object> data);
    }

    private final LedgerSink sink;
    private final String supervisorActorId;

    public LedgerExecutionListener(LedgerSink sink, String supervisorActorId) {
        this.sink = Objects.requireNonNull(sink);
        this.supervisorActorId = Objects.requireNonNull(supervisorActorId);
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        var data = new LinkedHashMap<String, Object>();
        data.put("decision", decision.getClass().getSimpleName());
        data.put("candidateCount", candidates.size());
        if (decision instanceof RoutingDecision.Selected s) {
            data.put("selectedAgents", s.agents().size());
            if (s.reason() != null) data.put("reason", s.reason());
        } else if (decision instanceof RoutingDecision.Unresolvable u) {
            data.put("reason", u.reason());
        } else if (decision instanceof RoutingDecision.Escalate e) {
            data.put("reason", e.reason());
        }
        sink.record(OrchestrationEventType.ROUTING_DECISION,
                supervisorActorId, "orchestration-router", data);
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        sink.record(OrchestrationEventType.ACTIVATION_EVALUATED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent),
                       "activated", activated));
    }

    @Override
    public void onAgentDispatched(AgentRef agent) {
        sink.record(OrchestrationEventType.AGENT_DISPATCHED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        var data = new LinkedHashMap<String, Object>();
        data.put("agent", ExecutionEventListener.agentName(result.agent()));
        data.put("status", result.status().name());
        data.put("durationMs", result.duration().toMillis());
        if (result.output() != null) {
            data.put("outputType", result.output().getClass().getSimpleName());
            data.put("outputSummary", truncate(result.output().toString(), 500));
        }
        sink.record(OrchestrationEventType.AGENT_RESULT,
                supervisorActorId, "orchestration-supervisor", data);
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        sink.record(OrchestrationEventType.AGENT_FAILED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent),
                       "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName()));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public void onTermination(TerminationDecision decision) {
        var data = new LinkedHashMap<String, Object>();
        data.put("decision", decision.getClass().getSimpleName());
        if (decision instanceof TerminationDecision.Failed f) data.put("reason", f.reason());
        if (decision instanceof TerminationDecision.Escalate e) data.put("reason", e.reason());
        sink.record(OrchestrationEventType.TERMINATION_EVALUATED,
                supervisorActorId, "orchestration-supervisor", data);
    }

    @Override
    public void onExecutionStart(ExecutionModel<?> model) {
        sink.record(OrchestrationEventType.EXECUTION_STARTED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("task", model.task()));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result) {
        var data = new LinkedHashMap<String, Object>();
        data.put("result", result.getClass().getSimpleName());
        if (result instanceof ExecutionResult.Escalated e) data.put("reason", e.reason());
        if (result instanceof ExecutionResult.Failed f) data.put("reason", f.reason());
        sink.record(OrchestrationEventType.EXECUTION_COMPLETED,
                supervisorActorId, "orchestration-supervisor", data);
    }
}
```

### 8. Minor fixes

**#8.2 — invokeAgent null:** Verified as fixed by the engine integration work. `AbstractExecutionDriver.invokeAgent()` correctly passes `context` through `invoker.invoke(agent, context)`. No action.

**#8.6 — FQN imports:** `OrchestratedDriver` line 36 and `ChoreographedDriver` line 43 use `io.casehub.blocks.agentic.AgentResult` inline. Change to regular import statements.

## Type Summary

**New types:**

| Type | Package | Purpose |
|------|---------|---------|
| `JudgeConvergence<T>` | `agentic.termination` | Judge-driven debate convergence termination |
| `OrchestrationEventType` | `agentic.listener` | Blocks-local orchestration event taxonomy |
| `EventLogListener` | `agentic.listener` | Operational audit via EventLog sink |
| `LedgerExecutionListener` | `agentic.listener` | Compliance audit via ledger sink |

**Modified types:**

| Type | Change |
|------|--------|
| `ExecutionModel<T>` | +`task` field, null-check all SPI fields in compact constructor |
| `RoutingDecision.Selected` | +`reason` field (@Nullable), convenience single-arg constructor |
| `RoutingCandidate` | +`@Nullable` on `descriptor`, +null-check on `ref` |
| `LlmSelectedRouting<T>` | +`stateRenderer` constructor param, state in prompt, enriched agent cards |
| `DebateBuilder<T>` | Override `build()` to wire judge, default aggregation → `CollectAll`, `judge()`/`convergence()` mutual exclusivity |
| `HtnBuilder<T>` | Rename `task()` → `rootTask()`, override `execute()` for pre-decomposition (no builder mutation), default routing → `SequentialRouting`, aggregation → `CollectAll` |
| `SupervisorBuilder<T>` | +`stateRenderer(Function<T, String>)` setter (eagerly applies to routing), store `agentProvider` field |
| `AbstractPatternBuilder<T, B>` | +`task` field with setter, updated `build()` with 9th field, flows into `ExecutionModel` |
| `ExecutionEventListener` | +static `agentName(AgentRef)` utility method |
| `AbstractExecutionDriver<T>` | Use `model.task()` in RoutingContext instead of `"task"` |
| `OrchestratedDriver<T>` | Fix FQN import |
| `ChoreographedDriver<T>` | Fix FQN import |

**Unchanged:**

- Five SPI interfaces (RoutingStrategy, DecompositionStrategy, ActivationRule, AggregationStrategy, TerminationCondition)
- All sealed types except RoutingDecision.Selected
- All context records (RoutingContext, ActivationContext, AggregationContext, TerminationContext, DecompositionContext)
- All existing SPI implementations
- All non-LLM routing strategies (FirstMatchRouting, RoundRobinRouting, SequentialRouting)
- FailurePolicy, AgentRef, AgentResult, ExecutionState, ExecutionResult

## Testing Strategy

**ExecutionModel null validation:** Verify NPE with named message for each null SPI field.

**RoutingDecision.Selected reason:** Verify reason flows through LlmSelectedRouting parsing, verify null for non-LLM strategies, verify listeners receive reason.

**LlmSelectedRouting state rendering:** Verify state appears in prompt via stateRenderer, verify Worker.description in agent cards, verify AgentDescriptor.briefing in agent cards.

**JudgeConvergence:** Verify judge invoked with accumulated results, verify convergence terminates execution, verify non-convergence continues, verify max-iterations safety cap, verify custom convergence predicate, verify convenience constructor throws for non-ExternalAgent.

**Debate end-to-end:** Two debaters + judge, verify judge sees full history, verify convergence terminates, verify CollectAll accumulates, verify judge/convergence mutual exclusivity throws.

**SupervisorBuilder stateRenderer:** Verify stateRenderer flows through to LlmSelectedRouting prompt. Verify `stateRenderer()` then `route(custom)` uses custom. Verify `route(custom)` then `stateRenderer()` uses LlmSelectedRouting.

**DebateBuilder interaction ordering:** Verify `convergence().maxRounds().judge()` works (maxRounds clears the flag). Verify `convergence().judge()` throws ISE. Verify `maxRounds().judge()` works.

**HTN end-to-end:** Static task tree, verify sequential execution order, verify guard-based method selection with initial state.

**EventLogListener:** Verify all event types recorded with correct payloads, verify reason flows from Selected into payload.

**LedgerExecutionListener:** Verify full decision-dispatch-result chain recording (routing, activation, dispatch, result, termination, failure, completion), verify actorId and actorRole on every entry.

## Deferred Items

| Item | Issue | Reason |
|------|-------|--------|
| Runtime HTN re-decomposition | blocks#13 | Requires decomposition phase in driver loop — different execution flow |
| `AgentCapability.description` in prompts | eidos#77 | Field doesn't exist yet in eidos-api |
| `CaseHubEventType` orchestration values | engine#626 | Cross-repo extraction to engine-api |
| `LedgerAppender` in ledger-api | ledger#168 | Cross-repo extraction |
| `MetricsListener` (OpenTelemetry) | blocks#21 | Third listener from prior spec, not in scope for #12 |
| ExternalAgent identity in listener callbacks | — | `agentName()` uses `identityHashCode` as interim distinguisher; proper fix is carrying `RoutingCandidate` identity through dispatch/result callbacks |
