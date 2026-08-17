# ConversationListener — per-dispatch state callback

**Issue:** casehubio/blocks#125
**Consumer:** casehubio/fsitrading#26 — deliberation convergence progress via WebSocket push

## Problem

`ConversationOrchestrator.converse()` runs the multi-agent debate loop but provides no hook for consumers to observe per-dispatch state. The only external output is `responseDispatcher` (dispatches agent responses to the channel). Consumers like fsitrading need per-dispatch convergence updates to broadcast via WebSocket push topics.

## Design

### New type: `ConversationListener`

```java
package io.casehub.blocks.conversation.orchestration;

@FunctionalInterface
public interface ConversationListener {
    void onDispatch(ConversationState state, TerminationDecision decision,
                    int dispatchCount, Duration elapsed);
}
```

A `@FunctionalInterface` with a single `onDispatch` method. Lambda-friendly — consumers wire with a one-liner. Lives in `io.casehub.blocks.conversation.orchestration` alongside the orchestrator and other SPIs.

### Callback granularity

The listener fires after every `terminationCondition.evaluate()` call — once per successful agent dispatch, not per conversation round. This is the most granular point: `ConversationState` has already been updated with the agent's response, and the `TerminationDecision` tells the consumer whether the loop will continue.

The callback fires for all decision types including `Continue`. The consumer sees the full stream and can filter or aggregate as needed (e.g., debounce to round boundaries, ignore Continue signals).

The callback does NOT fire for failed/timed-out agent dispatches (those skip the termination check — see lines 116-119 of `ConversationOrchestrator`).

### Callback signature

| Parameter | Type | Source |
|-----------|------|--------|
| `state` | `ConversationState` | Current projection state, already updated with the agent's response |
| `decision` | `TerminationDecision` | Result of `terminationCondition.evaluate()` — Continue, Complete, Failed, or Escalate |
| `dispatchCount` | `int` | Running count of agent dispatches (including failures) |
| `elapsed` | `Duration` | Time since `converse()` was called — already computed at the call site for `TerminationContext` |

This gives consumers enough context to run `ConvergenceAnalyser` and `CommonGroundAnalyser` externally without coupling the SPI to agentic-layer types like `TerminationContext`.

### Wiring

`ConversationOrchestrator` gains a `@Nullable ConversationListener` field:

- The existing 9-arg constructor is preserved (delegates to the new one with `listener = null`)
- A new 10-arg constructor accepts the listener as the last parameter
- Source-compatible — no existing callers need changes

After `terminationCondition.evaluate()` (current line 131), the listener fires:

```java
var elapsed = Duration.between(start, Instant.now());
var termCtx = new TerminationContext<>(state, dispatchCount, elapsed, List.copyOf(allResults));
finalDecision = terminationCondition.evaluate(termCtx);

if (listener != null) {
    listener.onDispatch(state, finalDecision, dispatchCount, elapsed);
}
```

### Constraints

- The listener is called synchronously on the conversation loop thread. It must be fast — blocking the listener blocks the debate loop. Consumers should offload heavy work (e.g., LLM calls, database writes) to another thread.
- The listener must not throw. If it does, the exception propagates and terminates the conversation loop. This is intentional — a broken observer should not silently drop events while the loop continues with stale downstream state.
- No `List<ConversationListener>` — single listener, nullable. Multi-listener composition is trivial via lambda chaining if a consumer needs it.

### What does NOT change

- No new dependencies
- `ConversationOutcome` unchanged
- No CDI beans added
- `TerminationCondition`, `TerminationDecision`, `ConversationState` unchanged
- Existing tests remain green without modification

## Testing

1. **Listener receives all dispatches** — wire a listener that collects calls into a list, run a 4-dispatch debate, assert the listener was called 4 times with incrementing dispatchCount
2. **Listener receives final termination decision** — verify the last callback carries a non-Continue decision matching the outcome
3. **Listener not called on agent failure** — agent returns FAILURE, verify listener is not called for that dispatch
4. **Null listener (backward compat)** — existing 9-arg constructor works, no NPE
5. **Listener receives elapsed duration** — verify elapsed is non-zero and monotonically increasing across callbacks

## References

- `ConversationOrchestrator.java:127-131` — call site where listener fires
- `ConversationOrchestrator.java:116-119` — failure skip (listener not called)
- `ExecutionEventListener.java` — existing multi-event listener pattern (not reused — wrong abstraction level)
- `TerminationContext.java` — fields available at call site
- casehubio/fsitrading#26 — consumer use case
