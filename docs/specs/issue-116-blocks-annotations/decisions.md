# Decisions — issue-116-blocks-annotations

## D1: LC4j annotation relationship for blocks-annotations

**Choice:** Own all 8 orchestration pattern annotations (`@Supervisor`, `@Sequence`, `@Parallel`, `@Loop`, `@Conditional`, `@Debate`, `@Voting`, `@Htn`) in CaseHub's `io.casehub.blocks.annotations` package. No `langchain4j-agentic` annotation dependency. LC4j agents are integrated as first-class CaseHub workers at runtime, not at the annotation layer.

**Alternatives:**
- Adopt LC4j's 5 base annotations (`@SupervisorAgent`, `@SequenceAgent`, `@ParallelAgent`, `@LoopAgent`, `@ConditionalAgent`) and add CaseHub-unique patterns only — requires `langchain4j-agentic` compile dependency, creates semantic divergence (same annotation name, different execution model), build extension coexistence conflict (two processors for the same annotations)
- Adapter layer bridging LC4j annotations to CaseHub's ExecutionModel — most complex option, fragile translation between execution models, loses LC4j semantics without fully gaining CaseHub semantics

**Rationale:**

CaseHub's orchestration model and LC4j's orchestration model are architecturally distinct at the execution layer, not just superficially different:

- **LC4j agentic:** imperative control flow — supervisor delegates to sub-agents, sequence chains calls, parallel fans out. The execution model is a directed graph of agent invocations with fixed control flow.
- **CaseHub blocks:** five independently composable SPIs (routing, decomposition, activation, aggregation, termination) with pluggable execution backends (orchestrated push, choreographed event-driven). A CaseHub supervisor can have round-robin routing + majority-vote aggregation + convergence-based termination + custom decomposition — compositions LC4j's annotation attributes cannot express.

Sharing annotations across different execution models creates three structural problems:

1. **Build extension coexistence.** LC4j's `AgenticProcessor` auto-activates when `langchain4j-agentic` is on the classpath and processes `@SupervisorAgent` to generate LC4j agent beans. If CaseHub's build extension also processes `@SupervisorAgent` to generate `ExecutionModel<T>` beans, either: duplicate beans are generated, one processor must suppress the other (breaking pure LC4j agents in the same deployment), or both run with CDI disambiguation. CaseHub deployments already have LC4j agents alongside CaseHub-orchestrated agents — suppressing LC4j's processor breaks that coexistence.

2. **Attribute surface mismatch.** LC4j's `@SupervisorAgent` attributes express LC4j's model. CaseHub's supervisor is configured through five SPIs. CaseHub would either ignore most LC4j attributes (making the annotation a vestigial marker) or overload them with CaseHub semantics (confusing developers who expect LC4j behavior).

3. **Semantic lie.** A developer who reads LC4j documentation about `@SupervisorAgent` expects LC4j's supervisor behavior. In a CaseHub deployment, the same annotation would produce CaseHub behavior — different execution model, different runtime, different debugging path. Same name, different contract violates Principle of Least Surprise.

**Integration path (maximal interop, no migration):**

The collaboration boundary between LC4j and CaseHub is at the runtime layer, not the annotation layer:

| Layer | Owner | How they interop |
|-------|-------|-----------------|
| LLM client (`ChatModel`, `ChatRequest`) | LC4j | CaseHub uses directly via `AgentProvider` |
| Tool-augmented agents (`AiService`, `@RegisterAiService`) | LC4j | CaseHub bridges via `ChatModelAgentProvider` / `AgentProviderChatModel` |
| Orchestration patterns | Each owns their own | LC4j: imperative. CaseHub: composable 5-SPI. |
| Governance (oversight, trust, attestation) | CaseHub | No LC4j equivalent — CaseHub's differentiator |

An existing LC4j `@SupervisorAgent` is a CDI bean. CaseHub wraps it as a worker via `@Worker` + `AgentWorkerFunction`. CaseHub handles external orchestration (when to invoke, governance, trust routing, audit). LC4j handles internal orchestration (how the agent does its work). The LC4j agent automatically gets qhorus channel audit, ledger attestation chains, trust scoring, and oversight gates — these operate on the worker dispatch boundary, not the annotation layer.

No LC4j annotation migration is needed. Write in LC4j, deploy in CaseHub, get governance for free.

**Analogy:** Spring defines `@Transactional` and `@Repository` over Hibernate's `SessionFactory`. Spring doesn't reuse Hibernate's annotations for session management — the orchestration layers serve different purposes even though they share the persistence engine. CaseHub defines `@Supervisor` and `@Debate` over LC4j's `ChatModel`. Same principle — shared engine, distinct orchestration semantics.

**Trade-offs:**
- 5 additional annotation definitions and build step handlers (trivial — thin mappings to `Patterns.*().build()`)
- Developers must learn CaseHub annotation names for CaseHub orchestration (distinct from LC4j annotation names for LC4j orchestration)
- CaseHub cannot automatically benefit from new patterns LC4j adds to its agentic annotations (but CaseHub's patterns are architecturally different, so LC4j additions are unlikely to map directly)

**Sources:**
- Engine-annotations D1 decision (same conclusion for engine layer, with less LC4j overlap)
- `docs/specs/issue-909-engine-annotations/2026-08-16-annotation-driven-programming-model-design.md` §Layer 2
- `io.casehub.blocks.agentic.pattern.Patterns` — 8 pattern builders
- `io.casehub.blocks.agentic.model.ExecutionModel` — 5-SPI composition record
- Garden: GE-20260608-4c8108 — `AnnotationsImpliesAiServiceBuildItem` bridges LC4j annotations to AiService pipeline (demonstrates runtime integration without annotation adoption)
- Capability ownership: `casehub-platform-agent-api` bridges (`ChatModelAgentProvider`, `AgentProviderChatModel`)

**Depends on:** engine D1 (establishes the "own annotations" precedent at Layer 1)
**Exploration:** deep-analysis
**Status:** captured

## D2: Module structure — nested layout

**Choice:** Nested layout: `annotations/pom.xml` (parent aggregator), `annotations/runtime/` (annotation definitions + runtime types), `annotations/deployment/` (Quarkus build extension). Matches engine, work, and ledger.
**Alternatives:**
- Flat siblings (`annotations/` + `annotations-deployment/`) — only eidos uses this; casehubio/eidos#142 filed to align
**Rationale:** 3-of-4 existing annotation modules use nested. Consistency across CaseHub repos means developers navigate the same directory structure in every `*-annotations` module. Artifact IDs are unaffected by directory layout.
**Trade-offs:** None significant — purely a layout convention.
**Exploration:** quick
**Status:** captured

## D3: Pattern annotation target and product — dual-use ExecutionModel

**Choice:** Pattern annotations (`@Supervisor`, `@Debate`, etc.) are METHOD-level on separate pattern interfaces. The build extension generates `ExecutionModel<T>` CDI beans. These are dual-use: invocable standalone via `execute()` (no `@Case` required) AND referenceable as case workers via `@Worker(capability = "...")` pointing to the pattern's capability name.
**Alternatives:**
- Case-integrated only (patterns only meaningful inside `@Case`) — too restrictive; blocks patterns are used standalone today via `Patterns.*().execute()`
- Standalone only (never auto-wired into cases) — misses the primary enterprise use case where patterns are workers in case orchestration
**Rationale:** Matches how `Patterns.*()` builders work today — they produce `ExecutionModel` that can be executed directly or used as a worker function inside a case. The annotation layer mirrors this dual-use contract. Role annotations (`@Debater`, `@Voter`, `@Judge`) are parameter-level, defining agent participants. Governance meta-annotations (`@OversightGate`, `@TrustRouted`) compose onto the same method.
**Trade-offs:** The build extension must generate both a standalone CDI bean and a worker-compatible function from the same annotation — slightly more codegen, but both paths are thin wrappers over the same `ExecutionModel`.
**Exploration:** quick
**Status:** captured

## D4: Governance meta-annotations compose onto both @Worker and pattern methods

**Choice:** `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation` are METHOD-level annotations that compose onto both engine `@Worker` methods (Layer 1) and blocks pattern methods (`@Debate`, `@Supervisor`, etc., Layer 2). The blocks build extension detects context (is this on a `@Worker` or a pattern method?) and generates the appropriate wiring — `ActionRiskClassifier` chain for oversight, `TrustRoutingPolicyResolver` config for trust, etc.
**Alternatives:**
- Pattern methods only — forces engine `@Worker` governance through `@Customize`, fragmented developer experience
- Engine `@Worker` only — patterns would need `@Customize` for governance, defeating the progressive disclosure goal
**Rationale:** Unified governance surface. A developer applies `@OversightGate(MyClassifier.class)` and it works whether the target is a simple worker or a complex orchestration pattern. The build extension handles the wiring differences internally. This is the whole value proposition of blocks-annotations — governance annotations that compose uniformly.
**Trade-offs:** The blocks build extension must handle two composition contexts (engine worker method vs pattern method). The engine build extension runs first and produces worker/binding beans; the blocks build extension runs after and layers governance onto both engine-generated and blocks-generated beans.
**Depends on:** D3 (pattern annotations produce ExecutionModel)
**Exploration:** quick
**Status:** captured

## D5: Annotation naming — bare pattern names

**Choice:** Bare pattern names without suffix: `@Supervisor`, `@Sequence`, `@Parallel`, `@Loop`, `@Conditional`, `@Debate`, `@Voting`, `@Htn`. Package: `io.casehub.blocks.annotations`.
**Alternatives:**
- `@DebateAgent`, `@SupervisorAgent` — matches LC4j naming but creates import confusion when both are on the classpath
- `@CaseHubDebate`, `@CaseHubSupervisor` — maximum disambiguation, excessive verbosity
**Rationale:** Consistent with engine Layer 1 convention (`@Worker`, `@Case`, `@Goal` — no suffix). Distinct from LC4j's `@SupervisorAgent` — different package AND different name shape, making accidental import of the wrong one unlikely. Shorter, cleaner API surface.
**Trade-offs:** `@Supervisor` and `@SupervisorAgent` are similar enough that developers might conflate them. The package difference (`io.casehub.blocks.annotations` vs `dev.langchain4j.agentic.declarative`) and the dual-track strategy (ADR-0004) mitigate this. `@Parallel` and `@Conditional` have collision risk with Spring/JUnit annotations — accepted pre-GA, revisit if developers report friction. LC4j avoids this with the `Agent` suffix (`@ParallelAgent`, `@ConditionalAgent`).
**Exploration:** quick
**Status:** captured

## D6: @Decompose is a pattern attribute, not a governance meta-annotation

**Choice:** Decomposition strategy override is an attribute on the pattern annotation: `@Supervisor(decomposition = MyDecomposition.class)`. Not a separate `@Decompose` meta-annotation.
**Alternatives:**
- Separate `@Decompose(MyDecomposition.class)` meta-annotation — treats SPI replacement as a cross-cutting concern, which it isn't
- `@Customize` only — too hidden for something that's a direct pattern configuration
**Rationale:** Decomposition is intrinsic to the pattern, not a cross-cutting concern. Governance meta-annotations (`@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation`) ADD concerns that are orthogonal to the pattern. Decomposition REPLACES part of the pattern's internal configuration. The annotation attribute surface should reflect this distinction — governance composes, SPI configuration configures.
**Trade-offs:** Each pattern annotation that supports decomposition override needs the attribute. This is consistent with how the builders work — `Patterns.supervisor().decompose(strategy)`.
**Depends on:** D3 (pattern annotations and their attribute surface)
**Exploration:** quick
**Status:** captured

## D7: Convergence is a pattern attribute, not a separate @Convergence

**Choice:** Convergence configuration is expressed through pattern annotation attributes that map to existing builder methods: `@Debate(maxRounds = 5)` maps to `DebateBuilder.maxRounds(int)`. No separate `@Convergence` annotation. Complex convergence (custom `TerminationCondition`) uses `@Customize`.
**Alternatives:**
- Separate `@Convergence(policy = ..., threshold = 0.8, staleRounds = 3)` — reusable across patterns but adds annotation count for a concern that's intrinsic to the pattern
**Rationale:** Same principle as D6 — convergence is intrinsic to the debate/voting pattern, not a cross-cutting concern. It configures when the pattern terminates, which is part of the pattern's definition. Governance adds; configuration configures. Complex convergence policies use `@Customize` with the pattern builder.
**Depends on:** D6 (SPI configuration as attributes, not meta-annotations)
**Exploration:** quick
**Status:** captured

## D8: Examples — 3 domains extending engine#945 bases

**Choice:** Three example modules extending engine-annotations examples with blocks patterns and governance. Each example layers blocks annotations onto an existing engine case, demonstrating progressive disclosure.

| Example | Domain | Engine base | Blocks annotations |
|---------|--------|------------|-------------------|
| `incident-response-blocks` | Cybersecurity | engine#945 incident-response | `@Debate` (containment strategy), `@TrustRouted` (triage confidence), `@Attestation` |
| `aircraft-maintenance-blocks` | Aviation MRO | engine#945 aircraft-maintenance | `@OversightGate` (sign-off), `@Debate` (repair strategy), `@CbrRouted` |
| `wildfire-response-blocks` | Disaster mgmt | engine#945 wildfire-response | `@Voting` (multi-agency consensus), `@Htn` (multi-phase ops), `@OversightGate` (evacuation) |

Coverage: 3 pattern annotations (`@Debate`, `@Voting`, `@Htn`) + all 4 governance annotations across 3 domains. The 5 base patterns (`@Supervisor`, `@Sequence`, `@Parallel`, `@Loop`, `@Conditional`) are thin builder mappings with straightforward usage — covered by Javadoc examples and the consumer guide, not dedicated example modules.

**Alternatives:**
- 2 examples from issue (debate-annotated + governed-supervisor) — doesn't cover voting/HTN patterns
- All 5 engine#945 domains — scope-heavy for v1; remaining 2 (search-rescue, warehouse) are good follow-ups
- All 8 patterns as examples — the base 5 are simple enough that dedicated modules add bulk without teaching value
**Rationale:** 3 examples cover the patterns that have genuine compositional complexity (debate needs debaters + judge + convergence, voting needs evaluators + strategy, HTN needs task tree + decomposition). The base 5 are one-liners that map directly to builder calls — examples would demonstrate the annotation syntax, not the pattern. Each tells a story: "here's the engine case, here's what blocks adds." Developers see the value of governance annotations in context, not in isolation. LC4j's examples (stories, horoscopes) don't have governance requirements — CaseHub's examples demonstrate the differentiator.
**Depends on:** engine#945 (engine examples must ship first or in parallel)
**Exploration:** quick
**Status:** captured
