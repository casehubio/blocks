# CaseHub TypeScript Programming Model — Roadmap Feasibility

## Summary

CaseHub can be made accessible to the TypeScript world through three progressive levels of support, without a full port in either direction. The strategy serves three audiences with three representations (YAML / Java DSL / TS DSL) that all produce the same runtime objects.

**Consuming CaseHub** from TS is genuinely native — type-safe GraphQL overlays, full npm/Node.js ecosystem. **Authoring CaseHub** in TS uses familiar syntax and type safety as a barrier reduction — the runtime remains JVM.

The immediate high-value work is extending YAML to cover all expressible patterns. This benefits everyone: ops teams get declarative config, tooling gets visualisable documents (orchestration diagrams, serverless workflow views), LLMs generate validated YAML, and the TS CDK gains every pattern automatically.

## Three-Level Strategy

| Level | What | Audience | Runtime | Status |
|-------|------|----------|---------|--------|
| **L1 — GraphQL Client** | Type-safe TS overlays for CaseHub GraphQL API | TS app developers consuming CaseHub | Node.js (native) | To build |
| **L2 — TS CDK → YAML** | Builder functions (Pages DSL pattern) emitting validated YAML | TS developers authoring, LLMs generating configs | Node.js at build time, JVM at runtime | To build (depends on YAML expansion) |
| **L3 — TSJ + YAML** | TS → JVM bytecode for direct Java interop | Advanced TS developers, SPI implementors | JVM | Placeholder (like desiredstate #108) |

## Type Generation & Drift Prevention

**The Java model types are canonical.** The records, sealed interfaces, and builders (e.g., `CaseDefinition`, `ExecutionModel`, `AgentRef`) are the single source of truth. Both YAML and Java DSL/annotations are authoring surfaces that produce instances of these same model types. TS becomes a third authoring surface.

```
Java model types (records, sealed interfaces, builders) ← THE SOURCE

    ↑ authored via YAML          ↑ authored via Java DSL        ↑ authored via TS CDK
    CaseDefinitionYamlMapper     @Case, @Worker, Patterns.*     builder functions → YAML
```

**Generation flows FROM the model types** via JavaParser:
- Java model → JSON Schema (what YAML must accept)
- Java model → TS types + builder APIs (what TS CDK uses)
- Java DSL/annotations already use these types directly (no generation needed)

JavaParser gives richer type information than JSON Schema can express:
- Sealed interfaces → TS discriminated unions with exhaustiveness
- Generics (`DecompositionStrategy<T>`) → TS generic types
- Builder method signatures → TS builder methods with full type info
- `@FunctionalInterface` → precise TS function types
- Javadoc → TSDoc and JSON Schema descriptions

**Current state:** Schema came first historically (jsonschema2pojo generates Java POJOs from CaseDefinition.yaml). The proposal reverses the flow — model types become the source, JSON Schema becomes a generated artifact. The hand-written 1882-line CaseDefinitionYamlMapper and the two-CaseDefinition-class problem (generated POJOs vs hand-written API model) collapse over time.

**Drift prevention:** CI regenerates schema and TS types from Java model source. Diff against committed artifacts. Non-empty diff = build failure. No manual sync needed.

## Cross-Foundation Principle

Every YAML DSL in the CaseHub foundations gets a dual TS builder. The pattern established by Pages (pages-ui/src/dsl/builders.ts) applies universally:

| Module | YAML surface | TS builder status |
|--------|-------------|-------------------|
| **pages** | Component tree definitions | Done (Pages DSL) |
| **engine** | CaseDefinition.yaml | To build |
| **desiredstate** | Graph declarations | Placeholder (issue #108) |
| **flow** | Workflow definitions | To assess |
| **work** | Work item templates | To assess |

Shared infrastructure: one JSON Schema → TS generator tool, per-module builder API, common npm publishing (`@casehub/*-sdk`).

## Pattern-to-YAML Mapping

### Fully YAML-expressible (strategy name + config parameters)

These patterns are naturally declarative. Strategy implementations live in Java; YAML declares which strategy and its configuration. The TS CDK covers these automatically.

| Package | Pattern | YAML representation |
|---------|---------|-------------------|
| `agentic.pattern` | Supervisor | `pattern: { type: supervisor, maxIterations: 10 }` |
| `agentic.pattern` | Sequence | `pattern: { type: sequence, agents: [...] }` |
| `agentic.pattern` | Loop | `pattern: { type: loop, maxIterations: 5, exitCondition: "..." }` |
| `agentic.pattern` | Parallel | `pattern: { type: parallel, agents: [...] }` |
| `agentic.pattern` | Voting | `pattern: { type: voting, evaluators: [...] }` |
| `agentic.pattern` | Debate | `pattern: { type: debate, maxRounds: 5, debaters: [...], judge: "..." }` |
| `agentic.pattern` | Conditional | `pattern: { type: conditional, when: [...] }` |
| `agentic.pattern` | HTN | `pattern: { type: htn, rootTask: {...} }` |
| `agentic.routing` | FirstMatch | `routing: { strategy: first-match, filter: "..." }` |
| `agentic.routing` | RoundRobin | `routing: { strategy: round-robin }` |
| `agentic.routing` | Sequential | `routing: { strategy: sequential }` |
| `agentic.routing` | LlmSelected | `routing: { strategy: llm-selected }` |
| `agentic.termination` | MaxIterations | `termination: [{ type: max-iterations, iterations: 20 }]` |
| `agentic.termination` | GoalReached | `termination: [{ type: goal-reached, condition: "..." }]` |
| `agentic.termination` | Convergence | `termination: [{ type: convergence, threshold: 0.8 }]` |
| `agentic.aggregation` | PassThrough | `aggregation: { strategy: pass-through }` |
| `agentic.aggregation` | CollectAll | `aggregation: { strategy: collect-all }` |
| `agentic.aggregation` | MajorityVote | `aggregation: { strategy: majority-vote }` |
| `agentic.activation` | OnExplicitDispatch | `activation: { rule: on-dispatch }` |
| `agentic.activation` | MaxIterationsGuard | `activation: { rule: max-iterations, limit: 5 }` |
| `negotiation` | UnanimousAcceptance | `acceptance: { policy: unanimous }` |
| `negotiation` | MajorityAcceptance | `acceptance: { policy: majority }` |
| `negotiation` | ThresholdAcceptance | `acceptance: { policy: threshold, required: 3 }` |
| `negotiation` | MaxRoundsTermination | `termination: [{ type: max-rounds, rounds: 10 }]` |
| `negotiation` | DeadlineTermination | `termination: [{ type: deadline, duration: PT24H }]` |
| `conversation.orchestration` | RoundRobinTurnPolicy | `turnPolicy: { type: round-robin }` |
| `conversation.orchestration` | AddressedTurnPolicy | `turnPolicy: { type: addressed }` |
| `conversation.orchestration` | FreeTurnPolicy | `turnPolicy: { type: free }` |
| `conversation` | EpistemicRules (built-in) | `epistemicRule: { type: explicit-acknowledgement, minParticipants: 2 }` |
| `conversation` | ConvergencePolicies (built-in) | `convergencePolicy: { type: structural, similarityThreshold: 0.8 }` |
| `oversight` | RiskDecision | `riskDecision: { type: gate-required, reason: "...", reversible: true }` |
| `normative` | PriorityResolution | `conflictResolution: { strategy: priority }` |
| `normative` | SpecificityResolution | `conflictResolution: { strategy: specificity }` |
| `normative` | MostRestrictiveResolution | `conflictResolution: { strategy: most-restrictive }` |
| `agentic.decomposition` | Identity | `decomposition: { strategy: identity }` |
| `agentic.decomposition` | Static | `decomposition: { strategy: static, methods: [...] }` |
| `agentic.decomposition` | LLM | `decomposition: { strategy: llm, maxDepth: 2 }` |
| `agentic.decomposition` | Hybrid | `decomposition: { strategy: hybrid, maxDepth: 2 }` |
| `agentic.decomposition` | GOAP | `decomposition: { strategy: goap }` |
| `prompt` | FewShotOptimiser | `promptOptimiser: { type: few-shot, maxExamples: 5 }` |
| `prompt` | InstructionOptimiser | `promptOptimiser: { type: instruction }` |

### Partially YAML-expressible (config + expression language for predicates)

These patterns have a declarative structure but include predicate/expression components. Using JQ or MVEL expressions in YAML covers most use cases without custom code.

| Package | Pattern | YAML approach | Limitation |
|---------|---------|---------------|------------|
| `agentic.decomposition` | Static method guards | `guard: ".priority > 5"` (JQ expr) | Complex multi-field guards may be unwieldy |
| `agentic.termination` | GoalReached predicate | `condition: ".reviewComplete == true"` (JQ) | Stateful predicates need code |
| `agentic.pattern` | Conditional dispatch | `when: [{ condition: "...", agent: "..." }]` | Complex routing logic needs code |
| `agentic.aggregation` | Auction (BidExtractor) | `auction: { type: english, bidField: ".offer.amount" }` | Custom bid extraction logic needs code |
| `conversation` | Custom EpistemicRule | Composable built-ins via `and:`/`or:` | Truly novel classification needs code |
| `conversation` | Custom ConvergencePolicy | Composable built-ins via `composite:` | Truly novel convergence detection needs code |

### Not YAML-expressible (require code — TSJ or Java)

These patterns are inherently procedural, stateful, or require custom logic that cannot be reduced to strategy name + config + expressions.

| Package | Pattern | Why code is needed |
|---------|---------|-------------------|
| `agentic.social` | PersonalityEvolutionOrchestrator | Stateful orchestrator with JPAF pipeline composition |
| `agentic.social` | InnerLifeOrchestrator | Background thought loop with proactive initiation |
| `agentic.social` | UserModelOrchestrator | Tiered heuristic+LLM profile synthesis |
| `agentic.social` | MentalModelOrchestrator | BDI Theory of Mind with confidence decay, GOAP projection |
| `agentic.social` | StrategyLearningOrchestrator | Multi-level reflection with three-tier engagement analysis |
| `agentic.social` | MoodOrchestrator | PAD emotional state with bounded decay |
| `agentic.social.drive` | DriveOrchestrator | Intrinsic motivation with personality/mood modulation |
| `agentic.social.goal` | GoalProposalOrchestrator | Autonomous goal proposal from drive signals |
| `agentic.belief` | BeliefSet operations | Custom ConsistencyChecker SPI |
| `agentic.intention` | JointIntention lifecycle | Custom IntentionMonitor SPI |
| `agentic.coalition` | CoalitionEvaluator | Custom coalition scoring logic |
| `agentic.model` | ChoreographedDriver events | Custom EventSource, EventConcurrencyPolicy |
| `agentic.decomposition` | Custom DecompositionHeuristic | Batch-native async method scoring |
| `agentic.decomposition` | CompositeHeuristic weights | Runtime-computed weights |
| `memory` | Custom ImportanceScorer | Domain-specific importance logic |
| `memory` | Custom IntegrityChecker | Structural + semantic escalation logic |
| `routing.agent` | Custom RoutingSignalProvider | Domain-specific routing signals |
| `routing.agent` | Custom CbrOutcomeWeights | Domain-specific outcome weighting |
| `prompt` | Custom PromptQualityMetric | Domain-specific quality scoring |
| `prompt` | Custom DiversityStrategy | Domain-specific example re-ranking |
| `summarisation` | Custom Summariser implementations | Domain-specific summarisation logic |
| `summarisation` | Custom Compactor | Domain-specific event compaction |

## Immediate Next Steps

1. **YAML expansion** — Extend CaseDefinition.yaml schema to cover the "fully expressible" patterns above. This is the highest-value work: it benefits YAML authors, TS CDK, LLM generation, and tooling/visualisation simultaneously.

2. **GraphQL audit** — Assess current GraphQL schema coverage. Identify gaps for L1 (type-safe TS client).

3. **JavaParser-based generator** — Build or configure a JavaParser tool that walks Java source and emits both TS types/builders and JSON Schema. Retire the jsonschema2pojo flow. Evaluate victools/jsonschema-generator for the Java→schema direction.

4. **Pages DSL as template** — Use pages-ui/src/dsl/builders.ts as the reference implementation for the TS CDK builder pattern. Design the engine CDK builder API following the same conventions.

5. **TSJ evaluation** — Track TSJ maturity. Placeholder for L3, same as desiredstate #108. Evaluate when TSJ reaches sufficient maturity for the constrained DSL use case.

6. **Pattern mapping document** — Maintain the mapping table above as a living reference. Update as YAML expressiveness expands and patterns evolve.

## Platform-Wide YAML Coverage Audit

Cross-repo audit of Java annotation/DSL/builder patterns vs YAML equivalents.

### Strong parity (already dual YAML + Java)

Engine core loop: `@Case` → YAML root, `@Worker` → `workers:`, `@Capability` → `capabilities:`, `@Bind` → `bindings:`, `@Goal` → `goals:`, `@Milestone` → `milestones:`, `@Completion` → `completion:`, `@SystemPrompt` → `agent.systemPrompt`.

Work: `@HumanApproval` → `humanTask:` in bindings.

Pages: Component tree definitions → TS DSL builders.

### Gaps — high value for YAML expansion

| Priority | Repo | Gap | YAML feasibility |
|----------|------|-----|-----------------|
| **P0** | eidos | `@Identity`, `@Disposition`, `@AgentGoals`, `@AgentConstraints` — entirely annotation-only | Purely declarative (slot, personality traits, goals, constraints). Maps naturally to YAML. **Biggest single gap.** |
| **P1** | engine | `@Cost`, `@Effect`, `@SoftDependency` — no YAML fields | Straightforward: add `cost:`, `effect:`, `softDependency:` to worker/capability schema |
| **P1** | engine | `@Customize` — no YAML equivalent | Named customizer IDs as a YAML list |
| **P1** | work | `@Escalate`, `@SkillMatch` — no YAML fields | Declarative config, natural fit for `humanTask:` schema extension |
| **P2** | blocks | All agentic orchestration patterns — code-only | Covered by pattern mapping table above |
| **P2** | desiredstate | Entirely annotation-only | Tracked via #108 |

### ~15 Java-only fields in engine (no YAML equivalent)

These exist in `io.casehub.api.model.CaseDefinition` but not in the YAML schema: `goapActions`, `planningConstraints`, `monitoringConfig`, `portfolioConfig`, `defaultWorkerBridge`, `workerServiceAccountIds`, `defaultQuorum`, `reflectionTrigger`, `adaptationConfig`, and others. Adding these to the schema is the YAML expansion work list.

### Inherently code-only (no YAML path makes sense)

- Ledger interception: `@Attested`, `@Audited`, `@ProvenanceCapture` — AOP interceptors wrapping method calls
- Platform CDI qualifiers: `@CloudEventType`, `@McpDomain`, `@Inference` — injection points
- Ledger parameter bindings: `@SubjectId`, `@ActorId`, `@TenancyId` — runtime parameter extraction
- Worker lambdas: `Worker.builder().function(this::method)` — arbitrary code (YAML covers HTTP via `do:` and LLM via `agent:`)

### Current Schema Generation Flow

```
CaseDefinition.yaml (JSON Schema) ← CURRENT source of truth
    │
    ├──▶ jsonschema2pojo (CasehubCodegen) → io.casehub.model.* (~40 generated POJOs)
    │
    └──▶ CaseDefinitionYamlMapper (hand-written, 1882 lines)
            YAML → generated POJO → io.casehub.api.model.CaseDefinition (hand-written, 1019 lines)
```

**Proposed flow (model-canonical):**

```
Java model types (io.casehub.api.model.*) ← SOURCE OF TRUTH
    │
    ├──▶ JavaParser → JSON Schema (CaseDefinition.yaml, now generated)
    ├──▶ JavaParser → TS types + builder APIs (@casehub/engine-sdk)
    ├──  Java DSL + annotations → already use these types directly
    └──▶ Jackson direct deserialization (replaces 1882-line mapper over time)
```

Three authoring surfaces (YAML, Java DSL, TS CDK), one model, zero drift.

## Decisions

Seven decisions captured in `specs/main/decisions.md`:

| # | Decision | Summary |
|---|----------|---------|
| D1 | Three-Level TS Strategy | L1 (GraphQL, native), L2 (CDK→YAML), L3 (TSJ+YAML, placeholder) |
| D2 | YAML-first expressiveness | Push YAML as far as possible before relying on TSJ |
| D3 | Full type safety | No `any`, no escape hatches, no exceptions |
| D4 | Java-canonical type generation | JavaParser generates TS types, builders, and JSON Schema from Java source |
| D5 | Cross-foundation principle | Every YAML surface gets a TS builder |
| D6 | Extend CaseDefinition schema | Agentic patterns in existing spec section |
| D7 | Three audiences | YAML (ops), Java DSL (Java devs), TS DSL (TS devs) — same runtime |

## References

- casehubio/casehub-desiredstate#108 — TSJ graph declarations design
- Pages DSL: pages-ui/src/dsl/builders.ts — proven dual YAML/TS pattern
- CaseDefinition.yaml: engine/schema/src/main/resources/schema/CaseDefinition.yaml — existing JSON Schema (1345 lines)
- tsj: https://github.com/vgargatgit/tsj — TS→JVM compiler (early stage)
- engine YAML examples: engine/schema/src/main/resources/examples/
- CasehubCodegen: engine/schema — jsonschema2pojo custom entry point (current flow)
- CaseDefinitionYamlMapper: engine/api — hand-written mapper (1882 lines, drift risk)
- victools/jsonschema-generator — candidate for Java→schema generation
