# Design: ActivationContext, MetricsListener, Routing Hardening

**Issues:** #7, #21, #31
**Branch:** `issue-7-activation-context-metrics-hardening`
**Date:** 2026-07-05

---

## 1. ActivationContext Type-Tightening (#7)

### Problem

`ActivationContext.lastAggregationResult` is typed `Optional<Object>` but only ever
holds `AggregationResult`. The loose type loses information and forces consumers to
downcast. The implementation in `AbstractExecutionDriver` is already correct (tracking
maps, count maintenance, aggregation carry-forward) — this was fixed during
`AbstractExecutionDriver` extraction (#6). What remains is the type fix and missing
test coverage.

### Changes

**`ActivationContext<T>` record:**

```java
public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<AggregationResult> lastAggregationResult,  // was Optional<Object>
        int consecutiveIdleActivations
) {}
```

**`AbstractExecutionDriver` field:**

```java
protected AggregationResult lastAggregationResult = null;  // was Object
```

Construction site (`dispatchAgents`, line 156) already passes
`Optional.ofNullable(lastAggregationResult)` — no change needed.

### Impact

Zero external consumers. Only two `ActivationRule` implementations exist
(`MaxIterationsGuard`, `OnExplicitDispatch`), both in blocks, neither reads
`lastAggregationResult()`.

### Tests

1. `lastAggregationResult` is `Optional.empty()` on first iteration, carries forward
   the `AggregationResult` from the previous iteration's aggregation on subsequent
   iterations.
2. `consecutiveIdleActivations` increments when activation returns false, resets to 0
   when activation returns true.
3. Both fields work correctly across multiple agents in the same iteration (per-agent
   tracking verified).

---

## 2. AgentResult.duration Fix

### Problem

`AgentResult` has a `duration` field, but all static factories hardcode
`Duration.ZERO`. The driver's `invokeAgent()` doesn't time invocations. Every consumer
sees `Duration.ZERO` — including `EventLogListener.onAgentResult` which already logs
`durationMs`.

### Change

`AbstractExecutionDriver.invokeAgent()` wraps the invocation with timing and
reconstructs the result:

```java
protected AgentResult invokeAgent(ExecutionModel<T> model, AgentRef agent, T context) {
    transition(model, new ExecutionState.WaitingForAgent(agent));
    var start = Instant.now();
    try {
        var result = invoker.invoke(agent, context).await().indefinitely();
        var elapsed = Duration.between(start, Instant.now());
        return new AgentResult(result.agent(), result.output(), elapsed, result.status());
    } catch (Exception e) {
        var elapsed = Duration.between(start, Instant.now());
        LOG.log(System.Logger.Level.WARNING, "Agent invocation failed", e);
        notifyFailure(model, agent, e);
        return new AgentResult(agent, e.getMessage(), elapsed, AgentResult.AgentResultStatus.FAILURE);
    }
}
```

### Rationale

The driver owns the dispatch lifecycle. Timing at the invoker layer would require every
`AgentInvoker` implementation to add timing — duplicated concern. The driver wraps once,
duration becomes authoritative for all listeners and consumers.

### Test

Verify `AgentResult.duration()` is non-zero after execution with a delayed agent
(e.g., `CompletableFuture.supplyAsync` with a brief sleep).

---

## 3. MetricsListener (#21)

### Design Decision: OTel Meter, not a custom sink

EventLogListener and LedgerExecutionListener use custom sinks because their destination
formats are application-specific (EventLog schema, compliance ledger schema). Metrics
are standardized — OTel API is designed for library instrumentation, includes built-in
no-ops, and is already transitively on the classpath via Quarkus scheduler dependencies.

A custom `MetricsSink` would force every consumer to write an identical OTel bridge.
Direct `Meter` injection eliminates that boilerplate.

### Dependency

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <scope>provided</scope>
</dependency>
```

Version managed by Quarkus BOM (1.57.0 as of Quarkus 3.32.2). No version tag needed.

### Class

`io.casehub.blocks.agentic.listener.MetricsListener implements ExecutionEventListener`

Constructor takes `io.opentelemetry.api.metrics.Meter`. Instruments created at
construction time.

### Instruments

| Instrument | Type | Name | Unit | Attributes | Source |
|------------|------|------|------|------------|--------|
| Agent dispatch latency | Histogram | `casehub.agentic.agent.duration` | `s` | `agent`, `status` | `onAgentResult` — `result.duration()` in seconds |
| Routing decisions | Counter | `casehub.agentic.routing.decisions` | — | `decision_type` | `onRoutingDecision` |
| Activation evaluations | Counter | `casehub.agentic.activation.evaluations` | — | `agent`, `activated` | `onActivation` |
| Agent failures | Counter | `casehub.agentic.agent.failures` | — | `agent` | `onFailure` |
| Execution duration | Histogram | `casehub.agentic.execution.duration` | `s` | `outcome` | `onExecutionComplete` — `executionDuration` in seconds |
| Iteration count | Histogram | `casehub.agentic.execution.iterations` | `{iteration}` | `outcome` | `onExecutionComplete` — from `iterationCount` parameter |

All duration histograms use seconds per OTel semantic conventions. Recording
preserves sub-second precision: `duration.toMillis() / 1000.0`.

### Lifecycle Callback Enrichment

`ExecutionEventListener.onExecutionComplete` signature change:

```java
default void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                 int iterationCount) {}
```

The driver tracks `Instant executionStart` (set in `execute()` before `notifyExecutionStart`)
and `int iterationCount` (incremented in `executeIteration()`). Both are reset at the start
of each `execute()` call. The driver computes duration and passes both values through the
callback, eliminating the need for listener-side state.

EventLogListener and LedgerExecutionListener updated to accept the enriched signature.

### Attribute Values

| Attribute | Values | Derivation |
|-----------|--------|------------|
| `decision_type` | `Selected`, `Unresolvable`, `Escalate` | `decision.getClass().getSimpleName()` |
| `outcome` | `Completed`, `Failed`, `Escalated`, `Cancelled` | `result.getClass().getSimpleName()` |
| `status` | `SUCCESS`, `FAILURE`, `TIMEOUT`, `DECLINED` | `result.status().name()` |
| `agent` | worker name / `channel:{id}` / `human` / `composed` / `external:{hex}` | `ExecutionEventListener.agentName()` |
| `activated` | `true`, `false` | `String.valueOf(boolean)` |

Consistent with EventLogListener's existing attribute production (`onRoutingDecision` uses
`decision.getClass().getSimpleName()`, `onAgentResult` uses `result.status().name()`).

### Metric Semantics

`casehub.agentic.agent.failures` and `casehub.agentic.agent.duration{status=FAILURE}` both
fire for a single failed invocation when the failure originates from an exception. The two
instruments serve different roles:

- `agent.failures` (from `onFailure`): counts invocation exceptions. Only fires when
  `invoker.invoke()` throws.
- `agent.duration{status=FAILURE}` (from `onAgentResult`): records duration for all
  failure-status results — includes both exceptions-turned-to-failures AND invoker-returned
  FAILURE results.

`agent.failures` count ≤ `agent.duration{status=FAILURE}` count. Dashboard builders should
not treat `agent.failures` as disjoint from `agent.duration{status=FAILURE}`.

### Thread Safety

MetricsListener is stateless — no mutable instance fields. `Meter`, `Counter`, and
`Histogram` from OTel API are thread-safe by design. Execution lifecycle data (duration,
iteration count) is passed through the `onExecutionComplete` callback from the driver,
which owns the per-execution state. A single MetricsListener instance can safely be shared
across concurrent executions.

### Testing

- Basic tests: construct with `OpenTelemetry.noop().getMeter("test")` — verifies no
  exceptions.
- Verification tests: add `opentelemetry-sdk-testing` as test scope dependency.
  Use `SdkMeterProvider` + `InMemoryMetricReader` to assert recorded metric values,
  attribute correctness, and instrument types.

---

## 4. AI Routing Strategy Hardening (#31)

### 4a. Config injection for CBR parameters

Add `@ConfigProperty` parameters to `CbrAgentRoutingStrategy`'s `@Inject` constructor:

```java
@Inject
public CbrAgentRoutingStrategy(
    @ConfigProperty(name = "casehub.blocks.cbr.top-k", defaultValue = "10") int topK,
    @ConfigProperty(name = "casehub.blocks.cbr.min-similarity",
                    defaultValue = "0.5") double minSimilarity,
    Instance<CbrCaseMemoryStore> cbrStore,
    Instance<AgentGraphQuery> graphQuery,
    Instance<TrustCandidateClassifier> classifier,
    Instance<TrustScoreSource> scoreSource,
    Instance<TrustRoutingPolicyProvider> policyProvider) {
```

Store as final fields. Apply in `tryCbrStore()`:

```java
CbrQuery query = CbrQuery.of(context.tenancyId(),
    new MemoryDomain(context.capabilityName()),
    "agent-routing", Map.of(), topK)
    .withMinSimilarity(minSimilarity);
```

Remove `DEFAULT_TOP_K` constant. Tests construct directly with explicit values.

**Behavioral change:** the default `minSimilarity` moves from `0.0` (implicit via
`CbrQuery.of()`) to `0.5`. The previous `0.0` accepted all CBR results regardless of
similarity, passing irrelevant cases into success rate analysis. The new `0.5` default
filters low-quality matches. Operators who need lower thresholds can configure
`casehub.blocks.cbr.min-similarity` explicitly.

### 4b. Parser hardening — Jackson replaces manual indexOf

Replace `LlmRoutingSupport.extractAgentName()` with Jackson `ObjectMapper`. Jackson is
already on the classpath (`AgentRoutingContext.caseContext` is a `JsonNode`).

```java
private static final ObjectMapper MAPPER = new ObjectMapper();

static @Nullable String extractAgentName(@Nullable String text) {
    if (text == null) return null;
    var trimmed = text.trim();
    // Strip markdown code fences if present
    if (trimmed.startsWith("```")) {
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start + 1, end).trim();
        }
    }
    try {
        var node = MAPPER.readTree(trimmed);
        var agentNode = node.get("agent");
        return agentNode != null && agentNode.isTextual() ? agentNode.asText() : null;
    } catch (Exception e) {
        return null;
    }
}
```

Handles: markdown-wrapped responses, escaped quotes, extra whitespace, malformed JSON.

### 4c. NullNode case context filtering

`NullNode.toString()` returns `"null"` which passes `!isBlank()` and gets sent as
literal text to the LLM/CBR. Fix in both strategies using Jackson's `isNull()`:

```java
String caseContextSummary = context.caseContext() != null
    && !context.caseContext().isNull()
    ? context.caseContext().toString()
    : null;
```

### 4d. Extract shared trust classification

Both strategies contain ~35 identical lines: classify → bootstrap guard → filter
eligible → empty-pool delegation. Extract to package-private utility.

Rename `LlmRoutingSupport` → `RoutingSupport` (it already contains shared prompt and
parser logic; now trust filtering too).

```java
sealed interface TrustFilterOutcome {
    record Proceed(List<AgentCandidate> eligible,
                   @Nullable List<ClassifiedCandidate> classified)
            implements TrustFilterOutcome {}
    record Decided(AgentAssignment assignment)
            implements TrustFilterOutcome {}
}

static TrustFilterOutcome applyTrustFilter(
        @Nullable TrustCandidateClassifier classifier,
        @Nullable TrustScoreSource scoreSource,
        @Nullable TrustRoutingPolicyProvider policyProvider,
        AgentRoutingContext context,
        List<AgentCandidate> candidates) { ... }
```

Each strategy calls `applyTrustFilter()` and switches on the result:
- `Decided` → return the assignment directly (escalation or classifier decision)
- `Proceed` → continue with strategy-specific selection using `eligible` list

When strategy-specific selection fails — no CBR/graph result, LLM provider failure
(`response == null`), or LLM response unparseable/unknown agent (`workerId == null`) —
and `proceed.classified()` is non-null, both strategies fall back to
`classifier.decide()` before returning unresolvable. The LLM strategy consolidates
both null checks into a single fallback path, with the `ScoredCandidate` reason string
distinguishing the cause ("LLM invocation failed" vs "LLM response unparseable"). This
ensures the classifier's trust context is the last resort — neither provider
unavailability nor LLM hallucination should discard trust information that could
resolve routing.

### 4e. Test cases

| Test | Strategy | Coverage |
|------|----------|----------|
| QUALIFIED + BORDERLINE pool | Both | BORDERLINE filtered, QUALIFIED passes to selection |
| QUALIFIED + BOOTSTRAP, guard ON | Both | BOOTSTRAP filtered, QUALIFIED selected |
| QUALIFIED + BOOTSTRAP, guard OFF | Both | Both pass through to selection |
| NullNode case context | LLM | Context not sent as string "null" |
| NullNode case context | CBR | Query built without problem text |
| Empty TextDelta stream | LLM | Empty string handled as unresolvable |
| Markdown-wrapped JSON response | LLM | Parser extracts through code fences |
| minSimilarity filtering | CBR | Sub-threshold results produce no match |
| Trust-filtered + unparseable LLM | LLM | Falls back to `classifier.decide()`, not unresolvable |
| Trust-filtered + LLM provider failure | LLM | Falls back to `classifier.decide()`, not unresolvable |

---

## Dependency Changes

| Dependency | Scope | Reason |
|------------|-------|--------|
| `io.opentelemetry:opentelemetry-api` | provided | MetricsListener OTel instrumentation |
| `io.opentelemetry:opentelemetry-sdk-testing` | test | Metric value verification tests |

No new compile-scope dependencies. Both provided/test additions are already transitively
available via Quarkus BOM.

---

## Files Changed

| File | Change |
|------|--------|
| `pom.xml` | Add OTel dependencies |
| `ActivationContext.java` | Type-tighten `lastAggregationResult` |
| `AbstractExecutionDriver.java` | Field type fix, invokeAgent timing, execution lifecycle tracking |
| `ExecutionEventListener.java` | Enrich `onExecutionComplete` with `Duration` and `int iterationCount` |
| `EventLogListener.java` | Update `onExecutionComplete` signature |
| `LedgerExecutionListener.java` | Update `onExecutionComplete` signature |
| `MetricsListener.java` | **New** — stateless OTel metrics listener |
| `LlmRoutingSupport.java` → `RoutingSupport.java` | Rename, add trust filter extraction, Jackson parser |
| `LlmAgentRoutingStrategy.java` | Use RoutingSupport for trust filter + NullNode fix |
| `CbrAgentRoutingStrategy.java` | Config injection, use RoutingSupport for trust filter + NullNode fix |
| `AbstractExecutionDriverTest.java` | Tests for lastAggregationResult, consecutiveIdleActivations, duration |
| `MetricsListenerTest.java` | **New** — OTel metric verification tests |
| `LlmAgentRoutingStrategyTest.java` | Mixed-pool, NullNode, empty stream, markdown tests |
| `CbrAgentRoutingStrategyTest.java` | Mixed-pool, NullNode, minSimilarity tests |
| `LlmRoutingSupportTest.java` → `RoutingSupportTest.java` | Rename, add trust filter + parser unit tests |

---

## Out of Scope

- Event-bus integration for ChoreographedDriver (#19) — `ActivationContext.event` stays `Object`
- LlmDecomposition (#13) — separate issue, different SPI
- Parallel agent dispatch timing — current single-threaded loop is sufficient
