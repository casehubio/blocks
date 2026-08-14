## D1: Observation architecture

**Choice:** Composition — ChannelObserver<S> as a standalone type that implements EventSource and holds projected state. TerminationCondition/AggregationStrategy close over the observer. No changes to existing SPIs or drivers.
**Alternatives:**
- Context update hook — add ContextUpdater<T> to the driver loop, T carries projection state. More invasive, requires T mutation support.
- Driver phase — add "observe" phase to the five-phase loop, enrich TerminationContext/AggregationContext. Broadest API change, couples driver to projection concept.
**Rationale:** Natural completion of #19's event-driven model. Observer IS an EventSource — channel events both update the projection and wake the ChoreographedDriver. Event delivery ordering guarantees projected state is current at evaluation time. Zero SPI/driver changes. Matches the ActiveGraph/blackboard pattern — coordination through derived shared state, not control flow.
**Trade-offs:** TerminationCondition reads from outside its TerminationContext (side-channel). Mitigated by deterministic event ordering in ChoreographedDriver. For OrchestratedDriver (continuous loop), one-message lag is possible but acceptable for convergence/consensus decisions.
**Exploration:** deep-analysis (first-principles + internet research: blackboard, event sourcing projections, actor model supervision, ActiveGraph)
**Status:** captured

## D1b: Thread safety for projection state

**Choice:** AtomicReference<S> with updateAndGet for apply(), get() for currentState(). Lock-free.
**Alternatives:**
- Synchronized + volatile — pins virtual thread carrier. Wrong default for a virtual-thread platform.
- ReadWriteLock — overkill for single-writer, single-reader.
**Rationale:** For single-channel observers, qhorus serializes message delivery per channel, so CAS never retries. For multi-channel observers (builder pattern, D4), concurrent dispatch threads may trigger CAS retries — these are cheap and correct. AtomicReference provides happens-before visibility without monitor locking. No virtual thread pinning.
**Trade-offs:** Multi-channel case has CAS contention. Negligible — channel message rates are low (agent conversations), and CAS retry cost is nanoseconds.
**Exploration:** quick
**Depends on:** D1 (observer holds mutable projection state)
**Status:** captured

## D2: Channel subscription mechanism

**Choice:** Observer implements qhorus MessageObserver interface. CDI-based registration — consumer produces the observer as a @ApplicationScoped bean, qhorus auto-discovers it via Instance<MessageObserver>. channels() returns channel name filter (Strings). For non-CDI environments, consumer calls onMessage() directly.
**Alternatives:**
- ChannelManager.addObserver() — does not exist. ChannelManager is a lifecycle API (create/delete/pause), not an observer registry.
- EventStreamBus bridge — bus carries extracted domain events E, not raw MessageView. The projection needs MessageView. Would require a passthrough extractor.
- Decoupled Function<Consumer, Cancellation> subscriber — pushes wiring complexity to consumer.
**Rationale:** Implementing MessageObserver directly is the idiomatic qhorus pattern (same as ChannelEventAdapter). CDI discovery is automatic. onMessage() converts MessageReceivedEvent → MessageView internally, then folds through the projection. Self-describing via channels() filter.
**Trade-offs:** Requires CDI in production. Non-CDI environments call onMessage() manually — acceptable for testing and standalone use.
**Exploration:** quick (corrected during spec self-review — original assumed ChannelManager.addObserver() existed)
**Depends on:** D1 (composition approach requires the observer to manage its own subscription)
**Status:** captured

## D3: Termination convenience API

**Choice:** Generic factory methods on ChannelObserver — `<T> TerminationCondition<T> terminateWhen(Predicate<S>)` and `<T> TerminationCondition<T> asTermination(Function<S, TerminationDecision>)`. Returned condition ignores TerminationContext<T>, reads from currentState(). Generic <T> inferred at call site.
**Alternatives:**
- Standalone adapter class (ChannelTermination<T, S>) — adds a class for no benefit over factory methods.
- No convenience, consumer writes lambda — works but repeats boilerplate across consumers.
**Rationale:** Factory methods make the common case readable (`observer.terminateWhen(...)`) with zero type ceremony. The generic erasure bridges S (projection type) to T (driver context type) cleanly.
**Trade-offs:** The returned TerminationCondition ignores its context's T state entirely — it only reads the observer. This is by design (the projection IS the state), but a consumer who accidentally uses these methods expecting T-based evaluation would get silent wrong behavior.
**Exploration:** quick
**Depends on:** D1 (observer holds projected state that termination reads)
**Status:** captured

## D4: Multiple channel observation

**Choice:** Independent observers per channel as the default. Each observer is its own EventSource, composition via termination combinators. Additionally, a builder pattern supports multiple channels feeding one projection: `ChannelObserver.builder(projection).channel(mgr, idA).channel(mgr, idB).build()`.
**Alternatives:**
- Independent only, no builder — forces consumer to build custom multi-channel wiring when channels share a projection type.
- Composite only — conflates concerns when channels have different projection types.
**Rationale:** Independent observers compose naturally via existing CompositeTermination / .and() / .or(). The builder covers the legitimate case where multiple channels feed the same projection (e.g., multiple sub-team channels observed as one conversation). Cheap to provide, no architectural cost.
**Trade-offs:** Builder adds a small API surface. Worth it — the alternative is consumer boilerplate for a common pattern.
**Exploration:** quick
**Depends on:** D1 (observer as EventSource), D2 (MessageObserver subscription per channel)
**Status:** captured

## D5: Package placement

**Choice:** `io.casehub.blocks.agentic.channel` — alongside ChannelBinding, ChannelConfig, ChannelExecutionStrategy from #20. The observer is part of the channel infrastructure.
**Alternatives:**
- `agentic.model` — alongside EventSource/ChoreographedDriver. Observer implements EventSource but that's not its identity.
- New `agentic.observation` sub-package — premature for one type.
**Rationale:** The observer observes channels. It belongs with the channel types. Consistent with the existing package structure from #20.
**Trade-offs:** None significant.
**Exploration:** quick
**Depends on:** D1 (observer type exists)
**Status:** captured

## D6: Factory API

**Choice:** Static factory taking channel names (Strings) — `ChannelObserver.of(projection, "channel-name")`. No ChannelManager dependency. The observer is self-describing via MessageObserver.channels().
**Alternatives:**
- ChannelManager + channelId (UUID) — ChannelManager has no addObserver() API. channelId is wrong type — MessageObserver.channels() uses Strings.
- No factory, constructor only — minor ergonomic loss.
**Rationale:** Channel names are the filter key in qhorus's MessageObserver contract. The observer doesn't need ChannelManager for anything — registration is CDI-based, filtering is name-based.
**Trade-offs:** Consumer must know the channel name at construction. This is always available — the consumer creates the channel.
**Exploration:** quick (corrected during spec self-review)
**Depends on:** D2 (CDI-based MessageObserver registration uses channels() filter)
**Status:** captured
