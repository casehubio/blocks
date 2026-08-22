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
