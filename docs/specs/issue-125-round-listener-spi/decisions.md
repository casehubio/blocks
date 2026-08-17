## D1: SPI shape — @FunctionalInterface vs multi-event interface

**Choice:** `@FunctionalInterface` with a single `onDispatch` method
**Alternatives:**
- Multi-event interface (like `ExecutionEventListener`) — speculative; nobody needs other events today
- Reuse `ExecutionEventListener` directly — semantics don't map; conflates execution model and conversation abstractions
**Rationale:** The request is for exactly one event. A `@FunctionalInterface` is the simplest thing that works, lambda-friendly for consumers. Adding a second SPI later for new events is cheap and non-breaking.
**Trade-offs:** If multiple conversation events are needed later, each requires a separate SPI rather than adding a default method to an existing interface.
**Sources:** `ExecutionEventListener.java` (existing multi-event pattern), issue #125 (suggested `@FunctionalInterface`)
**Exploration:** quick
**Status:** captured

## D2: Callback signature — what context does the listener receive?

**Choice:** `void onDispatch(ConversationState state, TerminationDecision decision, int dispatchCount, Duration elapsed)`
**Alternatives:**
- Minimal (no `elapsed`) — consumer must track timing externally
- Full `TerminationContext<ConversationState>` — couples conversation SPI to agentic-layer type; `results` list is heavy per-dispatch context the listener doesn't need
**Rationale:** `elapsed` is already computed at the call site for `TerminationContext`. Consumers broadcasting progress often need duration without tracking it themselves. Four params keeps the signature clean without dragging in foreign types.
**Trade-offs:** Slightly wider signature than the issue suggested. If a future consumer needs `List<AgentResult>`, they'd need the full `TerminationContext` or a new method — but that's an unlikely per-dispatch need.
**Sources:** `ConversationOrchestrator.java:127-131` (call site context), `TerminationContext.java` (available fields), issue #125 consumer (fsitrading#26 WebSocket push)
**Exploration:** quick
**Status:** captured
