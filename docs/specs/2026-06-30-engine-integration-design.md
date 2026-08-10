# Engine Integration & Channel Coordination Design

**Issues:** blocks#14 (engine integration), blocks#15 (channel coordination), blocks#6 (driver extraction)
**Parent:** blocks#10 (supervisor mode epic)

## Context

The agentic orchestration framework has five compositional SPIs (routing, decomposition, activation, aggregation, termination), two execution drivers, and eight pattern builders. Currently only `AgentRef.ExternalAgent` dispatches — the other four variants (WorkerAgent, ChannelAgent, HumanAgent, ComposedAgent) return "unsupported." This design adds real platform dispatch, stage gating, and channel coordination.

### Architectural Position

Blocks is a compositional incubation layer **above** the engine. Its drivers are an alternative execution backend — when the engine dispatches a supervisor worker, that worker contains a blocks ExecutionModel and runs its own loop. The driver dispatches sub-agents **directly to platform primitives**, not through the engine's event-driven pipeline. The engine's pipeline handles case-level concerns (PlanItems, context changes, distributed coordination); the supervisor's internal dispatch doesn't need those.

Resilience infrastructure (DeadLetterQueue, PoisonPillDetector) belongs at the engine level, not in blocks. If a supervisor's sub-agent fails, the LLM-driven orchestration loop observes the failure through aggregation and termination and decides what to do — retry the same agent with different input, pick a different agent, or give up. Mechanical quarantine and dead-lettering are infrastructure concerns managed by the engine's worker scheduling pipeline before candidates reach the supervisor.

## Design

### 1. AbstractExecutionDriver<T>

Extract common logic from OrchestratedDriver and ChoreographedDriver.

**Extracted into base class:**
- Five-phase loop body: route → activate → invoke → aggregate → terminate
- State transition management (`transition()` + listener notification)
- FailurePolicy application (routing failure → FAIL/RETRY_BROADER/ESCALATE)
- Agent invocation via `invoker.invoke(agent, state)` (invoker from constructor)
- ActivationContext construction from tracked state
- Cancellation support

**Tracked state (new):**
- `Map<AgentRef, Integer> activationCounts` — per-agent activation count this execution
- `Map<AgentRef, Integer> consecutiveIdleCounts` — consecutive iterations where agent wasn't activated
- `Object lastAggregationResult` — previous iteration's aggregation result

These populate ActivationContext fields that are currently hardcoded to zero:
```java
// Before: new ActivationContext<>(null, context, agent, 0, Optional.empty(), 0)
// After:  new ActivationContext<>(null, context, agent, activationCounts.getOrDefault(agent, 0),
//             Optional.ofNullable(lastAggregationResult), consecutiveIdleCounts.getOrDefault(agent, 0))
```

**Execution lifecycle:** Calls `onExecutionStart(model)` before the loop and `onExecutionComplete(result)` in a finally block on all listeners.

**Subclasses become thin (~20 lines each):**
- OrchestratedDriver: imperative `while (!cancelled)` loop
- ChoreographedDriver: same loop body, transitions to `WaitingForEvent` between iterations

### 2. AgentInvoker<T>

Functional interface for agent dispatch:

```java
@FunctionalInterface
public interface AgentInvoker<T> {
    Uni<AgentResult> invoke(AgentRef agent, T state);
}
```

Generic over T — consistent with the five SPIs. Returns `Uni<AgentResult>` — this is load-bearing, not aspirational. ChannelAgent and HumanAgent dispatch complete asynchronously (message response observation, work item lifecycle events). The Uni completes when the agent produces a result; the driver chains on it without blocking a thread.

**On the driver, not on ExecutionModel.** AgentInvoker is an infrastructure concern (HOW agents dispatch to platform primitives), not a compositional concern (WHAT the execution model does). ExecutionModel remains a pure composition record — five SPIs, candidate supplier, failure policy, and listeners. The invoker belongs on AbstractExecutionDriver, passed via constructor:

```java
public abstract class AbstractExecutionDriver<T> implements ExecutionDriver<T> {
    private final AgentInvoker<T> invoker;

    protected AbstractExecutionDriver(AgentInvoker<T> invoker) {
        this.invoker = invoker;
    }
}
```

ExecutionModel is unchanged — no new fields.

**Default invoker:** When no invoker is provided (null/overload), AbstractExecutionDriver uses a default that handles ExternalAgent via its CompletionStage function and returns failure for other variants. All existing pattern builder tests work without change.

**ComposedAgent recursion:** When the driver dispatches a ComposedAgent, it creates a child driver with the same invoker — natural inheritance via constructor, no model wiring needed.

### 3. PlatformAgentInvoker<T>

Per-variant dispatch to platform services. All dependencies are API-tier types — blocks does not reference foundation runtime modules.

```java
public class PlatformAgentInvoker<T> implements AgentInvoker<T> {
    private final WorkerRuntime workerRuntime;
    private final MessageDispatcher messageDispatcher;
    private final CorrelationObserver correlationObserver;
    private final WorkItemLifecycle workItemLifecycle;
    private final WorkItemObserver workItemObserver;
    private final Function<T, Map<String, Object>> inputMapper;
    private final String supervisorInstanceId;

    // Per-supervisor tracking for scoped cancellation
    private final Set<String> activeCorrelationIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeWorkItemIds = ConcurrentHashMap.newKeySet();
}
```

**Dependency tier compliance:**
- `WorkerRuntime` — `casehub-engine-api` (compile)
- `MessageDispatcher` — `casehub-qhorus-api` (proposed SPI, see §New Dependencies)
- `CorrelationObserver` — blocks-local CDI bean implementing `MessageObserver` (qhorus-api)
- `WorkItemLifecycle` — `casehub-work-api` (compile)
- `WorkItemObserver` — `casehub-work-api` (proposed SPI, see §New Dependencies)

**Dispatch per variant:**

| Variant | Mechanism |
|---------|-----------|
| ExternalAgent | `ext.fn().apply(state)` → join on CompletionStage → AgentResult |
| WorkerAgent | `workerRuntime.execute(worker.function(), inputMapper.apply(state))` → map WorkerResult to AgentResult |
| ChannelAgent | Post COMMAND via messageDispatcher, observe response via MessageObserver → AgentResult |
| HumanAgent | Create work item via workItemLifecycle, observe completion via lifecycle events → AgentResult |
| ComposedAgent | Create child driver (inherits this invoker), execute nested model with current state → map ExecutionResult to AgentResult |

**WorkerAgent dispatch — WorkerRuntime, not WorkerExecutionManager.** The supervisor IS a worker dispatched by WorkerExecutionManager at the engine level. Its sub-agents are internal — they don't need separate PlanItems, Quartz scheduling, or engine EventLog entries. WorkerRuntime (`casehub-engine-api`) provides synchronous in-process execution, which is the correct abstraction for supervisor-internal dispatch. WorkerExecutionManager (`casehub-engine-common`) takes engine-internal types (CaseInstance, EventLog, Worker, Capability) that blocks cannot and should not reference. Sub-agent execution is audited via ExecutionEventListener (§5) — specifically, `EventLogListener` and `LedgerExecutionListener` (blocks#12) write persistent audit records for routing decisions, agent results, and terminations. PlatformAgentInvoker wires these listeners by default when operating within an engine case. See §Persistent Audit Trail.

**ChannelAgent dispatch — multiplexing CDI observer, not per-dispatch registration.**

MessageObserver is CDI-discovered — `MessageObserverDispatcher.dispatch()` iterates `Instance.Handle<MessageObserver>` beans. Dynamic per-dispatch registration/deregistration is not supported. Instead, a single long-lived `CorrelationObserver` CDI bean multiplexes by correlationId:

```java
@ApplicationScoped
public class CorrelationObserver implements MessageObserver {
    private final ConcurrentHashMap<String, CompletableFuture<AgentResult>> pending
        = new ConcurrentHashMap<>();

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.correlationId() == null) return;
        var future = pending.get(event.correlationId());
        if (future != null && isTerminal(event.messageType())) {
            future.complete(mapToAgentResult(event));
            pending.remove(event.correlationId());
        }
    }

    @Override
    public Set<String> channels() {
        return Set.of(); // observe all channels — correlationId filtering is sufficient
    }

    public CompletableFuture<AgentResult> register(String correlationId) {
        var future = new CompletableFuture<AgentResult>();
        pending.put(correlationId, future);
        return future;
    }

    public void cancel(String correlationId) {
        var future = pending.remove(correlationId);
        if (future != null) future.completeExceptionally(new CancellationException());
    }
}
```

**Channel scoping:** `MessageObserver.channels()` filters by channel **name** (String), not UUID. `CorrelationObserver` returns empty set (observe all) because it filters by correlationId — scoping to specific channels would require tracking active channel names and add complexity for no benefit. The ConcurrentHashMap lookup misses cheaply for non-ChannelAgent messages.

**Thread safety:** ConcurrentHashMap is lock-free for reads. `onMessage()` fires post-commit on the qhorus dispatch thread (confirmed: `MessageObserverDispatcher` registers a `TransactionSynchronization` that dispatches in `afterCompletion` when status == COMMITTED). CompletableFuture.complete() is thread-safe.

**Memory cleanup:** Timed-out futures are removed by the timeout handler (which also removes the correlationId from `activeCorrelationIds`). Cancelled futures are removed by `cancel()`. On supervisor cancellation, PlatformAgentInvoker iterates its own `activeCorrelationIds` set and calls `correlationObserver.cancel(id)` for each — scoped to this supervisor only. Same pattern for `activeWorkItemIds` / `workItemObserver.cancel(id)`. No `cancelAll()` on the shared observers — that would cross supervisor boundaries.

**ChannelAgent dispatch flow:**
1. Generate correlationId (UUID string)
2. `activeCorrelationIds.add(correlationId)`
3. `var future = correlationObserver.register(correlationId)`
4. Post MessageDispatch (type=COMMAND, content from state T, correlationId, sender=supervisorInstanceId) via messageDispatcher
5. Return `Uni.createFrom().completionStage(future).ifNoItem().after(timeout).fail()` — default 5 minutes
6. On completion or timeout: `activeCorrelationIds.remove(correlationId)`, `correlationObserver.cancel(correlationId)` (no-op if already completed)

**HumanAgent dispatch — WorkItemObserver SPI (proposed).**

`WorkItemLifecycle` (`io.casehub.work.api.spi`) is write-only: `cancel()` and `complete()`. It has no observation capability. `WorkItemLifecycleEvent` is in `casehub-work` runtime, not `casehub-work-api`. A `WorkItemObserver` SPI is needed in `casehub-work-api`, parallel to MessageObserver in qhorus-api. Filed as casehubio/work issue (see §New Dependencies).

The observer follows the same multiplexing pattern as CorrelationObserver — one CDI bean with a ConcurrentHashMap<UUID, CompletableFuture> keyed by work item ID:

**HumanAgent dispatch flow:**
1. Create work item from template via workItemLifecycle → work item ID
2. `activeWorkItemIds.add(workItemId)`
3. `var future = workItemObserver.register(workItemId)`
4. Return `Uni.createFrom().completionStage(future)` — no timeout by default (human timescales)
5. On work item terminal status (COMPLETED, CANCELLED, EXPIRED): observer completes the future, `activeWorkItemIds.remove(workItemId)`

The Uni<AgentResult> return type enables the driver to block via `.await().indefinitely()` (OrchestratedDriver) or chain reactively (ChoreographedDriver) — see §Thread Model.

**Input mapping:** Configurable `Function<T, Map<String, Object>> inputMapper` replaces ad-hoc type switching. The consumer provides the mapping appropriate for their T. Defaults: `Function.identity()` when T is Map, `CaseContext::getData` when T is CaseContext. ChannelAgent always serialises state T to String. HumanAgent uses the template from the ref. ComposedAgent passes T through.

**Transient error retry — infrastructure level, not resilience delegation.**
Two levels of failure handling exist:
1. **Engine-level resilience** (DeadLetterQueue, PoisonPillDetector, RetryPolicy) — manages the supervisor worker itself. If the entire supervisor fails, the engine's resilience infrastructure handles retry, quarantine, and dead-lettering. This is not reimplemented in blocks.
2. **Infrastructure retry** (2-3 retries, 100-200ms backoff) — handles transient network/infrastructure failures during sub-agent dispatch (connection reset, temporary unavailability). Every RPC has this. It uses FailurePolicy.agentRetry fields. If infrastructure failure persists, returns AgentResult.failure() and the LLM orchestration loop decides next steps.

Semantic agent failures (agent returns error result, agent declines) are NOT retried — they flow through aggregation and termination for the LLM to reason about.

### 4. StageAwareCandidateSupplier

Decorator wrapping `Supplier<List<RoutingCandidate>>`. Mirrors the engine's stage gating (PlanningStrategyLoopControl lines 106-130).

```java
public class StageAwareCandidateSupplier implements Supplier<List<RoutingCandidate>> {
    private final Supplier<List<RoutingCandidate>> delegate;
    private final StageGate stageGate;
    private final Function<AgentRef, String> bindingNameResolver;
}
```

**StageGate — blocks-local interface, not engine's Stage class.** Stage (`io.casehub.blackboard.stage.Stage`) is in `casehub-engine-blackboard`, which blocks does not depend on. StageGate captures only the data blocks needs:

```java
public interface StageGate {
    Set<String> allStagedBindings();
    Set<String> activeStagedBindings();
}
```

The consumer provides a StageGate implementation. Inside an engine case: reads from CasePlanModel's Stage objects, collecting binding names and filtering by StageStatus.ACTIVE. Outside: not used (consumers don't wrap with this decorator).

**Filtering logic:**
1. `stageGate.allStagedBindings()` → `allStagedNames`
2. `stageGate.activeStagedBindings()` → `activeStagedNames`
3. Free-floating candidates (binding name not in `allStagedNames`) → always pass
4. Staged candidates (binding name in `allStagedNames`) → pass only if in `activeStagedNames`

**bindingNameResolver:** Maps AgentRef → binding name. Consumer-provided (domain-specific). Default: `worker.name()` for WorkerAgent, `channelId.toString()` for ChannelAgent.

Does not evaluate stage entry/exit conditions or manage stage lifecycle — that remains the engine's StageLifecycleEvaluator's job.

### 5. ExecutionEventListener Extensions

Two new default methods:

```java
default void onExecutionStart(ExecutionModel<?> model) {}
default void onExecutionComplete(ExecutionResult result) {}
```

AbstractExecutionDriver calls `onExecutionStart` before the loop and `onExecutionComplete` in a finally block — guaranteed cleanup even on failure or cancellation.

**Primary use:** Channel lifecycle for collaborative patterns. Pattern builders (Debate, Voting) create channels at build time and register a listener that closes channels on execution complete. Channel creation and closure uses `ChannelLifecycle` — a proposed SPI in `casehub-qhorus-api` (same qhorus issue as MessageDispatcher). Currently `ChannelService` is in qhorus runtime; the SPI extracts the create/close contract:

```java
// In a pattern builder (application layer, wired with platform services):
var channel = channelLifecycle.create(request);
listeners.add(new ExecutionEventListener() {
    @Override public void onExecutionComplete(ExecutionResult result) {
        channelLifecycle.close(channel.id());
    }
});
```

No new channel abstraction in blocks — pattern builders handle their own lifecycle through listeners. The ChannelLifecycle SPI is a qhorus concern.

Existing 8 listener methods unchanged.

### 6. FailurePolicy Enrichment

One new field on AgentRetryPolicy:

```java
public record AgentRetryPolicy(
    int maxRetries,
    Duration backoff,
    BackoffStrategy backoffStrategy,
    AgentFailureAction onExhausted
)
```

BackoffStrategy defined locally in blocks (`io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy`): FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER. Three enum values — no cross-repo dependency justified per PLATFORM.md scope rules.

`defaults()` factory sets FIXED — backward compatible. PlatformAgentInvoker's infrastructure retry uses these fields. Backoff calculation is ~15 lines inline.

RoutingFailureAction and AggregationFailureAction unchanged.

## New Dependencies

No new compile dependencies on blocks. All types used by PlatformAgentInvoker are in existing compile dependencies (`casehub-engine-api`, `casehub-qhorus-api`, `casehub-work-api`).

**Prerequisites — API-level SPI extractions:**

| SPI | Current Runtime Type | Target Module | Issue |
|-----|---------------------|---------------|-------|
| `MessageDispatcher` | `MessageService.dispatch()` | `casehub-qhorus-api` | qhorus#315 |
| `ChannelLifecycle` | `ChannelService.create()/close()` | `casehub-qhorus-api` | qhorus#315 |
| `WorkItemObserver` | `WorkItemLifecycleEmitter` | `casehub-work-api` | work#285 |

Qhorus SPIs: blocks needs dispatch and channel lifecycle as API-level types. Filed as casehubio/qhorus#315.
Work SPI: blocks needs work item lifecycle observation for HumanAgent event-driven dispatch. Filed as casehubio/work issue.

## Type Summary

**New types:**

| Type | Package | Purpose |
|------|---------|---------|
| `AbstractExecutionDriver<T>` | `agentic.model` | Common loop, state tracking, listener dispatch; accepts AgentInvoker via constructor |
| `AgentInvoker<T>` | `agentic.model` | Functional interface for dispatch |
| `PlatformAgentInvoker<T>` | `agentic.model` | Per-variant dispatch to platform services (API-tier deps only) |
| `StageAwareCandidateSupplier` | `agentic.routing` | Filter candidates by active stage bindings |
| `StageGate` | `agentic.routing` | Interface: `allStagedBindings()` + `activeStagedBindings()` |

**Modified types:**

| Type | Change |
|------|--------|
| `FailurePolicy.AgentRetryPolicy` | +1 field: `BackoffStrategy backoffStrategy` (locally defined enum) |
| `ExecutionEventListener` | +2 default methods: `onExecutionStart()`, `onExecutionComplete()` |
| `OrchestratedDriver` | Thin subclass of AbstractExecutionDriver |
| `ChoreographedDriver` | Thin subclass of AbstractExecutionDriver |

**Unchanged:**
- Five SPI interfaces (RoutingStrategy, DecompositionStrategy, ActivationRule, AggregationStrategy, TerminationCondition)
- Seven sealed types (AgentRef, RoutingDecision, TaskNode, AggregationResult, TerminationDecision, ExecutionResult, ExecutionState)
- All context records (RoutingContext, ActivationContext, AggregationContext, TerminationContext, DecompositionContext)
- RoutingCandidate
- All existing SPI implementations
- All pattern builder tests (15+ methods)

## Epic Completeness

Parent epic blocks#10 has five children. This spec covers:

| # | Title | Addressed | Notes |
|---|-------|-----------|-------|
| #6 | Driver extraction | Yes — §1 AbstractExecutionDriver | |
| #11 | Routing enrichment | No | Separate spec — state-aware prompts, capability descriptions |
| #12 | Routing accountability | No | Separate spec — EventLogListener, LedgerExecutionListener |
| #13 | LLM decomposition | No | Separate spec — LlmDecomposition strategy |
| #14 | Engine integration | Yes — §§1-6 | Stage gating, real agent dispatch, resilience clarification |
| #15 | Channel coordination | Partial — ChannelAgent dispatch | Channel setup between agents mid-execution deferred (blocks#20) |

Issues #11, #12, #13 are out of scope — each is a separate concern with its own implementation. Issue #7 (ActivationContext population) is subsumed by §1 tracked state.

## Thread Model and Loop Design

### OrchestratedDriver — imperative loop, blocking await

OrchestratedDriver stays imperative. The loop structure is unchanged from the current code — `Uni.createFrom().item(() -> { while loop })`. All SPI calls and agent invocations use `.await().indefinitely()`. The driver thread blocks during each await.

```java
// OrchestratedDriver — thin subclass (~20 lines)
@Override
public Uni<ExecutionResult> execute(ExecutionModel<T> model, T context) {
    return Uni.createFrom().item(() -> {
        notifyExecutionStart(model);
        try {
            while (!cancelled) {
                var result = executeIteration(model, context);
                if (result != null) return result;
            }
            return new ExecutionResult.Cancelled();
        } finally {
            notifyExecutionComplete(result);
        }
    });
}
```

The base class provides `executeIteration()` — the five-phase body:

```java
// AbstractExecutionDriver.executeIteration() — returns null for Continue
protected ExecutionResult executeIteration(ExecutionModel<T> model, T context) {
    var candidates = model.candidateSupplier().get();
    var decision = model.routing().route(routingCtx).await().indefinitely();

    if (decision instanceof Selected selected) {
        // Activate and dispatch — concurrent for multi-agent
        var unis = new ArrayList<Uni<AgentResult>>();
        for (var agent : selected.agents()) {
            var activated = model.activation()
                .shouldActivate(actCtx).await().indefinitely();
            if (activated) unis.add(invoker.invoke(agent, context));
        }
        // Concurrent dispatch: all agent Unis run in parallel
        var results = Uni.join().all(unis).andCollectFailures()
            .await().indefinitely();

        model.aggregation().aggregate(results, aggCtx).await().indefinitely();
    }
    // ... routing failure handling ...

    var termDecision = model.termination().evaluate(termCtx).await().indefinitely();
    // Return terminal result or null for Continue
}
```

**The five SPIs remain Uni-returning and unchanged.** The current code already calls `.await().indefinitely()` on each one. AgentInvoker.invoke() is new but follows the same pattern.

**Blocking behaviour by agent variant:**
- ExternalAgent: `.await()` completes immediately (in-process CompletionStage)
- WorkerAgent: `.await()` completes immediately (in-process WorkerRuntime.execute())
- ChannelAgent: `.await()` blocks until CorrelationObserver completes the future (seconds to minutes)
- HumanAgent: `.await()` blocks until WorkItemObserver completes the future (hours to days)
- ComposedAgent: `.await()` blocks for the duration of the nested execution

**Why blocking is acceptable:** The supervisor IS a long-running worker dispatched via Quartz. Its thread is dedicated for the supervisor's lifetime. Blocking during sub-agent invocation is the same as any long-running worker function. If many supervisors block concurrently for human agents, the Quartz thread pool needs sizing — that is a deployment concern, not an architectural one.

**Concurrent multi-agent dispatch:** When routing returns `Selected(List.of(a, b, c))`, activated agents' Uni results are joined via `Uni.join().all()`. The three agent invocations run concurrently (CorrelationObserver callbacks fire on the qhorus dispatch thread, completing CompletableFutures independently). The driver thread blocks on the combined Uni until all agents complete.

### ChoreographedDriver — reactive, event-driven

ChoreographedDriver is genuinely reactive — it waits for events between iterations using Uni chaining, not blocking. This is unchanged by this spec; the driver's existing event-wait mechanism handles async agent dispatch naturally.

### Cancellation

`cancel()` sets the cancelled flag, then iterates this supervisor's `activeCorrelationIds` and `activeWorkItemIds`, calling `correlationObserver.cancel(id)` and `workItemObserver.cancel(id)` for each. Only this supervisor's pending futures are cancelled — other concurrent supervisors are unaffected. The per-ID `cancel()` completes the CompletableFuture with CancellationException; the blocking `.await()` throws, breaking the loop. Both tracking sets are cleared after iteration.

## Persistent Audit Trail

Sub-agent dispatch via WorkerRuntime produces no engine-level EventLog entry — WorkerRuntime's Javadoc explicitly states "no external scheduling, no ledger trace." ExecutionEventListener is an in-memory callback interface. Without a persistent listener, sub-agent dispatch history is lost if the supervisor crashes.

**Resolution:** The prior agentic orchestration spec defines three built-in listeners:

| Listener | Target | What It Captures |
|----------|--------|-----------------|
| `EventLogListener` | Engine EventLog | Routing decisions, agent results, terminations |
| `LedgerExecutionListener` | casehub-ledger | Compliance-grade audit (EU AI Act Art.12) |
| `MetricsListener` | OpenTelemetry | Latency, distribution, failure rates |

These are in-scope for blocks#12 (routing accountability). `EventLogListener` writes to the engine's EventLog — this IS a persistent record of which sub-agents were dispatched, what inputs they received, what results they returned, and what failed.

PlatformAgentInvoker wires `EventLogListener` by default when operating within an engine case (EventLog is available from the case context). Outside an engine case (standalone mode), the consumer chooses whether to wire audit listeners.

This is a conscious design: the audit trail is a listener concern (blocks#12), not an invoker concern (this spec). The invoker dispatches; listeners observe and persist. The separation is clean — different deployments wire different listeners based on their audit requirements.

## Testing Strategy

**AbstractExecutionDriver:** Extract tests from OrchestratedDriverTest — loop behavior, state transitions, cancellation, listener notification, ActivationContext population. Both thin subclasses get minimal tests verifying their loop trigger semantics.

**AgentInvoker / PlatformAgentInvoker:** Per-variant dispatch tests:
- WorkerAgent: mock WorkerRuntime, verify execute() called with inputMapper output, verify WorkerResult → AgentResult mapping
- ChannelAgent: mock MessageDispatcher, verify COMMAND posted with correlationId; simulate MessageObserver callback with RESPONSE, verify Uni completes with AgentResult; verify timeout produces failure
- HumanAgent: mock WorkItemLifecycle, verify work item created from template; simulate lifecycle completion event, verify Uni completes with AgentResult
- ComposedAgent: verify child driver inherits invoker, executes with state passthrough
- ExternalAgent: existing test pattern (CompletionStage lambda)
- Transient retry: verify 2-3 retries on transient failure, verify failure surfaced after retries exhausted

**StageAwareCandidateSupplier:** Unit tests with StageGate (plain Java, no CDI):
- Free-floating candidates pass regardless of stage state
- Staged candidates filtered when binding not in activeStagedBindings
- Staged candidates pass when binding in activeStagedBindings
- Mixed free-floating and staged candidates filtered correctly

**ExecutionEventListener lifecycle:** Verify onExecutionStart called before loop, onExecutionComplete called in finally (including on exception and cancellation).

**Integration tests:** End-to-end via pattern builders with PlatformAgentInvoker wired to real (in-memory) platform services from test dependencies (casehub-engine-testing, casehub-qhorus-testing).
