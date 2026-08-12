# ChoreographedDriver Event-Bus Integration Design

**Issue:** blocks#19
**Parent spec:** blocks `docs/specs/2026-06-30-engine-integration-design.md` (engine integration & channel coordination)

## Context

ChoreographedDriver extends AbstractExecutionDriver and runs the same five-phase loop as OrchestratedDriver: route → activate → dispatch → aggregate → terminate. The only current difference is a cosmetic `WaitingForEvent` state transition between iterations — the driver doesn't actually wait for events. It loops continuously, identical to OrchestratedDriver.

The engine integration spec describes ChoreographedDriver as "genuinely reactive — it waits for events between iterations using Uni chaining, not blocking." But the platform has since moved to virtual threads, which changes the calculus. Blocking a virtual thread is free — the thread-efficiency argument for reactive Uni chains is eliminated. Uni remains appropriate at API boundaries (`execute()` returns `Uni<ExecutionResult>`) and in SPI contracts (routing, activation, etc.), but the driver's internal loop is inherently sequential work on a dedicated thread.

The actual gap is narrow: ChoreographedDriver needs a mechanism to wait for external events between iterations, a policy for handling event concurrency, and integration points for ChannelAgent and HumanAgent event sources.

## Design

### 1. DriverEvent

Typed event record carrying source identification and optional payload. Enables the concurrency policy to make intelligent decisions (coalesce by source, window by timestamp).

```java
package io.casehub.blocks.agentic.model;

public record DriverEvent(
    String source,
    Instant timestamp,
    @Nullable Object payload
) {
    public DriverEvent(String source) {
        this(source, Instant.now(), null);
    }

    public DriverEvent(String source, Object payload) {
        this(source, Instant.now(), payload);
    }

    public static DriverEvent signal(String source) {
        return new DriverEvent(source);
    }

    public static DriverEvent timer() {
        return new DriverEvent("timer");
    }
}
```

### 2. EventSource

Lightweight SPI for declarative event composition. Not coupled to Mutiny — the driver consumes events via a BlockingQueue internally. EventSource provides subscription and cancellation semantics.

```java
package io.casehub.blocks.agentic.model;

@FunctionalInterface
public interface EventSource {

    Cancellation subscribe(Consumer<DriverEvent> sink);

    interface Cancellation {
        void cancel();

        static Cancellation of(Runnable action) {
            return action::run;
        }

        static Cancellation composite(List<Cancellation> cancellations) {
            return () -> cancellations.forEach(Cancellation::cancel);
        }
    }

    static EventSource merge(EventSource... sources) {
        return sink -> {
            var cancellations = new ArrayList<Cancellation>();
            for (var source : sources) {
                cancellations.add(source.subscribe(sink));
            }
            return Cancellation.composite(cancellations);
        };
    }

    static EventSource ticker(Duration interval, ScheduledExecutorService executor) {
        return sink -> {
            var future = executor.scheduleAtFixedRate(
                () -> sink.accept(DriverEvent.timer()),
                interval.toMillis(), interval.toMillis(),
                TimeUnit.MILLISECONDS);
            return Cancellation.of(() -> future.cancel(false));
        };
    }
}
```

**Lifecycle:** EventSource subscriptions are created when `execute()` is called and cancelled in a `finally` block when execution completes (normal termination, failure, or cancellation). The driver owns the lifecycle — consumers provide the EventSource, the driver manages subscription/cancellation.

### 3. EventConcurrencyPolicy

Controls how events are consumed from the internal queue between iterations. The policy answers: "given a queue with pending events, what should the driver process?"

```java
package io.casehub.blocks.agentic.model;

public interface EventConcurrencyPolicy {

    List<DriverEvent> awaitEvents(BlockingQueue<DriverEvent> queue)
            throws InterruptedException;

    default EventConcurrencyPolicy then(EventConcurrencyPolicy next) {
        var first = this;
        return queue -> {
            var events = first.awaitEvents(queue);
            // Re-queue the batch for the next policy to process
            var staging = new LinkedBlockingQueue<DriverEvent>();
            staging.addAll(events);
            return next.awaitEvents(staging);
        };
    }

    static EventConcurrencyPolicy serialize() {
        return queue -> List.of(queue.take());
    }

    static EventConcurrencyPolicy coalesce() {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            queue.drainTo(batch);
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesce(Duration window) {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            var deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                var next = queue.poll(
                    deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (next == null) break;
                batch.add(next);
            }
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesceBySource() {
        return queue -> {
            var first = queue.take();
            var bySource = new LinkedHashMap<String, DriverEvent>();
            bySource.put(first.source(), first);
            var remaining = new ArrayList<DriverEvent>();
            queue.drainTo(remaining);
            for (var e : remaining) {
                bySource.put(e.source(), e); // last-writer-wins per source
            }
            return List.copyOf(bySource.values());
        };
    }
}
```

**Queue bounds:** The internal BlockingQueue is unbounded (`LinkedBlockingQueue`). For most use cases (channel messages, timer ticks) event rates are low. High-frequency scenarios use `coalesce()` or `coalesceBySource()` to prevent queue growth — the policy drains all pending events into a single batch. If bounded queues are needed, consumers can use `coalesce(Duration)` with a short window to limit accumulation.

### 4. ChoreographedDriver Changes

The driver adds an event source and concurrency policy. AbstractExecutionDriver, OrchestratedDriver, executeIteration(), and all five SPIs are unchanged.

**Two modes of operation:**
- **Legacy mode** (no EventSource): loops continuously like the current implementation. `WaitingForEvent` transitions are cosmetic. This preserves backward compatibility — all existing tests pass without modification.
- **Event-driven mode** (EventSource provided): waits for events between iterations. The presence of an EventSource is the activation signal.

```java
package io.casehub.blocks.agentic.model;

public class ChoreographedDriver<T> extends AbstractExecutionDriver<T> {

    private final @Nullable EventSource eventSource;
    private final EventConcurrencyPolicy policy;
    private final BlockingQueue<DriverEvent> eventQueue = new LinkedBlockingQueue<>();

    public ChoreographedDriver() {
        super();
        this.eventSource = null;
        this.policy = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker) {
        super(invoker);
        this.eventSource = null;
        this.policy = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker,
                                EventConcurrencyPolicy policy,
                                EventSource... sources) {
        super(invoker);
        this.policy = policy;
        this.eventSource = sources.length == 0
            ? null
            : sources.length == 1 ? sources[0] : EventSource.merge(sources);
    }

    public void signal(DriverEvent event) {
        eventQueue.add(event);
    }

    public void signal(String source) {
        eventQueue.add(DriverEvent.signal(source));
    }

    @Override
    public Uni<Void> cancel() {
        eventQueue.add(DriverEvent.signal("cancelled"));
        return super.cancel();
    }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            EventSource.Cancellation subscription = null;
            try {
                if (eventSource != null) {
                    subscription = eventSource.subscribe(eventQueue::add);
                }

                var start = Instant.now();
                var allResults = new ArrayList<AgentResult>();
                int iteration = 0;

                while (!isCancelled()) {
                    transition(model, new ExecutionState.WaitingForEvent());

                    if (eventSource != null) {
                        policy.awaitEvents(eventQueue); // blocks virtual thread
                    }

                    transition(model, new ExecutionState.Running(iteration));
                    var result = executeIteration(
                            model, context, iteration, start, allResults);
                    if (result != null) return result;

                    iteration++;
                }

                transition(model, new ExecutionState.Cancelled());
                return new ExecutionResult.Cancelled();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                transition(model, new ExecutionState.Cancelled());
                return new ExecutionResult.Cancelled();
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        });
    }
}
```

### 5. ExecutionBackend Factory Methods

```java
public interface ExecutionBackend<T> {
    // ... existing methods ...

    static <T> ExecutionBackend<T> choreographed(EventConcurrencyPolicy policy,
                                                  EventSource... sources) {
        return new CancellableBackend<>(
            new ChoreographedDriver<>(AgentInvoker.defaultInvoker(), policy, sources));
    }

    static <T> ExecutionBackend<T> choreographed(AgentInvoker<T> invoker,
                                                  EventConcurrencyPolicy policy,
                                                  EventSource... sources) {
        return new CancellableBackend<>(new ChoreographedDriver<>(invoker, policy, sources));
    }
}
```

### 6. Cancellation Integration

When `cancel()` is called on the driver (see §4 override):

1. A `"cancelled"` DriverEvent is posted to the queue — this breaks `queue.take()` without thread interruption
2. `super.cancel()` sets the `cancelled` flag (existing behavior)
3. The loop checks `isCancelled()` after `awaitEvents()` returns and exits cleanly
4. The EventSource subscription is cancelled in the `finally` block

Posting a cancellation event is simpler and safer than thread interruption. If the driver is mid-iteration (waiting for an agent via `invoker.invoke().await()`), the cancellation event sits in the queue until the iteration completes, then the `isCancelled()` check exits the loop.

### 7. Virtual Thread Execution

The driver blocks on `eventQueue.take()` between iterations and on `invoker.invoke().await().indefinitely()` during agent dispatch. Both are free on virtual threads but pin platform threads.

**Consumer responsibility:** Consumers must ensure the driver executes on a virtual thread. The engine's supervisor dispatch already runs on Quartz worker threads. For Quartz 2.5+ with virtual thread support, no change is needed. For other consumers:

```java
driver.execute(model, context)
    .runSubscriptionOn(Executors.newVirtualThreadPerTaskExecutor())
    .subscribe().with(result -> { ... });
```

This is a documentation/API guidance concern, not a design constraint. The spec should note it clearly but the driver itself is thread-agnostic — it blocks wherever it runs.

## Thread Model

| Scenario | Thread | Blocking cost |
|----------|--------|--------------|
| Event-wait between iterations | Virtual thread blocks on `queue.take()` | Free |
| Agent dispatch (ExternalAgent) | Virtual thread blocks on `CompletionStage.join()` | Free |
| Agent dispatch (ChannelAgent) | Virtual thread blocks on `CorrelationObserver` future | Free (seconds–minutes) |
| Agent dispatch (HumanAgent) | Virtual thread blocks on `WorkItemObserver` future | Free (hours–days) |
| SPI calls (routing, activation, etc.) | Virtual thread blocks on Uni `.await()` | Free |

All blocking occurs on a single virtual thread per driver instance. No thread pool sizing concerns. The EventSource callbacks run on their own threads (timer thread, qhorus dispatch thread, etc.) and feed the queue — they never block.

## Type Summary

**New types:**

| Type | Package | Purpose |
|------|---------|---------|
| `DriverEvent` | `agentic.model` | Typed event record: source, timestamp, optional payload |
| `EventSource` | `agentic.model` | SPI for event delivery with subscription/cancellation |
| `EventSource.Cancellation` | `agentic.model` | Cancellation handle for subscriptions |
| `EventConcurrencyPolicy` | `agentic.model` | Pluggable policy: serialize, coalesce, coalesce-by-source, composable via `.then()` |

**Modified types:**

| Type | Change |
|------|--------|
| `ChoreographedDriver` | +EventSource, +EventConcurrencyPolicy, +BlockingQueue, +signal() method. runLoop() adds event-wait. Backward-compatible no-arg constructor preserves current continuous-loop behavior. |
| `ExecutionBackend` | +2 factory methods: `choreographed(policy, sources...)` |

**Unchanged:**
- AbstractExecutionDriver (base class, executeIteration, all notification methods)
- OrchestratedDriver
- ExecutionModel
- All five SPIs (RoutingStrategy, DecompositionStrategy, ActivationRule, AggregationStrategy, TerminationCondition)
- AgentInvoker
- ExecutionState (WaitingForEvent already exists)
- ExecutionEventListener
- CancellableBackend
- All pattern builders

## Testing Strategy

**EventConcurrencyPolicy:** Pure Java unit tests, no CDI:
- `serialize()`: single event per await, FIFO order
- `coalesce()`: drains all pending events into one batch
- `coalesce(window)`: waits for window duration after first event, collects all
- `coalesceBySource()`: last-writer-wins per source ID
- `.then()` composition: two policies chained produce expected output

**EventSource:** Pure Java unit tests:
- `merge()`: events from multiple sources arrive in a single sink
- `ticker()`: events arrive at the configured interval (use short intervals + Awaitility)
- `Cancellation.cancel()`: source stops delivering after cancellation
- `Cancellation.composite()`: all child cancellations fire

**ChoreographedDriver (event-driven mode):**
- Event triggers iteration: signal → iteration runs → WaitingForEvent
- Multiple events queued during iteration: processed per policy after iteration completes
- Cancellation: driver exits cleanly when cancelled during event-wait
- Cancellation during agent dispatch: driver exits after current iteration
- No event source (legacy mode): loops continuously like current behavior
- EventSource lifecycle: subscription created at execute(), cancelled at completion

**ChoreographedDriver (with AgentInvoker):**
- ExternalAgent: existing tests still pass (no behavioral change)
- Long-running agent simulation: event queued during dispatch, processed after dispatch completes
- Timer-triggered re-evaluation: ticker EventSource triggers periodic iterations

**Integration with ExecutionBackend:**
- `ExecutionBackend.choreographed()` creates a working driver with event source and policy
- Cancellation via `backend.cancel()` propagates to driver and event sources

**Backward compatibility:**
- All existing ChoreographedDriverTest tests pass without modification
- No-arg constructor preserves current continuous-loop behavior
