# ConversationOrchestrator Design

Refs casehubio/blocks#91

## Context

CaseHub applications (drafthouse, devtown, clinical) need autonomous multi-agent
conversations over qhorus channels. Today, agents must manually call MCP tools
each turn — there is no reactive loop. The platform primitives for this exist in
blocks but have never been composed into a reusable orchestrator.

A platform audit (2026-08-04) confirmed that all building blocks are in this repo.
What's missing is the composition.

## Design Decisions

### Reuse existing TerminationCondition — no new termination SPI

`TerminationCondition<T>` is generic over `T`. The conversation orchestrator uses
`TerminationCondition<ConversationState>` directly. `TerminationContext<T>` wraps
state with `iterationCount` (maps to total agent dispatches), `elapsed`, and
`List<AgentResult>` — all available in the conversation loop.

Conversation-specific termination conditions (`AllAgreedTermination`,
`ContestedEscalation`) read what they need from `context.state()`. Round count
is derived from `ConversationState` (tracked by the projection via memos), not
from `iterationCount`. `MaxIterationsTermination` still works as a universal
safety valve counting total dispatches.

`ConvergenceTermination` (existing) already proves this pattern — it extracts
`ConversationState` from the generic `T` and evaluates epistemic/convergence state.

### New TurnPolicy SPI — turn-taking is not routing

`RoutingStrategy<T>` answers "given scored candidates, which is best for this
task?" — a selection/decision problem with Unresolvable failure modes.

`TurnPolicy` answers "given conversation state, whose turn is it?" — a protocol
application. Differences:

| Concern | RoutingStrategy | TurnPolicy |
|---------|----------------|------------|
| Input | Scored candidate pool | Conversation state + triggering event |
| Logic | Selection/ranking | Deterministic protocol |
| Failure | Unresolvable (no candidate fits) | Empty list (silence — valid) |
| Return | Selected / Unresolvable / Escalate | List of participants |

Unifying these would over-abstract both. TurnPolicy is a first-class SPI.

### New composition root — not a driver subclass

The agentic `ExecutionDriver` runs a synchronous five-phase loop with shared
context passed to all agents. The conversation orchestrator has fundamentally
different execution semantics:

- **Per-agent context rendering** — each agent gets different context from its
  observation partition, not a shared `T`
- **Event-driven feedback** — agent responses feed back into the loop as new
  events, triggering further agent invocations
- **Turn-based dispatch** — agents are invoked based on conversation protocol,
  not candidate routing

Extending `AbstractExecutionDriver` would mean overriding `runLoop`,
`dispatchAgents`, and `executeIteration` — leaving only `invokeAgent` and
notification helpers. Inheritance without meaningful reuse.

### PartitionedObservationService is primary — SummarisationRunner is optional

`PartitionedObservationService` handles per-agent buffering, visibility routing,
and tiered rendering. The orchestrator controls drain timing based on turn policy
(turn-based, not time-based).

`SummarisationRunner` adds temporal windowing and compaction — useful for long
conversations but not required for the core loop. Consumers can wire it as an
additional layer externally.

### converse() over feed()

The primary use case is autonomous agent debates: a message arrives, agents go
back and forth until termination. `converse(MessageView)` runs the full loop
internally. Interactive mode (human + agents interleaving) is a future layer.

## Package

`io.casehub.blocks.conversation.orchestration`

## Type Inventory

### New Types

| Type | Kind | What it does |
|------|------|-------------|
| `ConversationOrchestrator` | Class | Composition root — iterative queue-based conversation loop |
| `ConversationOutcome` | Record | Final state, termination decision, agent results, metrics |
| `TurnPolicy` | Interface | SPI: determines which agents respond next |
| `TurnContext` | Record | Turn-relevant fields extracted from the triggering message |
| `AgentParticipant` | Record | Agent identity + conversational role + system prompt |
| `PromptAssembler` | @FunctionalInterface | SPI: assembles per-agent prompts from observation + state |
| `ResponseMessageBuilder` | @FunctionalInterface | Converts agent results to MessageView responses |
| `RoundRobinTurnPolicy` | Class | Strict alternation through participants |
| `AddressedTurnPolicy` | Class | Respond when message target matches agent role |
| `PointAddressedTurnPolicy` | Class | Respond to unresolved points addressed to you |
| `FreeTurnPolicy` | Class | All agents except sender respond |
| `AllAgreedTermination` | Class | TerminationCondition — all open points resolved |
| `SupervisorTermination` | Class | TerminationCondition — supervisor role signals end |
| `ContestedEscalation` | Class | TerminationCondition — disputed points escalate to human |
| `CompositeTermination` | Class | TerminationCondition — first non-Continue decision wins |

### Reused Types (no changes)

- `TerminationCondition<T>`, `TerminationContext<T>`, `TerminationDecision` — `agentic.termination`
- `AgentRef`, `AgentResult`, `AgentInvoker<T>` — `agentic` / `agentic.model`
- `MaxIterationsTermination` — `agentic.termination`
- `PartitionedObservationService<E,K>`, `VisibilityPolicy<E,K>`, `PartitionedDrain<K>`, `ObservationResult` — `summarisation.observation`
- `ConversationProjection`, `ConversationState`, `ConversationRenderer` — `conversation`

## SPI Definitions

### TurnPolicy

```java
public interface TurnPolicy {
    List<AgentParticipant> nextResponders(
        ConversationState state,
        TurnContext context,
        List<AgentParticipant> participants
    );
}
```

Synchronous. Returns a list (possibly empty — silence is valid). The
orchestrator passes the full participant list each call. Implementations
filter/select from it.

No `Uni` return — all four proposed implementations are deterministic pure
functions of state. Pre-release stage; async can be added later if a
moderator-based policy needs it.

### TurnContext

```java
public record TurnContext(
    String senderId,
    @Nullable String targetId,
    String entryType,
    Map<String, String> metadata
) {}
```

Extracted from `MessageView` by the orchestrator. Keeps `TurnPolicy`
decoupled from qhorus types.

### AgentParticipant

```java
public record AgentParticipant(
    AgentRef agentRef,
    String role,
    String systemPrompt
) {
    public String agentId() { return agentRef.name(); }
}
```

`role` is used by turn policies for role-based matching (e.g., "REV", "IMP",
"SUPERVISOR"). `systemPrompt` is per-agent; used during prompt assembly.
`agentId()` delegates to `agentRef.name()` — the key for observation partition
mapping and turn policy matching.

### PromptAssembler

```java
@FunctionalInterface
public interface PromptAssembler {
    String assemble(AgentParticipant agent,
                    PartitionedDrain<String> drain,
                    ConversationState state);
}
```

Called once per agent invocation. Receives the full `PartitionedDrain<String>`
from `PartitionedObservationService.drain()` — this includes the current
partition's `ObservationResult` plus remembered partitions from zones the
agent previously visited. Default implementation concatenates:

1. Agent's system prompt
2. Conversation state rendered via `ConversationRenderer` (structured view —
   points, threads, statuses)
3. Current partition observation text (`drain.currentPartition().renderedText()`)
4. Remembered partition summaries (if any — compacted context from prior zones)

Apps override to inject domain context (document content, selection scope) or
to shift rendering balance for long conversations.

### ResponseMessageBuilder

```java
@FunctionalInterface
public interface ResponseMessageBuilder {
    MessageView build(AgentParticipant agent,
                      AgentResult result,
                      ConversationState currentState);
}
```

Converts agent output to a `MessageView` the projection can fold. Default wraps
the output with conversation protocol sentinel metadata (entry type, role, round).
The default determines entry type from the agent's role and the conversation
context — responses to existing points use the role's default response type
(e.g., a reviewer COUNTERs or AGREEs), new contributions use the role's
initiation type. Apps override for domain-specific entry type determination.

## ConversationOrchestrator

### Constructor

```java
public class ConversationOrchestrator {

    public ConversationOrchestrator(
        ConversationProjection projection,
        PartitionedObservationService<MessageView, String> observationService,
        TurnPolicy turnPolicy,
        TerminationCondition<ConversationState> terminationCondition,
        AgentInvoker<String> agentInvoker,
        PromptAssembler promptAssembler,
        ResponseMessageBuilder responseBuilder,
        Consumer<MessageView> responseDispatcher,
        List<AgentParticipant> participants
    ) { ... }

    public Uni<ConversationOutcome> converse(MessageView triggeringMessage) { ... }

    public void terminate() { ... }
}
```

| Dependency | Role |
|-----------|------|
| `projection` | Updates ConversationState from messages (fold) |
| `observationService` | Per-agent context windowing and rendering |
| `turnPolicy` | Determines who responds next |
| `terminationCondition` | Determines when to stop |
| `agentInvoker` | Calls agents with assembled prompts |
| `promptAssembler` | Builds per-agent prompts from observation + state |
| `responseBuilder` | Converts agent results to channel messages |
| `responseDispatcher` | Sink — publishes agent responses to the channel |
| `participants` | Registered agents — immutable after construction |

Participants are passed at construction time (not via a mutable
`registerParticipant()` method). The constructor registers each participant
as an observer on `PartitionedObservationService` using `agentId()` as observer
ID with a default partition key (the `agentId()` string itself — single-partition
default; apps using multi-partition observation configure the service externally).

`terminate()` sets a `volatile boolean` flag checked by the loop after each
dispatch. Safe to call from any thread — the loop exits cleanly at the next
termination check point.

### Internal Loop

`converse()` runs an iterative queue-based loop:

```
1. state = projection.identity()   — empty initial conversation state
2. Enqueue triggering message
3. While queue is not empty AND not terminated:
   a. Dequeue message
   b. state = projection.apply(state, message)
   c. observationService.publishEvent(message)
   d. turnContext = extractContext(message)
   e. responders = turnPolicy.nextResponders(state, turnContext, participants)
   f. For each responder:
      i.   drain = observationService.drain(agentId, partition, now)
      ii.  prompt = promptAssembler.assemble(agent, drain, state)
      iii. result = agentInvoker.invoke(agentRef, prompt)
      iv.  If result.status() == FAILURE: record result, skip to next responder
      v.   responseMessage = responseBuilder.build(agent, result, state)
      vi.  state = projection.apply(state, responseMessage)  — update before termination
      vii. observationService.publishEvent(responseMessage)
      viii.responseDispatcher.accept(responseMessage)
      ix.  enqueue responseMessage
      x.   Check termination (with updated state) → if terminal, break
4. Return ConversationOutcome
```

**Loop invariants:**

- **State is always current.** The response message is applied to state (step
  3.f.vi) and published to observation (step 3.f.vii) before termination is
  checked (step 3.f.x). Termination conditions always evaluate the latest state.

- **Drain is safe.** `PartitionedObservationService.drain()` is called before
  invocation (step 3.f.i). If invocation fails (step 3.f.iv), the drained
  events are lost for that agent. This is acceptable — the events were already
  consumed into the prompt. On retry (if the invoker retries internally), the
  agent gets a fresh drain. The alternative (drain-after-success) would require
  the prompt to be assembled speculatively, which defeats the purpose of
  demand-driven rendering.

- **Multi-responder visibility.** When the turn policy returns multiple
  responders for the same message, they are invoked sequentially (step 3.f
  iterates). Each responder's response is applied to state and published to
  observation before the next responder is invoked. This means responder B
  sees responder A's response in its observation drain.

- **Queue growth is bounded by termination.** With `FreeTurnPolicy`, N agents
  each produce a response, each response triggers N-1 more responses. This is
  geometric growth, bounded by `MaxIterationsTermination` (counts total
  dispatches). Without a safety valve, `FreeTurnPolicy` can run indefinitely.
  `CompositeTermination` with `MaxIterationsTermination` is mandatory when
  using `FreeTurnPolicy`.

Termination is evaluated after each agent dispatch (not at round boundaries).
This gives fine-grained control — `AllAgreedTermination` can stop mid-round
when the last point is agreed.

Response messages feed back into the queue. The agent's response updates
`ConversationState` via the projection, is published to the observation service
(so other agents see it), and triggers `turnPolicy` (which may nominate more
agents). This is the self-driving loop.

**TerminationContext construction.** The orchestrator builds
`TerminationContext<ConversationState>` for each check:
- `state` — the current `ConversationState` (updated after each dispatch)
- `iterationCount` — total dispatches so far (monotonically increasing)
- `elapsed` — wall-clock duration since `converse()` was called
- `results` — all `AgentResult`s accumulated so far (including failures)

### ConversationOutcome

```java
public record ConversationOutcome(
    ConversationState finalState,
    TerminationDecision terminationDecision,
    List<AgentResult> agentResults,
    int dispatchCount,
    Duration elapsed
) {}
```

## Standard TurnPolicy Implementations

### RoundRobinTurnPolicy

Strict alternation. Determines "next" by checking who sent the most recent point
in `ConversationState.points()` and advancing to the next participant in list
order. No internal mutable state — purely derived from conversation state.

### AddressedTurnPolicy

Respond when the message's target matches an agent's role. Returns empty list
on null target (silence). Silence is intentional — in an addressed protocol,
nobody responds to unaddressed messages.

### PointAddressedTurnPolicy

Respond to unresolved points addressed to you. Scans `state.points()` for
OPEN/ACTIVE points, checks thread entries for role-based addressing, skips
points the participant has already responded to.

### FreeTurnPolicy

Returns all participants except the sender. Used when a supervisor or external
mechanism mediates — all agents contribute, turn order doesn't matter.

## Standard TerminationCondition Implementations

All implement `TerminationCondition<ConversationState>`.

### AllAgreedTermination

Constructor takes a `Set<String>` of resolved statuses (e.g., AGREED, VERIFIED).
Returns `Complete` when all points have a resolved status. Returns `Continue`
when no points exist yet or any point is unresolved.

### SupervisorTermination

Constructor takes supervisor role string. Scans recent points for a termination
signal from the supervisor role. Returns `Complete` with the supervisor's summary.

### ContestedEscalation

Constructor takes `maxDisputeRounds`. Checks for DISPUTED points that have been
disputed beyond the threshold. Returns `Escalate` — the conversation didn't fail,
it needs human judgment.

### CompositeTermination

Constructor takes an ordered list of conditions. Evaluates all; first
non-Continue decision wins. Typical composition:

```java
new CompositeTermination(List.of(
    new MaxIterationsTermination<>(50),
    new AllAgreedTermination(Set.of("AGREED", "VERIFIED")),
    new ContestedEscalation(3)
))
```

## Error Handling

### Agent invocation failure

Log and skip. The failed agent's response is not enqueued — state and
observation service are not updated for a failed dispatch. Other agents
continue. No retry — retries belong in the `AgentInvoker` implementation.
Failed `AgentResult` is included in `ConversationOutcome.agentResults()`.

If all agents fail, the queue drains without new messages and the conversation
terminates naturally. The outcome shows zero successful dispatches.

### Prompt assembly failure

`PromptAssembler` is per-agent, not infrastructure. If assembly fails for one
agent (e.g., renderer error on that agent's observation data), log the error,
record a failure `AgentResult`, and skip to the next responder. Do not
propagate — other agents can still be invoked.

### Turn policy returns empty

Normal — nobody needs to respond. State is updated, observation service
receives the event, but no agents are invoked. Queue drains to next item.
If the queue empties with no invocations, the conversation completes.

### Projection failure

`ConversationProjection.apply()` catches and logs malformed messages (returns
unchanged state). If projection fails, the observation service still receives
the event (step 3.c runs regardless) — observation and state can temporarily
diverge, but subsequent successful projections restore consistency. The
orchestrator does not stop the conversation on projection failure.

### TerminationDecision handling

`TerminationDecision` is a sealed interface with four variants:
- `Continue` — loop continues
- `Complete(result)` — conversation completed successfully
- `Failed(reason)` — conversation failed (e.g., all agents unreachable)
- `Escalate(reason)` — needs human intervention

All four are handled. `Failed` and `Escalate` both terminate the loop and
are reflected in `ConversationOutcome.terminationDecision()`.

### Infrastructure failures propagate

- `TerminationCondition` throws → propagates (broken check = bug)
- `ResponseDispatcher` throws → propagates (response didn't reach channel)

Fail fast on infrastructure, fail soft on agent-level errors (invocation,
prompt assembly).

## Consumer Wiring Example (Drafthouse)

```java
// In DebateChannelBackend

var observationService = new PartitionedObservationService<>(
    tieredRenderer, fullVisibilityPolicy, MessageView::createdAt, CONVERSATION);

var participants = List.of(
    new AgentParticipant(reviewerRef, "REV", reviewerSystemPrompt),
    new AgentParticipant(implementorRef, "IMP", implementorSystemPrompt)
);

var orchestrator = new ConversationOrchestrator(
    new DebateChannelProjection(),
    observationService,
    new RoundRobinTurnPolicy(),
    new CompositeTermination(List.of(
        new MaxIterationsTermination<>(20),
        new AllAgreedTermination(Set.of("AGREED", "VERIFIED")),
        new ContestedEscalation(3)
    )),
    debateAgentInvoker,
    new DebatePromptAssembler(documentContent, selectionScope),
    debateResponseBuilder,
    message -> messageService.post(channelId, message),
    participants
);

ConversationOutcome outcome = orchestrator.converse(triggeringMessage)
    .await().indefinitely();
```

### What the consumer provides

| Concern | What |
|---------|------|
| Projection | Subclass with domain vocabulary (RAISE/AGREE/COUNTER) |
| VisibilityPolicy | Full visibility — all agents see all messages |
| PromptAssembler | Injects document content and selection scope |
| AgentInvoker | Wraps existing DebateAgentProvider |
| ResponseMessageBuilder | Domain-specific message formatting |
| Response sink | Posts to qhorus channel |
| Agent config | System prompts per role |

### What the consumer stops doing

- Manual MCP tool invocation per turn
- Turn-taking logic in app code
- Termination checking in app code
- Context assembly in app code

## Testing Strategy

### Unit tests — per SPI implementation

| Test class | Covers |
|-----------|--------|
| `RoundRobinTurnPolicyTest` | Alternation order, wrapping, sender exclusion |
| `AddressedTurnPolicyTest` | Target matching, null target, unknown target |
| `PointAddressedTurnPolicyTest` | Open point detection, already-responded filtering |
| `FreeTurnPolicyTest` | All-except-sender, single participant |
| `AllAgreedTerminationTest` | All resolved, partial, no points, custom status sets |
| `SupervisorTerminationTest` | Signal detection, wrong role ignored |
| `ContestedEscalationTest` | Dispute round counting, escalation threshold |
| `CompositeTerminationTest` | First-wins ordering, all-continue passthrough |
| `DefaultPromptAssemblerTest` | Prompt concatenation structure |

### Integration tests — orchestrator end-to-end

Mock `AgentInvoker<String>` with scripted responses. Real projection, real
observation service, real turn/termination policies.

| Test | Scenario |
|------|----------|
| `twoAgentDebate_roundRobin_maxRounds` | A and B alternate, MaxIterations stops |
| `twoAgentDebate_convergence` | A raises point, B agrees, AllAgreed completes |
| `threeAgent_addressed` | A addresses B, B responds, C silent |
| `disputeEscalation` | Dispute exceeds threshold, escalation fires |
| `agentFailure_skipsAndContinues` | One invoker fails, other continues |
| `emptyResponders_queueDrains` | Turn policy returns empty, settles |
| `supervisorTermination` | Free policy, supervisor signals end |
| `promptAssembly_perAgentContext` | Each agent gets different observation |
| `responseDispatcher_calledPerResponse` | Sink receives each response in order |

## Implementation Order

1. `TurnPolicy` SPI + `TurnContext` + `AgentParticipant` records
2. Standard turn policy implementations (4 classes + tests)
3. `PromptAssembler` SPI + default implementation
4. `ResponseMessageBuilder` SPI + default implementation
5. Standard termination implementations (4 classes + tests)
6. `ConversationOrchestrator` + `ConversationOutcome`
7. Orchestrator integration tests
8. Consumer guide update — wiring into ChannelBackend

## Dependencies

No new external dependencies. All types come from existing blocks dependencies:

- `casehub-qhorus-api` — `MessageView` (compile, already present)
- `casehub-platform-agent-api` — `AgentProvider` (provided, already present)
- `io.smallrye.reactive:mutiny` — `Uni` (provided, already present)

## Downstream Consumers

- `casehubio/drafthouse#71` — first consumer (debate channels)
- devtown (code review conversations) — future
- clinical (case discussions) — future

## What This Does NOT Cover

- Qhorus changes — no changes needed; we consume existing SPIs
- Application-level MCP tools — each app owns its own tool surface
- Interactive mode (human + agents interleaving) — future layer on `converse()`
- SummarisationRunner integration — optional temporal compaction, not core
- Claudony integration — depends on protocol settled here
