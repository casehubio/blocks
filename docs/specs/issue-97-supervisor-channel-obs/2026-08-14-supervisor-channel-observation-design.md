# Supervisor Channel Observation Design

**Issue:** blocks#97
**Parent spec:** blocks `docs/specs/issue-19-choreographed-event-bus/2026-08-11-choreographed-event-bus-design.md` (ChoreographedDriver event-bus integration)
**Depends on:** blocks#20 (inter-agent channel setup — closed)

## Context

The agentic execution drivers (OrchestratedDriver, ChoreographedDriver) run a five-phase loop: route → activate → dispatch → aggregate → terminate. The `TerminationCondition<T>` and `AggregationStrategy<T>` SPIs receive the consumer's context `T` via `TerminationContext<T>` and `AggregationContext<T>`. But T is set once at `execute(model, initialContext)` and passed unchanged through every iteration. The driver is blind to inter-agent communication happening in channels.

Issue #20 established the inter-agent channel infrastructure — `ChannelBinding`, `ChannelConfig`, `ChannelExecutionStrategy` (Conversation, FanIn, Barrier). Issue #19 added event-driven execution to ChoreographedDriver via `EventSource` + `EventConcurrencyPolicy`. But there is no mechanism for a supervisor to observe channel activity and feed that observation into its termination/aggregation decisions.

ConversationOrchestrator solved this for conversation-specific use cases by maintaining `ConversationState` explicitly and passing it as T. But it's a separate loop — it doesn't use AbstractExecutionDriver. The generic drivers need a composable, consumer-facing observation API.

## Design

### 1. ChannelObserver

A composable observation type that maintains a projected view of channel communication. Implements both `MessageObserver` (qhorus message delivery) and `EventSource` (ChoreographedDriver wake-up). Combines four roles:

1. **MessageObserver** — receives `MessageReceivedEvent` from qhorus CDI-based dispatch, converts to `MessageView`, folds through projection
2. **Projection holder** — maintains current projected state via `AtomicReference<S>`
3. **EventSource** — wakes the ChoreographedDriver when channel activity occurs
4. **Termination factory** — provides convenience methods to create `TerminationCondition` instances that read from the projected state

```java
package io.casehub.blocks.agentic.channel;

public class ChannelObserver<S> implements MessageObserver, EventSource {

    private final ChannelProjection<S> projection;
    private final AtomicReference<S> state;
    private final Set<String> channelNames;
    private volatile Consumer<DriverEvent> sink;

    private ChannelObserver(ChannelProjection<S> projection,
                            Set<String> channelNames) {
        this.projection = projection;
        this.state = new AtomicReference<>(projection.identity());
        this.channelNames = Set.copyOf(channelNames);
    }

    // --- MessageObserver ---

    @Override
    public void onMessage(MessageReceivedEvent event) {
        try {
            var messageView = toMessageView(event);
            state.updateAndGet(s -> projection.apply(s, messageView));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                "ChannelObserver: projection failed for channel "
                + event.channelId() + ": " + e.getMessage());
            var s = sink;
            if (s != null) {
                s.accept(DriverEvent.signal("projection-error:" + event.channelId()));
            }
            return;
        }
        var s = sink;
        if (s != null) {
            s.accept(DriverEvent.signal("channel:" + event.channelId()));
        }
    }

    @Override
    public Set<String> channels() {
        return channelNames;
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    // --- EventSource ---

    @Override
    public Cancellation subscribe(Consumer<DriverEvent> sink) {
        if (this.sink != null) {
            throw new IllegalStateException(
                "ChannelObserver already has an active subscriber");
        }
        this.sink = sink;
        return Cancellation.of(() -> this.sink = null);
    }

    // --- Projection state ---

    public S currentState() {
        return state.get();
    }

    public void reset() {
        state.set(projection.identity());
    }

    // --- Termination convenience ---

    public <T> TerminationCondition<T> terminateWhen(Predicate<S> condition) {
        return ctx -> condition.test(currentState())
            ? new TerminationDecision.Complete("Channel observation")
            : TerminationDecision.Continue.INSTANCE;
    }

    public <T> TerminationCondition<T> asTermination(
            Function<S, TerminationDecision> evaluator) {
        return ctx -> evaluator.apply(currentState());
    }

    // --- Factories ---

    public static <S> ChannelObserver<S> of(ChannelProjection<S> projection,
                                             String channelName) {
        return new ChannelObserver<>(projection, Set.of(channelName));
    }

    public static <S> Builder<S> builder(ChannelProjection<S> projection) {
        return new Builder<>(projection);
    }

    // --- Builder ---

    public static final class Builder<S> {
        private final ChannelProjection<S> projection;
        private final Set<String> channelNames = new LinkedHashSet<>();

        private Builder(ChannelProjection<S> projection) {
            this.projection = projection;
        }

        public Builder<S> channel(String channelName) {
            channelNames.add(channelName);
            return this;
        }

        public ChannelObserver<S> build() {
            if (channelNames.isEmpty()) {
                throw new IllegalStateException("At least one channel required");
            }
            return new ChannelObserver<>(projection, channelNames);
        }
    }

    // --- Internal ---

    private static MessageView toMessageView(MessageReceivedEvent event) {
        return new MessageView(
            event.messageId(), event.channelId(), event.senderId(),
            event.messageType(), event.content(), event.correlationId(),
            null, event.target(), event.topic(),
            List.of(), event.actorType(), event.occurredAt(),
            null, 0);
    }
}
```

### 2. Event Delivery Ordering

The observer's `onMessage()` performs two operations in sequence:

1. `state.updateAndGet(...)` — folds the message through the projection, updating the `AtomicReference`
2. `sink.accept(DriverEvent.signal(...))` — posts a `DriverEvent` to the ChoreographedDriver's queue

This ordering guarantees that when the driver wakes (from the DriverEvent) and evaluates termination, `currentState()` already reflects the message that triggered the wake-up. The projection is always current at evaluation time.

For OrchestratedDriver (continuous loop, no EventSource), the observer still works — the projection updates asynchronously via CDI dispatch, and the next iteration's termination evaluation reads the latest state. One-message lag is possible but acceptable for convergence/consensus decisions.

### 3. Registration

The observer implements `MessageObserver` and is registered via CDI. Qhorus's `MessageObserverDispatcher` discovers all `Instance<MessageObserver>` beans and routes messages to matching observers based on the `channels()` filter.

**Production (CDI environment):**
```java
@Produces @ApplicationScoped
ChannelObserver<ConversationState> debateObserver() {
    return ChannelObserver.of(new DebateProjection(), "debate-channel");
}
```

**Testing (no CDI):**
```java
var observer = ChannelObserver.of(projection, "test-channel");
observer.onMessage(testEvent); // direct call
```

### 4. Consumer Wiring

#### Simple case — single channel observation

```java
// CDI-produced observer (or manual for tests)
var observer = ChannelObserver.of(conversationProjection, "debate-channel");

var model = Patterns.supervisor()
    .backend(ExecutionBackend.choreographed(
        EventConcurrencyPolicy.serialize(), observer))
    .termination(observer.terminateWhen(ConversationState::allResolved)
        .or(new MaxIterationsTermination<>(20)))
    .build();
```

#### Multiple independent channels

```java
var debateObserver = ChannelObserver.of(debateProjection, "debate-channel");
var researchObserver = ChannelObserver.of(researchProjection, "research-channel");

var driver = new ChoreographedDriver<>(invoker, policy, debateObserver, researchObserver);

var termination = debateObserver.terminateWhen(s -> s.hasConsensus())
    .and(researchObserver.terminateWhen(s -> s.isComplete()));
```

#### Multiple channels, one projection

```java
var observer = ChannelObserver.builder(sharedProjection)
    .channel("team-a-channel")
    .channel("team-b-channel")
    .build();
```

#### Custom termination logic

```java
var termination = observer.<MyContext>asTermination(channelState -> {
    if (channelState.disputedPoints() > 3) {
        return new TerminationDecision.Escalate("Too many disputed points");
    }
    if (channelState.allResolved()) {
        return new TerminationDecision.Complete(channelState.summary());
    }
    return TerminationDecision.Continue.INSTANCE;
});
```

#### Aggregation with channel context

```java
AggregationStrategy<MyContext> aggregation = (results, ctx) -> {
    var channelState = observer.currentState();
    if (channelState.hasConsensus()) {
        return new AggregationResult.Resolved(channelState.consensusValue());
    }
    return new AggregationResult.Partial(results);
};
```

### 5. MessageReceivedEvent → MessageView Conversion

`ChannelProjection<S>.apply()` takes `MessageView`. `MessageObserver.onMessage()` delivers `MessageReceivedEvent`. The observer converts internally via `toMessageView()`. Fields not available from the event (`inReplyTo`, `artefactRefs`, `deadline`, `replyCount`) are set to null/empty/zero defaults.

This is acceptable for all existing projections — `ConversationProjection` and its subclasses read `sender`, `type`, `content`, `correlationId`, and `topic`, all present in the event. If a future projection requires the missing fields, it should query the channel message store for full `MessageView` data rather than relying on the observer's event-based delivery.

### 6. Lifecycle

| Phase | What happens |
|-------|-------------|
| Construction | Observer created with projection and channel names. State = `projection.identity()`. Sink = null. |
| CDI discovery | Qhorus `MessageObserverDispatcher` discovers the observer bean. No messages yet. |
| `reset()` | Consumer calls before each execution to clear accumulated state from prior runs. Resets to `projection.identity()`. |
| `subscribe()` | Called by ChoreographedDriver at `execute()`. Stores the DriverEvent sink. Throws `IllegalStateException` if a subscriber is already active. |
| Execution | Messages arrive on qhorus dispatch thread → `onMessage()` folds and signals driver → driver wakes and evaluates termination with current projected state. Projection exceptions are caught, logged, and signaled as `projection-error` DriverEvents. |
| `Cancellation.cancel()` | Called by ChoreographedDriver in `finally` block. Clears the sink. Observer continues receiving messages (CDI lifecycle) but stops signaling the driver. |

The observer's CDI lifecycle is independent of the driver's execution lifecycle. The observer bean lives as long as the CDI container. The EventSource subscription (sink) lives only during driver execution. Consumers must call `reset()` before re-executing with the same observer to avoid stale state from prior runs.

### 7. Thread Model

| Thread | Operation | Safety |
|--------|-----------|--------|
| Qhorus dispatch thread | `onMessage()` → `AtomicReference.updateAndGet()` | Lock-free CAS. Single writer per channel (qhorus serializes). Multiple channels: CAS handles contention. |
| Qhorus dispatch thread | `sink.accept(DriverEvent)` — `LinkedBlockingQueue.add()` | Thread-safe by JDK contract. |
| Driver virtual thread | `currentState()` — `AtomicReference.get()` | Happens-before guaranteed by AtomicReference. |
| Driver virtual thread | `sink` field read via volatile | Volatile read — safe for null check. |

No `synchronized` blocks. No virtual thread pinning.

### 8. TerminationCondition Combinators

`TerminationCondition<T>` currently has no composition methods. The observer's `terminateWhen()` / `asTermination()` produce conditions that need to compose with other conditions (e.g., MaxIterationsTermination). Adding default `or()` and `and()` methods enables fluent composition:

```java
public interface TerminationCondition<T> {
    TerminationDecision evaluate(TerminationContext<T> context);

    default TerminationCondition<T> or(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) {
                return other.evaluate(ctx);
            }
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) {
                return first;
            }
            return higherPriority(first, second);
        };
    }

    default TerminationCondition<T> and(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) return first;
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) return second;
            return higherPriority(first, second);
        };
    }

    private static TerminationDecision higherPriority(
            TerminationDecision a, TerminationDecision b) {
        return priority(a) >= priority(b) ? a : b;
    }

    private static int priority(TerminationDecision d) {
        return switch (d) {
            case TerminationDecision.Escalate e -> 3;
            case TerminationDecision.Failed f -> 2;
            case TerminationDecision.Complete c -> 1;
            case TerminationDecision.Continue ignored -> 0;
        };
    }
}
```

Semantics: `or()` = either condition can terminate (first non-Continue wins; if both fire, highest priority wins). `and()` = both must agree to terminate (both non-Continue required; highest priority wins). Priority hierarchy: Escalate > Failed > Complete > Continue. Escalate and Failed always surface — they are never swallowed by a lower-priority Complete.

This also replaces `CompositeTermination` in the conversation.orchestration package (which is hardcoded to `ConversationState`). Existing uses of CompositeTermination become `.or()` chains.

## Type Summary

**New types:**

| Type | Package | Purpose |
|------|---------|---------|
| `ChannelObserver<S>` | `agentic.channel` | MessageObserver + EventSource + projection holder + termination factory |
| `ChannelObserver.Builder<S>` | `agentic.channel` | Multi-channel builder |

**Modified types:**

| Type | Change |
|------|--------|
| `TerminationCondition<T>` | +2 default methods: `or()`, `and()` for fluent composition |

**Deprecated:**

| Type | Replaced by |
|------|-------------|
| `CompositeTermination` | `TerminationCondition.or()` chains |

**Unchanged:**
- TerminationContext, TerminationDecision
- AggregationStrategy, AggregationContext, AggregationResult
- AbstractExecutionDriver, OrchestratedDriver, ChoreographedDriver
- ExecutionModel, ExecutionBackend
- EventSource, DriverEvent, EventConcurrencyPolicy
- ChannelProjection (qhorus), MessageObserver (qhorus)
- All pattern builders

## Testing Strategy

**ChannelObserver — projection folding:**
- Single message via `onMessage()`: converts event, folds, `currentState()` returns projected state
- Multiple messages: state accumulates incrementally through projection
- Identity: fresh observer returns `projection.identity()`
- Thread safety: concurrent `onMessage()` from multiple dispatch threads, `currentState()` from driver thread — no lost updates

**ChannelObserver — MessageObserver contract:**
- `channels()` returns the configured channel name set
- `scope()` returns LOCAL
- Channel name filtering: observer only processes messages for configured channels (tested via `channels()` return; actual filtering is done by qhorus dispatcher)

**ChannelObserver — EventSource contract:**
- `subscribe()` stores sink, returns Cancellation
- `subscribe()` with existing subscriber: throws IllegalStateException
- `subscribe()` after cancel + re-subscribe: succeeds (sink was cleared)
- `onMessage()` with active sink: posts DriverEvent after projection update
- `onMessage()` without sink (before subscribe or after cancel): folds message, no DriverEvent
- `Cancellation.cancel()`: clears sink, subsequent onMessage() folds without signaling

**ChannelObserver — reset:**
- `reset()` returns state to `projection.identity()`
- `reset()` after messages: clears accumulated state
- `reset()` is safe to call while no subscriber is active

**ChannelObserver — projection error handling:**
- Projection exception: state unchanged, error logged, projection-error DriverEvent posted
- Projection exception without sink: state unchanged, error logged, no DriverEvent
- Subsequent valid messages fold normally after a projection error

**ChannelObserver — event delivery ordering:**
- Projection state updated BEFORE DriverEvent posted (AtomicReference.updateAndGet completes before sink.accept)
- Driver reads currentState() after wake: sees the message that triggered the wake

**ChannelObserver — termination convenience:**
- `terminateWhen(predicate)`: returns Complete when predicate true on current state, Continue otherwise
- `asTermination(function)`: delegates to function with current state
- Generic T: returned condition works regardless of driver context type

**ChannelObserver — MessageReceivedEvent → MessageView conversion:**
- Core fields mapped correctly: messageId, channelId, senderId, messageType, content, correlationId, target, topic, actorType, occurredAt
- Missing fields defaulted: inReplyTo=null, artefactRefs=empty, deadline=null, replyCount=0

**ChannelObserver — builder (multi-channel):**
- Multiple channel names accepted
- All channels fold into single projection
- Events from any channel post DriverEvents
- Empty builder throws IllegalStateException
- Duplicate channel names deduplicated

**TerminationCondition combinators:**
- `or()`: Continue + Continue = Continue; Complete + Continue = Complete; Continue + Complete = Complete
- `or()` priority: Complete + Escalate = Escalate; Failed + Complete = Failed
- `and()`: Continue + Complete = Continue; Complete + Continue = Continue; Complete + Complete = Complete
- `and()` priority: Complete + Escalate = Escalate; Complete + Failed = Failed

**Integration with ChoreographedDriver:**
- Observer as EventSource: driver blocks on queue, wakes on channel message
- Termination evaluates with current projected state after wake
- Cancellation: driver's finally block cancels observer's EventSource subscription
- Multiple observers: each is an independent EventSource, driver merges via EventSource.merge()

**Integration with OrchestratedDriver:**
- Observer state updates asynchronously via CDI dispatch
- Termination reads latest state each iteration
- One-message lag: termination decision based on state at evaluation time, not message arrival time
