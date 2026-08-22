# 0004 — Own orchestration pattern annotations

Date: 2026-08-22
Status: Accepted

## Context and Problem Statement

CaseHub's `casehub-blocks-annotations` module needs annotation equivalents for 8 orchestration patterns (supervisor, sequence, parallel, loop, conditional, debate, voting, HTN). LangChain4j's `langchain4j-agentic` provides declarative annotations for 7 core patterns (`@SupervisorAgent`, `@SequenceAgent`, `@ParallelAgent`, `@ParallelMapperAgent`, `@LoopAgent`, `@ConditionalAgent`, `@PlannerAgent`) and `langchain4j-agentic-patterns` provides runtime `Planner` implementations for advanced patterns: `DebatePlanner`, `VotingPlanner`, `GoalOrientedPlanner` (GOAP), `BlackboardPlanner`, and `P2PPlanner`. The overlap covers ALL of CaseHub's pattern vocabulary — not just the base five. Both systems implement the same patterns with architecturally different execution models. CaseHub must decide whether to adopt LC4j's annotations or define its own — and how to maximise interoperability for the broader LC4j community.

## Decision Drivers

* CaseHub's orchestration execution model (5 composable SPIs: routing, decomposition, activation, aggregation, termination) is architecturally distinct from LC4j's (imperative control flow)
* CaseHub deployments already run LC4j agents alongside CaseHub-orchestrated agents — both must coexist without interference
* Governance meta-annotations (`@OversightGate`, `@TrustRouted`, `@Attestation`) must compose uniformly onto all pattern annotations
* Engine-annotations (Layer 1) established the "own all annotations" precedent (D1) for the same architectural reasons
* The LC4j team is within the same organisation — the decision must be defensible as architectural necessity, not preference
* The broader LC4j community should be able to benefit from CaseHub's governance, trust, and audit capabilities without rewriting their agents

## Considered Options

* **Option A** — Own all 8 annotations, maximise LC4j runtime integration via composition
* **Option B** — Adopt LC4j's 5 base annotations, own 3 CaseHub-unique patterns
* **Option C** — Adapter layer bridging LC4j annotations to CaseHub's ExecutionModel

## Decision Outcome

Chosen option: **Option A** — a dual-track strategy:

**Track 1 — CaseHub owns its orchestration annotations.** All 8 pattern annotations live in `io.casehub.blocks.annotations`, produce CaseHub's `ExecutionModel<T>`, and compose uniformly with governance meta-annotations. No `langchain4j-agentic` dependency. CaseHub's execution model is the right abstraction for CaseHub use cases — composable SPIs, governance hooks, event-driven backends.

**Track 2 — Maximise LC4j integration via composition.** LC4j agents are first-class CaseHub workers at runtime. An existing `@SupervisorAgent` LC4j agent is a CDI bean that CaseHub wraps via `@Worker` + `AgentWorkerFunction`. CaseHub handles external orchestration (when to invoke, governance, trust routing, audit). LC4j handles internal orchestration (how the agent does its work). The LC4j agent automatically gets qhorus channel audit, ledger attestation chains, trust scoring, and oversight gates — these operate on the worker dispatch boundary, not the annotation layer. No annotation migration needed. Write in LC4j, deploy in CaseHub, get governance for free.

The two tracks reinforce each other. Track 1 ensures CaseHub's orchestration model is not compromised by annotation-level coupling. Track 2 ensures the LC4j community can integrate with and benefit from CaseHub's governance capabilities without sacrificing their existing code. Interoperability and hybridisation happen through composition, not through annotation adoption.

### Positive Consequences

* Every CaseHub pattern annotation maps to the same `ExecutionModel<T>` — governance composes uniformly
* No build extension coexistence conflict — LC4j's `AgenticProcessor` and CaseHub's blocks processor handle different annotations
* LC4j agents coexist alongside CaseHub-orchestrated agents in the same deployment without interference
* LC4j community can deploy existing agents into CaseHub and gain governance, trust, audit without rewriting
* Hybrid deployments are natural — some agents are LC4j-native (simple tool-calling), others are CaseHub-native (governed, trust-routed, deliberative)
* `@Customize` escape hatch targets CaseHub builder types consistently

### Negative Consequences / Tradeoffs

* 5 additional annotation definitions + build step handlers (trivial — thin mappings to `Patterns.*().build()`)
* Developers learn CaseHub annotation names for CaseHub orchestration, LC4j names for LC4j orchestration (but the semantics are different, so distinct names prevent confusion)
* CaseHub does not automatically benefit from new patterns LC4j adds (but CaseHub's patterns are architecturally different — LC4j additions are unlikely to map directly)

## Why Not Adopt LC4j Annotations

Three structural problems make annotation adoption unworkable:

**1. Build extension coexistence.** LC4j's `AgenticProcessor` auto-activates when `langchain4j-agentic` is on the classpath and processes `@SupervisorAgent` to generate LC4j agent beans. If CaseHub's build extension also processes `@SupervisorAgent` to generate `ExecutionModel<T>` beans, either duplicate beans are generated, one processor must suppress the other (breaking pure LC4j agents in the same deployment), or both run with CDI disambiguation. Track 2 requires coexistence — suppressing LC4j's processor destroys it.

**2. Attribute surface mismatch.** LC4j's `@SupervisorAgent` attributes express LC4j's model. CaseHub's supervisor is configured through five independently composable SPIs. CaseHub would either ignore most LC4j attributes (making the annotation a vestigial marker) or overload them with CaseHub semantics (confusing developers who expect LC4j behavior).

**3. Semantic lie.** A developer who reads LC4j documentation about `@SupervisorAgent` expects LC4j's supervisor behavior. In a CaseHub deployment, the same annotation would produce CaseHub behavior — different execution model, different runtime, different debugging path. Same name, different contract. Distinct names for distinct systems is honest and predictable.

## The Collaboration Boundary

LC4j and CaseHub interop at the LLM client layer, not the orchestration layer:

| Layer | Owner | Interop |
|-------|-------|---------|
| LLM client (`ChatModel`, `ChatRequest`) | LC4j | CaseHub uses directly |
| Tool-augmented agents (`AiService`, `@RegisterAiService`) | LC4j | CaseHub bridges via `ChatModelAgentProvider` / `AgentProviderChatModel` |
| Orchestration patterns | Each owns their own | LC4j: imperative control flow. CaseHub: composable 5-SPI model |
| Governance (oversight, trust, attestation) | CaseHub | LC4j agents get this via Track 2 composition |

The analogy: Spring defines `@Transactional` over Hibernate's `SessionFactory`. Spring does not reuse Hibernate's annotations for session management — shared engine, distinct orchestration semantics. CaseHub defines `@Supervisor` over LC4j's `ChatModel`. Same principle.

## Pros and Cons of the Options

### Option A — Own all 8, maximise LC4j runtime integration

* ✅ Uniform execution model — all annotations produce CaseHub's `ExecutionModel<T>`
* ✅ Governance composes uniformly onto all patterns
* ✅ No build extension conflict — separate annotations, separate processors
* ✅ LC4j agents participate as CaseHub workers via composition — no migration
* ✅ Hybrid deployments: LC4j-native + CaseHub-native agents in the same case
* ❌ 5 overlapping pattern names (different packages, different semantics)

### Option B — Adopt LC4j's 5 base annotations

* ✅ Ecosystem alignment — developers use familiar LC4j annotation names
* ✅ Less annotation code to maintain
* ❌ Build extension coexistence conflict (two processors for `@SupervisorAgent`)
* ❌ Attribute surface mismatch — LC4j attributes cannot express CaseHub's 5-SPI composition
* ❌ Semantic divergence — same `@SupervisorAgent`, different behavior in CaseHub vs vanilla LC4j
* ❌ Requires `langchain4j-agentic` compile dependency on blocks-annotations

### Option C — Adapter layer bridging LC4j to CaseHub

* ✅ Maximum surface compatibility with LC4j annotations
* ❌ Translation layer between execution models is fragile and lossy
* ❌ Most complex option — two execution models, bridging logic, disambiguation
* ❌ Neither preserves LC4j semantics nor cleanly adopts CaseHub semantics

## Links

* Engine-annotations D1 decision — `docs/specs/issue-909-engine-annotations/decisions.md`
* Blocks D1 decision — `docs/specs/issue-116-blocks-annotations/decisions.md`
* Epic: casehubio/blocks#115 (annotation-driven agent programming model)
* Issue: casehubio/blocks#116 (blocks-annotations module)
* `io.casehub.blocks.agentic.pattern.Patterns` — 8 pattern builders
* `io.casehub.blocks.agentic.model.ExecutionModel` — 5-SPI composition record
* GE-20260608-4c8108 — `AnnotationsImpliesAiServiceBuildItem` (LC4j runtime integration pattern)
