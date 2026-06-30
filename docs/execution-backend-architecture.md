# Execution Backend Architecture

How CaseHub's agentic orchestration framework maps execution models to
runtime backends — which patterns run through Quarkus Flow (CNCF Serverless
Workflow), which need custom drivers, and how langchain4j agents integrate
as first-class components.

**Companion documents:**
- Design spec: `docs/superpowers/specs/2026-06-30-agentic-orchestration-design.md`
- Research: `docs/agentic-orchestration-research.md`
- DSL conventions: `casehubio/parent docs/DSL-STYLE-GUIDE.md`
- Execution models epic: casehubio/engine#595

---

## The Two-Backend Model

Not all execution models are workflows. Some have fixed topology known at
definition time (sequential, parallel, loop, conditional). Others have
topology that emerges at runtime from state, guards, LLM decisions, or
reactive activation. These are fundamentally different execution problems.

CaseHub's compositional SPI layer (five concerns: routing, decomposition,
activation, aggregation, termination) sits above both backends. The DSL is
the same. The `ExecutionModel<T>` record is the same. The execution backend
differs based on what the pattern requires.

```
                    ExecutionModel<T>
                    (five SPIs + DSL)
                         │
              ┌──────────┴──────────┐
              │                     │
     Quarkus Flow Backend    Custom Driver Backend
     (workflow-shaped)       (runtime-adaptive)
              │                     │
   ┌──────────┴─────┐    ┌────────┴────────┐
   │   Sequential   │    │   Supervisor    │
   │   Parallel     │    │   HTN           │
   │   Loop         │    │   GOAP          │
   │   Conditional  │    │   Blackboard    │
   │                │    │   Debate        │
   │                │    │   Voting        │
   │                │    │   P2P           │
   │                │    │   Stigmergy     │
   │                │    │   Swarm         │
   │                │    │   Market/Auction│
   │                │    │   Hierarchical  │
   └────────────────┘    └────────────────┘
```

---

## Backend Selection Criteria

### Quarkus Flow Backend

Use when the pattern has **fixed topology known at definition time** — the
sequence of steps, fork/join points, and conditional branches can be declared
before execution starts.

**What Flow provides that a custom driver cannot:**
- **Durability** — workflow state persists across restarts (DB, K8s)
- **Observability** — DevUI visualisation, Mermaid diagrams, full traces
- **Recovery** — automatic retries, saga compensation, checkpoint resume
- **Build-time validation** — topology and input schema checked at compile time
- **Standards compliance** — CNCF Serverless Workflow spec

**What Flow cannot express:**
- Guard-evaluated decomposition (HTN method selection at runtime)
- Dependency graphs computed from agent I/O declarations (GOAP)
- Reactive activation on arbitrary state changes (blackboard, stigmergy)
- LLM-decided next step (supervisor)
- Convergence detection across rounds (debate)
- Bid collection and evaluation (market/auction)

### Custom Driver Backend

Use when the pattern has **topology that emerges at runtime** — what happens
next depends on the current state, LLM decisions, reactive events, or
computational planning that cannot be pre-declared as a workflow graph.

**What the custom driver provides:**
- Runtime-adaptive control flow (the five SPIs drive execution dynamically)
- Reactive event-driven activation (ChoreographedDriver)
- Imperative loop with dynamic routing (OrchestratedDriver)
- Full platform integration (engine, qhorus, work, eidos) in the execution loop

**What the custom driver lacks vs Flow:**
- No built-in durability (must persist to CaseContext manually)
- No workflow visualisation (must build observability via ExecutionEventListener)
- No automatic recovery (must implement via FailurePolicy + EventLog)

---

## Pattern-to-Backend Mapping

### Workflow-Shaped Patterns → Quarkus Flow

| Pattern | Flow Construct | Topology | Notes |
|---------|---------------|----------|-------|
| Sequential | `@SequenceAgent` / `agent()` steps | SEQUENCE | Agents execute in declared order |
| Parallel | `@ParallelAgent` / `fork()` | PARALLEL | Fork/join with aggregation |
| Loop | `@LoopAgent` / `forEach()` | LOOP | Exit condition evaluated per iteration |
| Conditional | `@ConditionalAgent` / `switchWhenOrElse()` | SEQUENCE + predicates | Predicate-based path selection |

These four patterns have direct langchain4j-agentic ↔ Quarkus Flow equivalents.
Quarkus Flow generates Serverless Workflow definitions at build time from the same
`@SequenceAgent`, `@ParallelAgent`, `@LoopAgent`, `@ConditionalAgent` annotations
that langchain4j uses. The `FlowPlanner` bridges langchain4j's `Planner` interface
to Flow's workflow execution.

**CaseHub integration path:** Pattern builders (`sequence()`, `parallel()`,
`loop()`, `conditional()`) generate Serverless Workflow definitions rather than
driving an in-process loop. The five SPIs inform workflow construction:
- Routing → agent ordering or conditional predicates
- Termination → exit conditions on loops
- Aggregation → fork/join result handling
- Activation → not needed (workflow engine handles step sequencing)
- Decomposition → not applicable (topology is fixed)

### Runtime-Adaptive Patterns → Custom Driver

| Pattern | Why Not Workflow | Driver Type | Key SPI |
|---------|-----------------|-------------|---------|
| Supervisor | LLM decides next agent at each step | Orchestrated | Routing (LlmSelectedRouting) |
| HTN | Guard-evaluated hierarchical decomposition | Orchestrated | Decomposition (StaticDecomposition with methods) |
| GOAP | Dependency graph computed from agent I/O | Orchestrated | Decomposition (GoalOrientedDecomposition) |
| Blackboard | Agents fire when inputs appear in state | Choreographed | Activation (OnInputReady) |
| Debate | Multi-round convergence with judge | Orchestrated | Aggregation (ConvergenceCheck) + Termination |
| Voting | Parallel + aggregation strategy | Orchestrated | Aggregation (MajorityVote, WeightedVote) |
| P2P | Reactive activation on dependency satisfaction | Choreographed | Activation (OnInputReady) |
| Stigmergy | Indirect coordination via environment | Choreographed | Activation (OnStateChange) |
| Swarm | Self-organisation via local rules | Choreographed | Activation (local rules) + Termination (EmergentStability) |
| Market/Auction | Bid collection and evaluation | Orchestrated | Routing (BidEvaluatedRouting) |
| Hierarchical | Multi-tier delegation with synthesis | Orchestrated | Decomposition (HierarchicalDecomposition) + Aggregation (UpwardSynthesis) |
| Contract Net | Announce → bid → award protocol | Orchestrated | Routing (BidEvaluatedRouting) |

### Borderline Patterns

Some patterns could theoretically map to either backend:

**Voting** — structurally parallel (fan-out all agents, collect results). Could
use `@ParallelAgent` with a custom output aggregator. But the aggregation
strategy (majority, weighted, scored, unanimous) is the core value, and
Quarkus Flow has no built-in aggregation SPI. Custom driver is simpler.

**Supervisor** — could be modelled as a loop workflow where each iteration
calls an LLM router that returns the next agent name, then dispatches to it.
But the LLM routing decision is the defining characteristic, and embedding
it in a workflow step loses the natural expression. Custom driver.

**Debate** — could be modelled as a loop with agent(debater1), agent(debater2),
function(judge) per round. But convergence detection across rounds requires
state that spans iterations, and the round structure (2+ debaters + judge) is
richer than a simple loop body. Custom driver.

---

## LangChain4j Integration Architecture

### Three Layers of Integration

CaseHub and langchain4j operate at three distinct levels. Each level has a
different integration strategy.

**Layer 1: Individual Agent Definition — langchain4j owns this**

langchain4j's `@Agent` annotation + proxy generation creates typed domain
interfaces that the LLM implements:

```java
interface Reviewer {
    @Agent("Review code for bugs and style issues")
    String review(@V("code") String code, @V("language") String language);
}

var reviewer = AgentServices.builder(Reviewer.class)
    .chatModel(model)
    .tools(lintTool)
    .build();

reviewer.review(code, "java");  // reads like domain code
```

Parameters map to `AgenticScope` state via `@V`. The proxy handles prompt
construction, tool invocation, and result extraction. This is about making
individual LLM calls look like typed method calls.

CaseHub does not replicate this — it uses langchain4j agents directly.

**Layer 2: Workflow Orchestration — Quarkus Flow owns this**

For workflow-shaped patterns, Quarkus Flow provides:
- Build-time annotation scanning (`@SequenceAgent`, etc.)
- Serverless Workflow definition generation via Gizmo bytecode
- `FlowPlanner` bridging langchain4j's `Planner` to Flow execution
- `AgenticScope` ↔ workflow context bidirectional state mapping
- DevUI visualisation, durability, recovery

CaseHub's pattern builders for workflow-shaped patterns should target Flow
rather than reimplementing the orchestration loop.

**Layer 3: Runtime-Adaptive Orchestration — CaseHub owns this**

For patterns that workflow engines cannot express, CaseHub provides:
- Five compositional SPIs (routing, decomposition, activation, aggregation, termination)
- `OrchestratedDriver` (imperative loop) and `ChoreographedDriver` (reactive)
- Cross-platform `AgentRef` spanning engine workers, qhorus channels, human tasks
- Eidos personality-aware routing via `AgentDescriptor`
- Platform-grade audit via `ExecutionEventListener` → EventLog + Ledger

This is where CaseHub is architecturally superior to both langchain4j and
Quarkus Flow — neither can express HTN decomposition with guard-evaluated
methods, GOAP dependency graphs, personality-matched routing, or human-in-
the-loop with SLA-governed work items.

### Agent Interoperability

A langchain4j `@Agent` proxy is a component in CaseHub compositions via
`AgentRef`:

```java
// langchain4j agent definition (Layer 1)
Reviewer reviewer = AgentServices.builder(Reviewer.class)
    .chatModel(model).build();

// CaseHub composition (Layer 3) using the langchain4j agent
supervisor(chatModel)
    .agents(
        AgentRef.external(ctx -> reviewer.review(ctx.getCode(), "java")),
        AgentRef.worker(implementorWorker),
        AgentRef.human(arbitrationRequest)
    )
    .build()
```

### State Interoperability via ContextBridge<T>

Engine's `ContextBridge<T>` (engine#203) bridges typed context between
execution environments:

| Bridge | Context Type | Direction |
|--------|-------------|-----------|
| `AgenticScopeBridge` | langchain4j `AgenticScope` | CaseHub ↔ langchain4j |
| `WorkingMemoryBridge` | Drools `WorkingMemory` | CaseHub ↔ Drools |
| `PlainMapBridge` | `Map<String, Object>` | CaseHub ↔ plain workers |
| `SubCaseBridge` | CaseContext (child) | CaseHub ↔ sub-cases |
| `WorkflowContextBridge` | Flow context | CaseHub ↔ Quarkus Flow |

`AgenticScopeBridge` (engine#419) makes `AgenticScope` writes visible to
CaseHub's stage gating, goal evaluation, and EventLog — without copying state.
All writes route through the provider abstraction regardless of originating
framework.

### Naming Changes (as of langchain4j 1.17+)

| Old Name | Current Name | Package |
|----------|-------------|---------|
| Cognisphere | `AgenticScope` | `dev.langchain4j.agentic.scope` |
| CognisphereOwner | `AgenticScopeOwner` | `dev.langchain4j.agentic.internal` |
| CognisphereRegistry | `AgenticScopeRegistry` | `dev.langchain4j.agentic.scope` |

The rename also introduced persistence support:
- `AgenticScopeStore` — persistence SPI
- `AgenticScopePersister` — serialisation
- `AgenticScopeKey` — typed state keys
- `AgenticScopeSerializer` — JSON codec
- `ResultWithAgenticScope` — result + scope pair

---

## LangChain4j Pattern Catalogue (as of 1.17.0, June 2026)

### Core Module (`langchain4j-agentic`)

| Pattern | Class | Topology | Flow Equivalent |
|---------|-------|----------|----------------|
| Sequential | `SequentialPlanner` | SEQUENCE | `@SequenceAgent` ✅ |
| Parallel | `ParallelPlanner` | PARALLEL | `@ParallelAgent` ✅ |
| Parallel Mapper | `ParallelMapperPlanner` | PARALLEL | Partial — needs custom |
| Loop | `LoopPlanner` | LOOP | `@LoopAgent` ✅ |
| Conditional | `ConditionalPlanner` (sic) | SEQUENCE | `@ConditionalAgent` ✅ |
| Supervisor | `SupervisorPlanner` | STAR | ❌ None |

### Patterns Module (`langchain4j-agentic-patterns`)

| Pattern | Class | Topology | Flow Equivalent |
|---------|-------|----------|----------------|
| GOAP | `GoalOrientedPlanner` | SEQUENCE | ❌ None |
| Blackboard | `BlackboardPlanner` | STAR | ❌ None |
| Debate | `DebatePlanner` | STAR | ❌ None |
| Voting | `VotingPlanner` | PARALLEL | ❌ None |
| P2P | `P2PPlanner` | STAR | ❌ None |

### In PR (not yet shipped)

| Pattern | PR | Flow Equivalent |
|---------|-----|----------------|
| HTN | #5584 | ❌ None |
| AgentRegistry | #5551 | ❌ None |

### Core Interface

All patterns implement `Planner`:

```java
interface Planner {
    Action firstAction(PlanningContext ctx);    // optional
    Action nextAction(PlanningContext ctx);     // required
    boolean terminated();
    Map<String, Object> executionState();       // crash recovery
    void restoreExecutionState(Map<String, Object> state);
    AgenticSystemTopology topology();           // SEQUENCE | PARALLEL | STAR | LOOP | ROUTER
}
```

`Action` is sealed: `call(AgentInstance...)`, `noOp()`, `done()`, `done(result)`.

`FlowPlanner` in Quarkus Flow implements this interface, bridging langchain4j's
planning abstraction to Serverless Workflow execution.

---

## Quarkus Flow Agentic Support

### Build-Time Generation

Quarkus Flow scans langchain4j annotations at build time and generates
concrete `Flow` subclasses via Gizmo bytecode generation:

```
@SequenceAgent → GeneratedXxxAgenticFlow extends SequentialAgenticFlow
@ParallelAgent → GeneratedXxxAgenticFlow extends ParallelAgenticFlow
@LoopAgent     → GeneratedXxxAgenticFlow extends LoopAgenticFlow
@ConditionalAgent → GeneratedXxxAgenticFlow extends ConditionalAgenticFlow
```

Generated workflows are visible in DevUI before first execution. Input
schemas auto-generate from `@V("param")` annotations (JSON Schema Draft-7).

### Runtime Classes

| Class | Pattern | Key Mechanism |
|-------|---------|--------------|
| `SequentialAgenticFlow` | Sequential | Index-based agent execution |
| `ParallelAgenticFlow` | Parallel | Fork/join branches |
| `ConditionalAgenticFlow` | Conditional | `@ActivationCondition` predicates |
| `LoopAgenticFlow` | Loop | `@ExitCondition` evaluation per iteration |

Each has a `Runtime*` variant for programmatic (non-annotation) construction.

### State Flow

AgenticScope ↔ Workflow Context mapping is bidirectional:
- `AgenticScope.state()` maps directly to Workflow Global Context
- Standard workflow tasks (JQ expressions, HTTP calls) can read/write AI memory
- No manual marshaling required

### Manual DSL

For workflows not defined via annotations, the `agent()` step integrates
langchain4j agents as first-class workflow tasks:

```java
workflow("newsletter").tasks(
    agent("draft", drafter::write, Request.class),
    emitJson("ready", "review.required", Draft.class),
    listen("review", toOne(consumed("review.done"))),
    switchWhenOrElse(h -> ok(h), "send", "revise", Review.class),
    function("revise", editor::edit, Review.class).then("ready"),
    consume("send", draft -> mail.send(draft), Draft.class)
).build()
```

This mixes AI agents with HTTP calls, event listening, human-in-the-loop,
and conditional routing in a single durable workflow — something langchain4j's
standalone patterns cannot express.

---

## CaseHub's Architectural Position

### What CaseHub Adds That Neither Framework Has

| Capability | langchain4j | Quarkus Flow | CaseHub |
|-----------|-------------|-------------|---------|
| **Distributed agents** | JVM-local + A2A | Workflow tasks | WorkerProvisioner (JVM, Docker, remote, human) |
| **Agent identity** | Name + description | None | Eidos 4-layer descriptor (slot, capability, disposition) |
| **Personality routing** | None | None | Belbin, DISC, Thomas-Kilmann vocabulary matching |
| **Capability health** | None | None | Ready/Degraded/Unavailable/EpistemicallyWeak probe |
| **Learned routing** | None | None | DECLINE/FAIL pattern aggregation via ledger |
| **Human tasks** | None | listen() + events | Work — first-class items, SLA, claim/escalate |
| **Structured channels** | None | None | Qhorus — typed channels, speech acts, commitments |
| **Compliance audit** | AgentMonitor | Workflow traces | EventLog + Ledger (EU AI Act Art.12) |
| **Scope control** | None | None | CMMN Stages — lifecycle-gated eligibility |
| **HTN decomposition** | PR #5584 | None | TaskNode + DecompositionMethod + guard evaluation |
| **Compositional SPIs** | Single Planner | Fixed topologies | Five independent concerns, any composition |

### The Three-Layer Stack

```
┌─────────────────────────────────────────────────────┐
│  CaseHub Application Layer                          │
│  (drafthouse, quarkmind, clinical, etc.)            │
│  Domain-specific wiring, agent definitions          │
├─────────────────────────────────────────────────────┤
│  CaseHub Blocks — Compositional Orchestration       │
│  Five SPIs, ExecutionModel<T>, pattern builders     │
│  Custom drivers for runtime-adaptive patterns       │
│  Flow integration for workflow-shaped patterns      │
│  Cross-platform AgentRef (engine+qhorus+work+eidos) │
├─────────────────────────────────────────────────────┤
│  Platform Foundations                               │
│  ┌──────────┬──────────┬────────┬────────┬────────┐ │
│  │  Engine  │  Qhorus  │  Work  │ Ledger │ Eidos  │ │
│  │ Cases,   │ Channels,│ Tasks, │ Audit, │ Agent  │ │
│  │ Context, │ Messages,│ SLA,   │ Comply,│ Identity│ │
│  │ Goals,   │ Speech   │ Human  │ Attest │ Persona│ │
│  │ Stages   │ Acts     │ Route  │        │ Health │ │
│  └──────────┴──────────┴────────┴────────┴────────┘ │
├─────────────────────────────────────────────────────┤
│  External Frameworks                                │
│  ┌──────────────────┬───────────────────────────┐   │
│  │  LangChain4j     │  Quarkus Flow             │   │
│  │  @Agent proxies, │  Serverless Workflow,      │   │
│  │  AgenticScope,   │  durability, recovery,     │   │
│  │  tools, memory   │  DevUI, build-time gen     │   │
│  └──────────────────┴───────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### Why langchain4j's Standalone Workflow Patterns Are Redundant

langchain4j-agentic provides `SequentialPlanner`, `ParallelPlanner`,
`LoopPlanner`, and `ConditionalPlanner` as in-process Java orchestrators.
These are convenience implementations for quick starts — they run agents
in a loop with no durability, no recovery, and no observability beyond
`AgentMonitor`.

Quarkus Flow provides the same four patterns (`@SequenceAgent`,
`@ParallelAgent`, `@LoopAgent`, `@ConditionalAgent`) backed by CNCF
Serverless Workflow with full enterprise infrastructure. The DSL is
nearly identical. Mario Fusco built both.

For CaseHub:
- **Do not use** langchain4j's standalone workflow planners — they
  reimagine what Quarkus Flow already provides properly
- **Do use** Quarkus Flow for workflow-shaped patterns
- **Do use** langchain4j's `@Agent` interfaces for individual agent
  definition — they're clean, typed, and compose naturally
- **Do use** CaseHub's custom drivers for patterns Flow cannot express

### Why CaseHub's Custom Drivers Are Not Redundant

The custom drivers (`OrchestratedDriver`, `ChoreographedDriver`) handle
patterns that no workflow engine can express:

- **HTN** — the task tree is expanded at runtime via guard evaluation.
  Which decomposition method fires depends on the current state. The
  resulting execution sequence is not knowable at definition time.

- **GOAP** — the dependency graph between agents is computed from their
  declared inputs/outputs. The shortest path to the goal state is a
  graph search result, not a declared workflow.

- **Supervisor** — the LLM decides which agent to invoke next based on
  accumulated context. Each step's routing is an LLM inference call,
  not a pre-declared path.

- **Blackboard/P2P/Stigmergy** — agents fire reactively when their
  activation conditions are met by state changes. There is no step
  sequence — the execution order emerges from the data.

- **Debate** — convergence detection requires evaluating all prior
  round results against a judge or metric. The number of rounds is
  not known in advance.

These patterns need the five SPIs driving execution dynamically at
runtime. The drivers are the mechanism; the SPIs are the abstraction.

---

## Implementation Roadmap

### Phase 1 (Complete) — SPI Framework

Five SPI interfaces, sealed decision types, `ExecutionModel<T>`,
`OrchestratedDriver`, `ChoreographedDriver`, eight pre-composed pattern
builders. All generic over `<T>` for `ContextBridge<T>` readiness.

### Phase 2 — ContextBridge<T> Integration

When engine#203 ships, activate the generic `<T>` with concrete bridges:
- `AgenticScopeBridge` for langchain4j agents
- `WorkingMemoryBridge` for Drools rules
- Typed domain records via `@CaseFile`

### Phase 3 — Quarkus Flow Backend

For workflow-shaped pattern builders (`sequence()`, `parallel()`, `loop()`,
`conditional()`):
- Generate Serverless Workflow definitions from `ExecutionModel<T>`
- Bridge to Quarkus Flow's `FlowPlanner` for execution
- Map CaseHub's aggregation/termination SPIs to Flow output handling
- Preserve the DSL — builders return `ExecutionModel<T>` which can
  target either backend

### Phase 4 — Advanced SPI Implementations

Runtime-adaptive pattern implementations:
- `LlmSelectedRouting` — supervisor (needs ChatModel)
- `GoalOrientedDecomposition` — GOAP (needs agent I/O declarations)
- `DispositionAwareRouting` — Belbin/DISC personality matching (needs eidos)
- `ConvergenceCheck` — debate (needs judge agent or metric)
- Agent dispatch for non-External variants (WorkerAgent → WorkerExecutionManager,
  ChannelAgent → MessageService, HumanAgent → WorkBroker)

### Phase 5 — LangChain4j Agent Interop

First-class `LangChain4jAgent` variant on `AgentRef`:
- Wraps langchain4j `@Agent` proxy for direct invocation
- Bridges `AgenticScope` state via `ContextBridge<T>`
- Extracts `AgentDescriptor` from agent metadata for routing

---

## Design Principles

1. **langchain4j for agent definition, CaseHub for agent orchestration.**
   Don't replicate proxy generation. Use `@Agent` interfaces.

2. **Quarkus Flow for workflow-shaped patterns, custom drivers for the rest.**
   Don't reimagine workflow infrastructure. Don't force non-workflow
   patterns into workflow shapes.

3. **The five SPIs are the abstraction layer above both backends.**
   The DSL and `ExecutionModel<T>` are backend-agnostic. Backend
   selection is an implementation detail of the builder or driver.

4. **ContextBridge<T> is the state integration point.**
   All state interop between CaseHub, langchain4j, Drools, and
   Quarkus Flow routes through the bridge. No framework-specific
   state access in SPIs.

5. **Platform capabilities compose, not duplicate.**
   Eidos routing, ledger audit, work tasks, qhorus channels — these
   are CaseHub's value. They compose into the five SPIs at the blocks
   level. Individual foundation modules don't know about orchestration.

---

## Related Issues

- engine#595 — Execution Capability Models epic (16 patterns)
- engine#596–#606 — Individual execution model issues
- engine#203 — ContextBridge<T> protocol
- engine#419 — CaseContextProvider for AgenticScope interop
- engine#446 — WorkingMemoryBridge for Drools
- blocks#4 — Refactor conversation package as debate composition
- blocks#6 — Extract AbstractExecutionDriver (driver duplication)
- blocks#7 — Populate ActivationContext fields in drivers
- blocks#8 — Batch minor findings from code review
