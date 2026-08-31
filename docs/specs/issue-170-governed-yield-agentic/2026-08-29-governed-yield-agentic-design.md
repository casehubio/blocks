# Governed Yield — Orchestration Layer for Judgment Patterns

**Issue:** blocks#170 (epic), children #171, #172, #173
**Date:** 2026-08-29 (revised 2026-08-31)
**Status:** Design

## Overview

Slot 160 landed the minimal judgment loop: `JudgmentPhase<T>` SPI, `JudgmentDecision` sealed type, driver wiring at phase 3.5, and `LlmJudgmentPhase` in engine-adapter. This spec builds the **orchestration layer** on top — conditional triggering, multi-party dispatch, verification composition, and configurable failure handling.

The core contribution is `JudgmentPolicy<T> implements JudgmentPhase<T>` — a composition root that is a drop-in replacement for any `JudgmentPhase`. It composes triggers, caller strategies, verifiers, and retry policies into a single `evaluate()` call.

## What Already Exists (Landed on Main)

| Type | Package | What it does |
|------|---------|-------------|
| `JudgmentPhase<T>` | `blocks.agentic.judgment` | `@FunctionalInterface`: `JudgmentDecision evaluate(JudgmentContext<T>)` |
| `JudgmentDecision` | `blocks.agentic.judgment` | Sealed: `Approved(result, evidence, caller)`, `Rejected(feedback, evidence, caller)`, `Escalated(reason, caller)` |
| `JudgmentContext<T>` | `blocks.agentic.judgment` | Record: executionContext, iterationResults, aggregationResult, iteration, previousFeedback |
| `ExecutionModel<T>` | `blocks.agentic.model` | Component 12: `@Nullable JudgmentPhase<T> judgment` |
| `AbstractExecutionDriver` | `blocks.agentic.model` | Phase 3.5: Rejected → store feedback + continue loop; Escalated → terminate |
| `ExecutionEventListener` | `blocks.agentic.model` | `onJudgment(JudgmentDecision)` callback |
| `LlmJudgmentPhase<T>` | `engine.agentic.judgment` (engine-adapter) | ChatModelProvider-backed impl with optional JudgmentVerifier |
| `PatternJudgmentConfig` | `engine.agentic.judgment` (engine-adapter) | Config: prompt, callerConfig, verifierStrategy, evidenceRequirements, mode |
| `SchemaValidationVerifier` | `engine.agentic.judgment` (engine-adapter) | Validates decision against resolutionType |
| `LlmEvaluationVerifier` | `engine.agentic.judgment` (engine-adapter) | LLM-as-judge quality evaluation |
| `JudgmentVerifier` | `engine-api` `api.spi.judgment` | SPI: `VerificationResult verify(VerificationContext)` |
| `VerificationResult` | `engine-api` `api.spi.judgment` | Sealed: Accepted, InsufficientEvidence, TrustTooLow, Rejected |

## What This Spec Adds

| Gap | What we add | Why |
|-----|-------------|-----|
| Judgment always fires | `JudgmentTrigger<T>` — conditional yielding | Most iterations don't need judgment; triggers gate when it fires |
| Single caller only | `CallerStrategy` — Single, FanOut, EscalationChain | Multi-party judgment, M-of-N consensus, escalation chains |
| No agreement protocol | `AgreementPolicy` — reduce N responses to one | Fan-out needs a consensus mechanism before verification |
| No verification composition | `CompositeVerifier` — chain verifiers | Real deployments need evidence + schema + LLM checks together |
| No explicit opt-out | `NoOpVerifier` — always accepts | Mandatory-with-override: verifier required but can be NoOp |
| Retry not configurable | `RetryPolicy` + `VerifierFailurePolicy` | Per-verifier failure handling (retry vs escalate vs fail) |
| No dispatch abstraction | `JudgmentDispatcher` — pluggable dispatch | Standalone (AgentInvoker) vs engine-integrated (JudgmentScheduler) |

## Package: `io.casehub.blocks.agentic.judgment`

### JudgmentPolicy

The composition root. Implements `JudgmentPhase<T>` — drop-in wherever `JudgmentPhase` is accepted.

```java
public class JudgmentPolicy<T> implements JudgmentPhase<T> {

    private final JudgmentTrigger<T> trigger;
    private final CallerStrategy callerStrategy;
    private final JudgmentVerifier verifier;
    private final RetryPolicy retryPolicy;
    private final JudgmentDispatcher dispatcher;
    private final Duration dispatchTimeout;

    @Override
    public JudgmentDecision evaluate(JudgmentContext<T> context) {
        if (!trigger.shouldYield(context)) {
            return new JudgmentDecision.Approved("skipped", List.of(), CallerIdentity.system());
        }
        // dispatch → verify → retry loop
    }
}
```

`dispatchTimeout` controls how long the dispatcher waits for a response before returning `JudgmentDecision.Escalated("judgment timed out")`. Defaults to 5 minutes.

### JudgmentTrigger

Evaluates whether this iteration requires judgment. Stateless, composable.

```java
@FunctionalInterface
public interface JudgmentTrigger<T> {
    boolean shouldYield(JudgmentContext<T> context);
}
```

Provided implementations:

| Trigger | What it does |
|---------|-------------|
| `AlwaysYield` | Every iteration yields. |
| `ConfidenceThreshold` | Yields when a user-supplied extractor (`ToDoubleFunction<JudgmentContext<T>>`) returns below threshold. |
| `IterationBased` | Yields every N iterations. Periodic checkpoint patterns. |
| `NeverYield` | No-op. Judgment wired but disabled. |

Composable via `and()` (both must fire) and `or()` (either fires).

```java
public final class ConfidenceThreshold<T> implements JudgmentTrigger<T> {
    private final double threshold;
    private final ToDoubleFunction<JudgmentContext<T>> extractor;

    public static <T> ConfidenceThreshold<T> below(
            double threshold, ToDoubleFunction<JudgmentContext<T>> extractor) {
        return new ConfidenceThreshold<>(threshold, extractor);
    }

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return extractor.applyAsDouble(context) < threshold;
    }
}
```

### CallerStrategy

Determines who judges. Blocks orchestrates 1–N calls via `JudgmentDispatcher`.

```java
public sealed interface CallerStrategy {
    record Single(CallerRef caller) implements CallerStrategy {}
    record FanOut(List<CallerRef> callers, AgreementPolicy agreementPolicy) implements CallerStrategy {}
    record EscalationChain(List<CallerRef> callers) implements CallerStrategy {}

    static Single single(CallerRef caller) { return new Single(caller); }
    static Single single() { return new Single(CallerRef.agent("default", null)); }
    static FanOut fanOut(List<CallerRef> callers, AgreementPolicy policy) {
        return new FanOut(List.copyOf(callers), policy);
    }
}
```

- `Single(caller)` — one dispatch, one response
- `FanOut(callers, agreementPolicy)` — one dispatch per caller, `AgreementPolicy` reduces N responses to one agreed response before verification
- `EscalationChain(callers)` — try callers in order on escalation

`CallerRef` identifies a caller:

```java
public record CallerRef(
        String id,
        @Nullable String description,
        @Nullable Map<String, Object> routingHints,
        @Nullable AgentRef agentRef) {

    public static CallerRef agent(String id, AgentRef ref) {
        return new CallerRef(id, null, null, ref);
    }
    public static CallerRef human(String id, Map<String, Object> routingHints) {
        return new CallerRef(id, null, routingHints, null);
    }
}
```

### JudgmentDispatcher

Bridges dispatch and response collection. Blocks the virtual thread until a response arrives or timeout expires.

```java
@FunctionalInterface
public interface JudgmentDispatcher {
    JudgmentResponse dispatch(JudgmentDispatchRequest request);
}

public record JudgmentDispatchRequest(
        JudgmentContext<?> context,
        CallerRef caller,
        @Nullable String feedback) {}
```

Two implementations:

| Impl | When | How |
|------|------|-----|
| `DirectJudgmentDispatcher` | Standalone mode (default) | Uses `AgentInvoker.invoke()` — same pattern as `JudgeConvergence`. Maps `AgentResult` → `JudgmentResponse`. |
| `EngineJudgmentDispatcher` | Engine-integrated mode | Calls `JudgmentScheduler.schedule()`, blocks on per-correlation `BlockingQueue`. Mirrors engine's `JudgmentNodeExecutor` pattern. |

`DirectJudgmentDispatcher` response mapping:

| `AgentResult.output()` type | Mapping |
|------------------------------|---------|
| `JudgmentResponse` | Used directly |
| `Map<String, Object>` | `"decision"` → decision, rest → evidence |
| `String` | Used as decision, empty evidence |

### AgreementPolicy

Reduces multiple responses from `CallerStrategy.FanOut` to a single agreed response. Runs BEFORE verification.

```java
@FunctionalInterface
public interface AgreementPolicy {
    AgreementResult evaluate(List<JudgmentResponse> responses);
}

public sealed interface AgreementResult {
    record Agreed(JudgmentResponse selectedResponse) implements AgreementResult {}
    record Disagreed(String reason) implements AgreementResult {}
}
```

`ConsensusAgreement` — M-of-N agreement:

```java
public final class ConsensusAgreement implements AgreementPolicy {
    private final int requiredAgreement;
    private final ConsensusMode mode;

    public enum ConsensusMode { UNANIMOUS, MAJORITY, THRESHOLD }
}
```

On `Disagreed`: retries all N callers with the disagreement reason as feedback, sharing the retry budget with verification failures.

### RetryPolicy

Configurable failure handling when verification rejects a response.

```java
public record RetryPolicy(int maxRetries, ExhaustionPolicy exhaustionPolicy) {

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, ExhaustionPolicy.FAIL);
    }
}

public enum ExhaustionPolicy {
    FAIL,
    ESCALATE_NEXT_CALLER,
    TERMINATE_PATTERN
}
```

- `FAIL` — return `JudgmentDecision.Rejected`
- `ESCALATE_NEXT_CALLER` — move to next caller in `EscalationChain` (falls through to FAIL for Single/FanOut)
- `TERMINATE_PATTERN` — return `JudgmentDecision.Escalated`

### VerifierFailurePolicy

Per-verifier configurable behavior on rejection.

```java
public enum VerifierFailurePolicy {
    RETRY_WITH_FEEDBACK,
    ESCALATE,
    FAIL
}
```

Default is `RETRY_WITH_FEEDBACK`. Used by `CompositeVerifier` — most-restrictive policy wins (FAIL > ESCALATE > RETRY_WITH_FEEDBACK).

#### Decision Matrix: VerifierFailurePolicy × RetryPolicy

| VerifierFailurePolicy | Retry budget remaining? | Action |
|---|---|---|
| RETRY_WITH_FEEDBACK | Yes | Re-dispatch with rejection feedback |
| RETRY_WITH_FEEDBACK | No | Apply ExhaustionPolicy |
| ESCALATE | (ignored) | Apply ExhaustionPolicy immediately |
| FAIL | (ignored) | Return `JudgmentDecision.Rejected` immediately |

## Blocks-Level Verifiers

Compose engine-api's `JudgmentVerifier` SPI. Do NOT duplicate engine verifiers.

### CompositeVerifier

Chains multiple verifiers. All must accept. Most-restrictive failure policy wins.

```java
public final class CompositeVerifier implements JudgmentVerifier {
    private final List<VerifierEntry> entries;

    public static CompositeVerifier of(JudgmentVerifier... verifiers) { ... }
    public static CompositeVerifier of(VerifierEntry... entries) { ... }

    public record VerifierEntry(JudgmentVerifier verifier, VerifierFailurePolicy failurePolicy) {}
}
```

### NoOpVerifier

Explicit opt-out — always returns `Accepted`.

```java
public final class NoOpVerifier implements JudgmentVerifier {
    @Override public String id() { return "noop"; }
    @Override public VerificationResult verify(VerificationContext ctx) {
        return new VerificationResult.Accepted();
    }
}
```

## Usage

`JudgmentPolicy` is a `JudgmentPhase` — it plugs into pattern builders the same way `LlmJudgmentPhase` does:

```java
var reviewer = AgentRef.external("senior-reviewer", ctx -> ...);

Patterns.supervisor()
    .routing(...)
    .termination(...)
    .judgment(JudgmentPolicy.<MyContext>builder()
        .trigger(ConfidenceThreshold.below(0.8, ctx -> extractConfidence(ctx)))
        .caller(CallerStrategy.single(CallerRef.agent("reviewer", reviewer)))
        .verifier(CompositeVerifier.of(
            new VerifierEntry(evidencePresence, RETRY_WITH_FEEDBACK),
            new VerifierEntry(schemaValidation, ESCALATE)))
        .dispatcher(new DirectJudgmentDispatcher(invoker))
        .retryPolicy(RetryPolicy.defaults())
        .dispatchTimeout(Duration.ofMinutes(2))
        .build())
    .build();
```

Simple case (LLM judgment, no orchestration) still uses `LlmJudgmentPhase` directly — no need for `JudgmentPolicy` when you just want a single LLM judge.

## Relationship to Existing Types

| Existing | Relationship |
|----------|-------------|
| `JudgmentPhase<T>` | `JudgmentPolicy` implements it — composition, not replacement |
| `LlmJudgmentPhase` | Standalone LLM judgment. `JudgmentPolicy` wraps a dispatcher that can use `AgentInvoker` (same mechanism) or engine `JudgmentScheduler` |
| `JudgmentDecision` | Returned by `JudgmentPolicy.evaluate()` — same sealed type, no additions |
| `JudgmentContext<T>` | Consumed by `JudgmentTrigger.shouldYield()` and passed to dispatcher — same record |
| `AbstractExecutionDriver` | No changes needed — `JudgmentPolicy` is a `JudgmentPhase`, existing wiring works |
| `ExecutionModel` | No changes needed — component 12 already accepts `JudgmentPhase<T>` |
| `JudgeConvergence` | Orthogonal: termination concern (has the debate converged?) vs quality concern (is this iteration's output good?) |
| `JudgmentVerifier` (engine-api) | Blocks composes via `CompositeVerifier`, doesn't redefine |

## Testing Strategy

Plain JUnit 5 + Mockito, following existing blocks test patterns.

| Test class | What it covers |
|-----------|---------------|
| `JudgmentPolicyTest` | Full evaluate() loop: trigger → dispatch → verify → retry → decision |
| `JudgmentTriggerTest` | Each trigger impl + composition (and/or) |
| `CallerStrategyTest` | Single, FanOut, EscalationChain dispatch orchestration |
| `AgreementPolicyTest` | ConsensusAgreement: unanimous, majority, threshold; disagreement retry |
| `CompositeVerifierTest` | Chain evaluation, most-restrictive-wins, empty chain |
| `NoOpVerifierTest` | Always accepts |
| `DirectJudgmentDispatcherTest` | AgentInvoker-based dispatch, response mapping |
| `RetryPolicyMatrixTest` | All VerifierFailurePolicy × ExhaustionPolicy combinations |

## Extension Points

- `JudgmentTrigger<T>` — custom trigger implementations (risk-based, content-aware)
- `CallerStrategy` — sealed but extensible via composition (CallerRef carries routingHints)
- `AgreementPolicy` — custom agreement logic beyond M-of-N
- `JudgmentDispatcher` — future dispatch mechanisms (A2A, webhook, conversation orchestrator)
- `JudgmentVerifier` (engine-api) — future verifiers (signed cards, temporal properties)

## Dependencies

No new compile dependencies. `JudgmentPhase`, `JudgmentDecision`, `JudgmentContext` are already in blocks core. `JudgmentVerifier`, `VerificationResult` are in engine-api (provided scope). `AgentInvoker` is in blocks core.

## References

- `JudgmentPhase<T>` — blocks `agentic.judgment`, the SPI we implement
- `JudgmentDecision` — blocks `agentic.judgment`, the sealed return type
- `JudgmentContext<T>` — blocks `agentic.judgment`, evaluation context
- `AbstractExecutionDriver.executeIteration()` — blocks, phase 3.5 wiring (unchanged)
- `LlmJudgmentPhase<T>` — engine-adapter, existing LLM impl (complementary, not replaced)
- `JudgmentVerifier` — engine-api `api.spi.judgment` (engine#997)
- `VerificationResult` — engine-api, sealed: Accepted, InsufficientEvidence, TrustTooLow, Rejected
- `JudgeConvergence` — blocks termination, orthogonal convergence judgment
- `AgentInvoker` — blocks, used by DirectJudgmentDispatcher
- Epic blocks#170, children #171, #172, #173
- Parent epic engine#994
