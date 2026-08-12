## D1: Event model for ChoreographedDriver

**Choice:** Hybrid — external events trigger iterations, but agent completions from the previous cycle are also events that can trigger the next one
**Alternatives:**
- Agent-completion-driven — driver loops after each iteration like Orchestrated, just non-blocking. Simpler but not truly event-reactive.
- External-event-driven — only external events trigger iterations. Agent completions within an iteration flow naturally, but don't trigger re-evaluation.
**Rationale:** Hybrid is the most natural model for choreographic execution. The driver is dormant between iterations but can wake on ANY stimulus — external event or internal completion. In practice, agent completions trigger re-evaluation naturally (the loop continues after an iteration completes and checks the queue).
**Trade-offs:** Slightly more complex event handling than pure external-event-driven — the driver must decide whether to wait for a new event or re-evaluate immediately after an iteration.
**Exploration:** quick
**Status:** captured

## D2: Concurrency policy model

**Choice:** Composable EventConcurrencyPolicy with serialize (actor-mailbox) as default. Policies chain via `.then()` for pipeline composition.
**Alternatives:**
- Serialize only — no pluggable strategy. Simplest but no escape hatch for high-frequency event scenarios.
- Pluggable but non-composable — swap strategies but can't chain (e.g., coalesce-then-serialize).
**Rationale:** Research confirms actor-mailbox serialization as the safest default (Akka, Erlang/OTP, Strands Agents). But composable policies let consumers tune for their domain: coalesce rapid-fire sensor events within a window, then serialize the batches. Composition via `.then()` mirrors the pipeline model.
**Trade-offs:** Larger API surface than serialize-only. The composition model adds complexity that most consumers won't need — but the default (serialize) is zero-config.
**Exploration:** quick (informed by internet research)
**Status:** captured

## D3: Internal loop architecture

**Choice:** Blocking imperative loop on a virtual thread. No Uni chain refactoring.
**Alternatives:**
- Reactive Uni chains — refactor executeIteration to return Uni, chain loop iterations reactively. Eliminates thread blocking.
- Multi subscription — driver subscribes to a Multi stream, each event triggers a reactive iteration.
**Rationale:** The platform has moved to virtual threads. Blocking a virtual thread is free — the thread-efficiency argument for reactive Uni chains is eliminated. The current executeIteration() already calls `.await().indefinitely()` on every SPI. Converting to Uni chains adds complexity for zero benefit. Uni remains appropriate at API boundaries (execute() returns Uni) and in SPI contracts, but the driver's internal loop is inherently sequential work on a dedicated thread.
**Trade-offs:** Relies on virtual thread availability. On a platform thread, a ChoreographedDriver waiting for a HumanAgent would block a real thread for hours. Consumers must ensure execution runs on a virtual thread.
**Exploration:** deep-analysis (first-principles analysis of reactive vs. virtual threads)
**Depends on:** D1 (event model determines loop structure)
**Status:** captured

## D4: Event delivery mechanism

**Choice:** New EventSource SPI backed by a BlockingQueue. Not Multi, not EventStreamBus.
**Alternatives:**
- Multi-based stream — driver takes Multi<T> at construction. Rich composition via Mutiny operators. But overkill for a blocking queue consumer — the driver doesn't use reactive operators.
- Reuse EventStreamBus — existing pub/sub in summarisation package. But couples driver to LevelEvent/EventLevel types from the wrong architectural layer.
- Direct push only (driver.signal()) — simplest but no declarative composition or cancellation.
**Rationale:** With D3 (blocking loop on virtual thread), the driver consumes events by blocking on a queue, not by subscribing to a reactive stream. Multi is the wrong abstraction — 99% of its capabilities go unused. EventStreamBus is the wrong layer (summarisation, not agentic execution). A lightweight EventSource SPI provides declarative composition (merge, ticker) and auto-cancellation without Mutiny coupling.
**Trade-offs:** Introduces a new type (EventSource) instead of reusing Multi. But EventSource is ~15 lines — a functional interface with static factories. It's right-sized for the problem.
**Depends on:** D3 (blocking loop determines that Multi is overengineered)
**Exploration:** quick (followed from D3 analysis)
**Status:** captured

## D5: Event type

**Choice:** Typed DriverEvent record with source ID, timestamp, and optional payload.
**Alternatives:**
- Object / Void — pure trigger signal. Driver doesn't inspect events, just wakes up. Simplest but the policy can't differentiate events.
- Context update function (UnaryOperator<T>) — each event transforms the driver's context. Functional state evolution but overcomplicates the model — T is typically read from external state.
**Rationale:** Typed events let the EventConcurrencyPolicy make smart decisions: coalesce by source (merge all timer ticks, keep channel events separate), prioritize certain sources, log what triggered each iteration. The record is small (3 fields) and carries its own timestamp for coalescing windows.
**Trade-offs:** Slightly more ceremony than Object/Void — event sources must construct DriverEvent instances instead of just signalling.
**Exploration:** quick
**Status:** captured

## D6: OrchestratedDriver scope

**Choice:** OrchestratedDriver stays unchanged — blocking while loop, no event-waiting.
**Alternatives:**
- Both reactive — refactor AbstractExecutionDriver to be reactive-first. Larger scope but cleaner base.
**Rationale:** OrchestratedDriver loops continuously by design. It doesn't wait for events — the completion of one iteration IS the trigger for the next. The change is ChoreographedDriver-specific: add event-waiting between iterations. AbstractExecutionDriver and executeIteration() stay untouched — the five-phase loop is shared.
**Trade-offs:** None significant — this is the minimal-scope approach.
**Exploration:** quick
**Status:** captured
