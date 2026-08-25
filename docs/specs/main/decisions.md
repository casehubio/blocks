# TypeScript Programming Model — Decisions

## D1: Three-Level TS Strategy

**Choice:** Three progressive levels of TypeScript support for CaseHub, reducing the barrier to entry for TS developers

**Important framing:** Two distinct TS stories: *consuming* CaseHub is genuinely native TS (GraphQL overlays, full npm ecosystem, Node.js runtime). *Authoring* CaseHub (defining cases, workers, patterns) uses TS syntax and type safety as a barrier reduction — familiar tooling, but runtime is JVM.

- **Level 1 — GraphQL Client**: Native TS integration for consuming CaseHub. Type-safe GraphQL overlays, npm package, full Node.js ecosystem. A TS developer's Next.js/React/Express app talks to CaseHub with full type safety. This is genuinely first-class TS.
- **Level 2 — TS CDK → YAML**: Development-time builder functions (Pages DSL pattern) that produce validated CaseHub YAML. Types generated from CaseDefinition.yaml JSON Schema. Runs on Node.js at build time, produces YAML that JVM executes. Full type safety, autocomplete, refactoring.
- **Level 3 — Dual TSJ + YAML**: TSJ (TS→JVM bytecode) for direct Java object construction AND continued YAML expressiveness expansion. TS syntax, JVM runtime — no npm ecosystem access in workers. TS type definitions (.d.ts) generated from Java types. The relative weight of TSJ vs YAML evolves based on TSJ maturity and YAML coverage.

**Alternatives:**
- Node.js-native workers (TS code runs on Node.js, communicates with JVM via gRPC) — true TS ecosystem access but adds distributed system complexity, latency, and a separate runtime to operate
- TSJ-only (no YAML expansion) — all-in on a pre-alpha dependency; no fallback
- Full port to TS — prohibitive cost, maintaining two implementations

**Rationale:** Each level delivers standalone value. Level 1 is immediate. Level 2 leverages the existing YAML schema with the Pages DSL precedent. Level 3 provides the escape hatch for patterns that can't be declaratively expressed. YAML expansion as a parallel track reduces TSJ dependency. The goal is "you don't need to learn Java to build on CaseHub" — not "CaseHub is a TS platform."

**Trade-offs:** Level 3 depends on TSJ maturity (32 commits, no license, 1 contributor). The YAML expansion path is the safety net — if TSJ stalls, the declarative surface grows to compensate. TS developers do NOT get Node.js ecosystem access for workers/agents (no npm packages, no Node.js APIs in business logic).

**Sources:** casehubio/casehub-desiredstate#108, Pages DSL (pages-ui/src/dsl/builders.ts), CaseDefinition.yaml JSON Schema (engine/schema)

**Exploration:** quick
**Status:** captured

## D2: YAML as primary expressiveness surface

**Choice:** Push YAML expressiveness as far as possible before relying on TSJ for code-level patterns

**Alternatives:**
- TSJ-first (make everything code-constructable, YAML as secondary) — couples roadmap to TSJ maturity
- Independent TS SDK producing JSON IR — loses "no serialization boundary" elegance, still needs schema design

**Rationale:** YAML already covers case definitions comprehensively (1345-line JSON Schema). The engine already loads and executes YAML. Expanding YAML to cover agentic patterns, routing strategies, and termination conditions is incremental. The TS CDK (Level 2) automatically gains these patterns. TSJ is reserved for truly code-only patterns (custom lambdas, SPI implementations).

**Trade-offs:** Some blocks patterns are inherently procedural (custom decomposition logic, social cognition orchestrators) and may never be fully expressible in YAML. These remain TSJ-only or Java-only.

**Sources:** CaseDefinition.yaml schema analysis, Pages DSL dual YAML/TS strategy

**Exploration:** quick
**Status:** captured

## D3: Full type safety — no exceptions

**Choice:** All TS surfaces are fully type-safe. No `any`, no type escape hatches, no untyped config objects.

**Alternatives:**
- Pragmatic typing (allow `any` at extension points) — faster to build, but undermines the type safety story
- Runtime validation only (loose types + zod/joi) — shifts errors from compile time to runtime

**Rationale:** Type safety is the core value proposition of TS over raw YAML. If the TS surface has `any` holes, LLMs will generate untyped code that passes the type checker but fails at runtime. The type system IS the validation layer.

**Trade-offs:** Harder to design extension points. Generic type parameters may be needed for SPIs. Generated types from Java must be complete — no partial generation.

**Sources:** User requirement (explicit)

**Exploration:** quick
**Status:** captured

## D4: Java-canonical type generation via JavaParser

**Choice:** Java source is the single source of truth. JavaParser walks the AST and generates both TS types and YAML schema.

JavaParser extracts richer type information than JSON Schema can express:
- Sealed interfaces → TS discriminated unions with exhaustiveness
- Generics (`DecompositionStrategy<T>`) → TS generic types
- Builder method signatures → TS builder methods with full parameter/return types
- `@FunctionalInterface` → precise TS function types
- Javadoc → TSDoc
- Sealed hierarchies → TS `never` exhaustiveness

Generation outputs:
- **TS types + builder APIs** for both L2 (YAML-surface subset) and L3 (full Java interop)
- **JSON Schema** generated from Java types (replaces hand-maintained CaseDefinition.yaml as the derivative artifact, not the source)

The architecture is a single AST walker with pluggable writers — the visitor pattern. One walk of the Java model types dispatches to multiple writers at each node:

```
Java AST ──walker──┬──▶ SchemaWriter → JSON Schema
                   ├──▶ TypeScriptWriter → TS interfaces + discriminated unions + builders
                   └──▶ (future writers)
```

**Why JavaParser over existing tools (victools + json-schema-to-typescript):**
- Existing tools chain: Java → JSON Schema → TS interfaces. Two tools, lossy intermediate format. JSON Schema cannot express generics, sealed exhaustiveness, or builder method chains.
- Existing tools generate TS interfaces only — not the TS builder functions (Pages DSL pattern) that are the whole point of the CDK.
- JavaParser visitor generates both schema AND TS builders in one walk. Builder generation is the unique value — without it, every TS builder is hand-written and drifts.
- Scales across all modules (cross-foundation D5): engine, eidos, blocks, work, desiredstate. Hand-writing builders for all of those is unsustainable.

**Alternatives:**
- Chained existing tools (victools → JSON Schema → json-schema-to-typescript) — simpler but can't generate TS builders, loses type fidelity
- Hybrid (victools for schema, JavaParser for TS only) — two tools to maintain, divergence risk between them
- JSON Schema → TS only — loses generics, sealed interfaces, builder methods

**Rationale:** A single Java → everything generator eliminates drift by construction. JSON Schema becomes a generated artifact validated by CI, not a hand-maintained source. The YAML schema retains its documentation richness via Javadoc → description mapping. TS types gain fidelity that JSON Schema cannot express. TS builders are generated, not hand-written.

**Trade-offs:** More upfront investment than plugging in existing tools. The current schema's rich validation constraints (regex patterns, min/max, oneOf) need Java-side annotation equivalents (e.g., Jakarta Validation annotations). The existing jsonschema2pojo flow would be retired.

**Sources:** JavaParser, current CaseDefinition.yaml analysis, Pages DSL builder pattern

**Exploration:** quick
**Status:** captured

## D5: Cross-foundation principle — every YAML surface gets a TS builder

**Choice:** The dual YAML/TS strategy applies to every YAML DSL in the CaseHub foundations, not just blocks/engine

Scope: engine (case definitions), pages (component trees, already done), desiredstate (graph declarations), flow (workflows), work (templates), and any future YAML-configured module.

Shared infrastructure: one JSON Schema → TS generator tool, one CI validation pipeline, common npm publishing. Each module designs its own builder API following the Pages DSL pattern.

**Alternatives:**
- Blocks/engine only — misses the platform-wide TS story
- Selective (only modules with external users) — creates inconsistency in the developer experience

**Rationale:** The Pages DSL already proves the pattern works. The generator tool is mechanical. The marginal cost of adding a new module is low once the infrastructure exists. A consistent cross-foundation TS story is stronger than piecemeal support.

**Trade-offs:** Broader scope means more coordination across repos. Each module team needs to maintain their builder API design. But the shared generator handles the mechanical parts.

**Sources:** Pages DSL (proven), user insight ("every place there is YAML in the foundations")

**Exploration:** quick
**Status:** captured

## D6: Extend CaseDefinition schema for agentic patterns

**Choice:** Agentic orchestration patterns become YAML-expressible by extending the existing CaseDefinition spec section (same approach as `agent:` and `do:` blocks on workers)

Workers already have function-type blocks (`agent:`, `do:`, `sequence:`). The pattern is: strategy name + config parameters in YAML, strategy implementation in Java/TSJ. The TS CDK gains agentic patterns automatically when the schema expands.

**Alternatives:**
- Separate AgenticPattern.yaml schema — more modular but adds a new document type
- Named registry only (string IDs) — least YAML expansion, most reliance on code

**Rationale:** Keeping everything in one CaseDefinition document is simpler for authoring, validation, and deployment. The existing schema is already extensible (`unevaluatedProperties: true` on CaseDefinitionSpec). New blocks go into the spec section alongside capabilities, bindings, and workers.

**Trade-offs:** The CaseDefinition schema grows larger. Complex orchestration topologies may strain YAML readability. But the TS CDK (Level 2) compensates — type-checked builders are more ergonomic than raw YAML for complex configs.

**Sources:** CaseDefinition.yaml schema (spec.unevaluatedProperties: true), existing worker function patterns

**Exploration:** quick
**Status:** captured

## D7: Three audiences, three representations — YAML / Java / TS

**Choice:** YAML, Java DSL, and TS DSL serve different audiences, not the same audience with options

- **YAML** → ops teams, deployment tooling, GitOps pipelines, CI/CD
- **Java DSL + annotations** → Java power developers (existing, unchanged)
- **TS DSL** → TS developers, LLM-generated configurations

All three produce the same runtime objects. The deployment blueprints work (ops/desiredstate) connects them: YAML is how you deploy and configure, Java/TS is how you develop.

**Alternatives:**
- Single canonical representation with adapters — loses audience-appropriate DX
- Pick two of three — creates a second-class audience

**Rationale:** Different audiences have different expectations and tooling. Ops people live in YAML and kubectl/ArgoCD. Java developers want IDE refactoring and compile-time checking. TS developers want npm and VS Code. Forcing any audience into another's tooling creates friction that undermines adoption.

**Trade-offs:** Three representations to keep in sync. But the generation pipeline (D4) makes this mechanical, not manual. The source of truth is the Java types and JSON Schema — both DSLs are generated or validated against those.

**Sources:** Pages dual YAML/TS precedent, ops/desiredstate deployment blueprints discussion

**Exploration:** quick
**Status:** captured
