# casehub-blocks-annotations Design

**Date:** 2026-08-22
**Status:** Draft
**Scope:** `casehub-blocks-annotations` module — annotation-driven orchestration patterns and governance meta-annotations
**Issue:** casehubio/blocks#116
**Epic:** casehubio/blocks#115 (annotation-driven agent programming model)

## Motivation

CaseHub's orchestration patterns (`Patterns.supervisor()`, `Patterns.debate()`, etc.) and governance capabilities (oversight gates, trust routing, CBR evidence routing, attestation) are powerful but require fluent builder chains. The annotation module provides progressive disclosure: a developer writes `@Debate(maxRounds = 5)` with `@OversightGate(MyClassifier.class)` and gets a governed debate pattern — without learning the builder API.

This is a Layer 2 module in the annotation-driven model. Engine-annotations (Layer 1) provides `@Case`, `@Worker`, `@Bind`, `@Goal`, `@Milestone`, `@Completion`. Blocks-annotations adds orchestration patterns and governance meta-annotations that compose onto Layer 1.

---

## Architectural Decisions

All decisions are recorded in `decisions.md` alongside this spec. ADR-0004 (`docs/adr/0004-own-orchestration-annotations.md`) covers the LC4j relationship.

| # | Decision | Summary |
|---|----------|---------|
| D1 | Own all annotations | No `langchain4j-agentic` dependency. Dual-track: CaseHub owns orchestration annotations, LC4j agents integrate as workers at runtime (blocks#150). |
| D2 | Nested module layout | `annotations/runtime/` + `annotations/deployment/`. Matches engine, work, ledger. |
| D3 | Dual-use ExecutionModel | Pattern annotations produce `ExecutionModel<T>` CDI beans — usable standalone via `execute()` and as case workers via `@Worker` capability reference. |
| D4 | Governance on @Worker + patterns | `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation` compose onto both engine `@Worker` methods and blocks pattern methods. |
| D5 | Bare annotation names | `@Supervisor`, `@Debate`, `@Htn` — no suffix. Pre-GA; collision risk for `@Parallel`/`@Conditional` acknowledged. |
| D6 | Decomposition as pattern attribute | `@Supervisor(decomposition = X.class)` — intrinsic configuration, not cross-cutting. |
| D7 | Convergence as pattern attribute | `@Debate(maxRounds = 5)` — maps to builder. Complex convergence via `@Customize`. |
| D8 | 3 example domains | Incident response, aircraft maintenance, wildfire response — extending engine#945 bases. |

**Scope evolution from issue #116:** The original issue listed `@Convergence` as a separate annotation and `@Decompose` as a governance meta-annotation. During design, `@Convergence` was subsumed into pattern attributes (D7 — convergence is intrinsic to the pattern, not cross-cutting) and `@Decompose` was subsumed into pattern attributes (D6 — decomposition is SPI configuration, not governance). The original issue also listed `@DebateAgent`, `@VotingAgent`, `@HtnAgent` — these became `@Debate`, `@Voting`, `@Htn` per D5 (bare names).

**Epic #115 principle 1 departure:** The epic states "Adopt LangChain4j annotations as Layer 0." D1 and ADR-0004 depart from this — CaseHub owns all annotations with no `langchain4j-agentic` dependency. The ADR documents the architectural rationale (build extension coexistence, attribute surface mismatch, semantic divergence). The epic text should be updated to reflect the evolved position (see blocks#152).

---

## Module Structure

```
blocks/
  annotations/                    # parent aggregator
    pom.xml
    runtime/                      # annotation definitions + runtime types
      pom.xml
      src/main/java/
        io/casehub/blocks/annotations/
          # Pattern annotations
          Supervisor.java
          Sequence.java
          Parallel.java
          Loop.java
          Conditional.java
          Debate.java
          Voting.java
          Htn.java
          # Role annotations (parameter-level)
          Agent.java
          Debater.java
          Voter.java
          Judge.java
          # Governance meta-annotations
          OversightGate.java
          TrustRouted.java
          CbrRouted.java
          Attestation.java
        io/casehub/blocks/annotations/runtime/
          # Descriptors (build-time → runtime bridge)
          PatternDescriptor.java
          GovernanceDescriptor.java
          BlocksAnnotationsRecorder.java
    deployment/                   # Quarkus build extension
      pom.xml
      src/main/java/
        io/casehub/blocks/annotations/deployment/
          BlocksAnnotationsProcessor.java
          PatternAnnotationStep.java
          GovernanceAnnotationStep.java
```

**Dependencies:**
- `runtime/` depends on: `casehub-blocks` (for `ExecutionModel`, pattern types), `casehub-engine-api` (for `DecompositionStrategy`, `ActionRiskClassifier`), `casehub-engine-annotations-runtime` (for `@Customize` in standalone pattern usage — D3 dual-use)
- `deployment/` depends on: `runtime/`, `quarkus-arc-deployment`, `casehub-engine-annotations-deployment` (for build step ordering via `@Consume`), `casehub-eidos-annotations-deployment` (optional — for `agentId` resolution via eidos build items)

---

## Pattern Annotations

Eight annotations, one per `Patterns.*()` builder. All are `@Retention(RUNTIME) @Target(METHOD)` on separate pattern interfaces.

**Type parameter `T`:** In the builder API, `ExecutionModel<T>` is generic — `T` is the execution state type flowing through all 5 SPIs. Java annotations cannot express generic type parameters, so annotation-defined patterns produce `ExecutionModel<Object>`. Default SPI implementations are all generic (`FirstMatchRouting<T>`, `IdentityDecomposition<T>`, etc.) and work with `Object`. Custom CDI-managed SPIs must accept `Object` state. This is the annotation model's trade-off: type safety is sacrificed for convenience. `@Customize` regains full type safety when needed — the builder can be explicitly parameterised within the customizer method.

### @Supervisor

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Supervisor {
    String name() default "";
    int maxIterations() default 10;
    Class<? extends RoutingStrategy> routing() default FirstMatchRouting.class;
    Class<? extends DecompositionStrategy> decomposition() default IdentityDecomposition.class;
    Class<? extends AggregationStrategy> aggregation() default PassThrough.class;
}
```

Maps to `Patterns.supervisor()`. The `routing`, `decomposition`, `aggregation` attributes set the corresponding SPIs. Defaults match the builder defaults (`SupervisorBuilder()` uses `FirstMatchRouting(c -> true)`, `IdentityDecomposition`, `PassThrough`). LLM-driven routing requires an `AgentProvider` and is configured via `@Customize`. Attributes not on the annotation (activation, termination, failure policy, backend) use `@Customize` for the long tail.

### @Debate

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Debate {
    String name() default "";
    int maxRounds() default 5;
}
```

Maps to `Patterns.debate().maxRounds(n)`. Participants are defined via `@Debater` and `@Judge` parameter annotations. Judge presence triggers `JudgeConvergence`; absence uses `MaxIterationsTermination`.

### @Voting

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Voting {
    String name() default "";
    Class<? extends AggregationStrategy> strategy() default MajorityVote.class;
}
```

Maps to `Patterns.voting().strategy(s)`. Evaluators defined via `@Voter` parameter annotations.

### @Htn

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Htn {
    String name() default "";
    Class<? extends DecompositionStrategy> decomposition() default StaticDecomposition.class;
}
```

Maps to `Patterns.htn()`. Root task provided via method parameter or `@Customize`. Decomposition depth is controlled by the `DecompositionStrategy` and `PlanningConstraints`, not the pattern annotation — depth is a planning concern intrinsic to the task tree structure, not a top-level execution parameter (see blocks#151 for adding `maxDepth` to `PlanningConstraints`).

### @Sequence, @Parallel, @Loop, @Conditional

Follow the same pattern — bare annotation with attributes mapping to the corresponding builder's most-used methods. Full attribute surface documented in Javadoc; complex configuration via `@Customize`.

```java
@Retention(RUNTIME) @Target(METHOD)
public @interface Sequence {
    String name() default "";
}

@Retention(RUNTIME) @Target(METHOD)
public @interface Parallel {
    String name() default "";
}

@Retention(RUNTIME) @Target(METHOD)
public @interface Loop {
    String name() default "";
    int maxIterations() default 10;
}

@Retention(RUNTIME) @Target(METHOD)
public @interface Conditional {
    String name() default "";
}
```

---

## Role Annotations

Parameter-level annotations defining agent participants in patterns. Every `AgentRef`-typed parameter on a pattern method MUST carry a role annotation — unannotated `AgentRef` parameters are a build-time error. Each role annotation supports two configuration paths: inline definition via `systemPrompt` (simple) or eidos identity reference via `agentId` (advanced). Exactly one must be specified; specifying both is a build-time error.

### @Agent — generic role annotation

```java
@Retention(RUNTIME) @Target(PARAMETER)
public @interface Agent {
    String name() default "";
    String systemPrompt() default "";
    String agentId() default "";
}
```

Used by patterns without specialized roles: `@Supervisor`, `@Sequence`, `@Parallel`, `@Loop`, `@Conditional`. The `name` attribute provides a label for routing and logging.

### Pattern-specific role annotations

```java
@Retention(RUNTIME) @Target(PARAMETER)
public @interface Debater {
    String role();
    String systemPrompt() default "";
    String agentId() default "";
}

@Retention(RUNTIME) @Target(PARAMETER)
public @interface Voter {
    String role() default "";
    String systemPrompt() default "";
    String agentId() default "";
}

@Retention(RUNTIME) @Target(PARAMETER)
public @interface Judge {
    String systemPrompt() default "";
    String agentId() default "";
}
```

`@Debater` and `@Judge` are used with `@Debate`. `@Voter` is used with `@Voting`. These carry pattern-specific semantics: `@Judge` triggers `JudgeConvergence`, `@Debater.role()` identifies the argumentative position, `@Voter.role()` labels the voting perspective.

### AgentRef production from role annotations

The build extension processes each role-annotated parameter to produce an `AgentRef` and wraps it in a `RoutingCandidate`:

1. **Inline agent** (`systemPrompt` specified) — the recorder creates an `AgentRef.ExternalAgent` that wraps an LLM call using `AgentProvider` resolved from CDI at `RUNTIME_INIT`. The `systemPrompt` and `role` become the agent's briefing. The `RoutingCandidate` has a null `AgentDescriptor`.

2. **Eidos agent** (`agentId` specified) — the build extension consumes eidos build items produced by the eidos build extension (step 2 in §Build step ordering) and matches the `agentId` against registered `AgentDescriptor` identities. The recorder creates an `AgentRef.WorkerAgent` wrapping the eidos-managed agent resolved from CDI at `RUNTIME_INIT`. The `RoutingCandidate` pairs the `AgentRef` with the resolved `AgentDescriptor`, enabling identity-aware routing. If no matching eidos build item exists, build-time validation error: *"agentId 'X' not found — ensure a class annotated with @Identity(id = \"X\") exists and eidos-annotations is on the classpath."* The `agentId` attribute requires `casehub-eidos-annotations` on the classpath; usage without it is a build-time error.

3. **Neither specified** — build-time validation error.

### candidateSupplier population

The build extension collects all role-annotated parameters (`@Agent`, `@Debater`, `@Voter`, `@Judge`) from the pattern method, creates `RoutingCandidate` instances for each (per above), and generates a `Supplier<List<RoutingCandidate>>` that the recorder sets on the pattern builder via `agents(RoutingCandidate...)`. For debate patterns, the build extension also wires the `@Judge`-annotated parameter into `DebateBuilder.judge(AgentRef)`.

---

## Governance Meta-Annotations

Four annotations that compose onto any `@Worker` method (Layer 1) or pattern method (Layer 2). These ADD cross-cutting governance concerns — they don't replace pattern SPIs.

### @OversightGate

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface OversightGate {
    Class<? extends ActionRiskClassifier> value();
    boolean reversible() default true;
    String[] candidateGroups() default {};
}
```

Generates `ActionRiskClassifier` chain wiring. When the classifier returns `GateRequired`, the worker dispatch is gated through `OversightGateService` (WorkItem-based human approval). The blocks build extension produces a `@RiskClassifier`-qualified CDI bean from the specified classifier class.

### @TrustRouted

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface TrustRouted {
    double threshold() default 0.7;
    int minimumObservations() default 10;
    double borderlineMargin() default 0.1;
    double blendFactor() default 0.6;
    double cbrWeight() default 0.0;
}
```

Generates a `TrustRoutingPolicyProvider` CDI bean for the annotated worker or pattern. Attribute defaults match `TrustRoutingPolicy.DEFAULT` — the platform's standard trust routing behaviour — so `@TrustRouted` with no attributes applies platform defaults. Attribute names match the five configurable fields of `TrustRoutingPolicy` (`threshold`, `minimumObservations`, `borderlineMargin`, `blendFactor`, `cbrWeight`). Dynamic floor keys are too dynamic for annotation attributes — configure via a developer-provided `TrustRoutingPolicyProvider` CDI bean that displaces the annotation-generated bean (non-`@DefaultBean` takes priority). When multiple candidate workers compete for a capability, trust scores from the ledger gate selection.

### @CbrRouted

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface CbrRouted {
    double successWeight() default 1.0;
    double gateExpiredWeight() default 0.5;
    double gateRejectedWeight() default 0.25;
    double failureWeight() default 0.0;
}
```

Generates `CbrOutcomeWeights` configuration for CBR evidence routing. The four weight attributes cover the outcomes mapped in `DefaultCbrOutcomeWeights` (`SUCCESS`, `GATE_EXPIRED`, `GATE_REJECTED`, `FAILURE`). The remaining three outcomes (`DECLINED`, `CANCELLED`, `OBSOLETE`) default to `0.0` and are configurable via `@Customize` with a custom `CbrOutcomeWeights` CDI bean — these are rare terminal states that don't justify annotation attributes.

### @Attestation

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Attestation {
    Class<? extends LifecycleAttestationObserver> observer();
    String capabilityTag() default "";
}
```

Wires a `LifecycleAttestationObserver` to the worker's lifecycle events. Attestation intents are written via `AttestationIntentWriter` on worker completion. The build extension resolves the generic type parameter `E` of `LifecycleAttestationObserver<E>` via Jandex type hierarchy inspection at build time — no raw types or unchecked casts at runtime.

---

## Build Extension Architecture

### Build step ordering

The blocks build extension runs AFTER the engine build extension via Quarkus build step ordering:

1. **Engine `EngineAnnotationsProcessor`** — generates `CaseDefinition`, `Worker`, `Capability`, `Binding` beans from `@Case`/`@Worker`/`@Bind`
2. **Eidos build extension** — generates `AgentDescriptor` beans from `@Identity`/`@Disposition`
3. **Work build extension** — generates `WorkItem` config from `@HumanApproval`
4. **Blocks `BlocksAnnotationsProcessor`** — generates `ExecutionModel` beans from pattern annotations, layers governance onto engine-generated and blocks-generated beans

Ordering enforced via `@Consume` on custom build items produced by the engine extension.

### SPI instantiation strategy

Pattern annotations use `Class<? extends SPI>` attributes (e.g., `routing()`, `decomposition()`, `aggregation()`) to declare SPI implementations. The build extension resolves these type references to live instances at `RUNTIME_INIT`:

1. **Annotation default value** — the build extension constructs the same default instance as the corresponding builder's no-arg constructor. The annotation default documents which SPI the builder would use; the recorder replicates the builder's construction logic (e.g., `FirstMatchRouting.class` on `@Supervisor` → `new FirstMatchRouting<>(c -> true)`, matching `SupervisorBuilder()`).

2. **Developer-specified value** — first check whether the specified class equals the annotation's declared default (e.g., `routing = FirstMatchRouting.class` on `@Supervisor` where the default is already `FirstMatchRouting.class`). If so, treat as path §1 — the developer is restating the default, not requesting CDI/constructor resolution. Otherwise, CDI-first resolution:
   a. If the class is a CDI-managed bean (detected via Jandex — `@ApplicationScoped`, `@Dependent`, etc.), the recorder injects it at `RUNTIME_INIT` via `SyntheticBeanBuildItem`
   b. If the class has a public no-arg constructor and is not CDI-managed, the recorder instantiates it directly
   c. Otherwise, build-time validation error: *"SPI class X must be a CDI bean or have a public no-arg constructor. Use @Customize for SPIs requiring constructor arguments."*

3. **@Customize escape hatch** — for SPIs requiring complex construction (constructor arguments, runtime state, injected dependencies), `@Customize` provides full builder access.

This strategy means a developer can write `routing = MyCustomRouter.class` where `MyCustomRouter` is `@ApplicationScoped`, and it just works. SPIs that require constructor arguments (e.g., `LlmSelectedRouting` needs `AgentProvider`) are handled via `@Customize` — the annotation model handles the 80% case, `@Customize` handles the rest.

### Pattern annotation processing

For each pattern annotation found on a method:

1. **Jandex scan** — find all methods annotated with pattern annotations (`@Supervisor`, `@Debate`, etc.)
2. **Parameter extraction** — extract role-annotated parameters (`@Agent`, `@Debater`, `@Voter`, `@Judge`) as `AgentRef` descriptors
3. **Descriptor generation** — create a `PatternDescriptor` recording the pattern type, attributes, and agent participants
4. **Recorder invocation** — `@Record(RUNTIME_INIT)` recorder method builds the `ExecutionModel<T>` via the corresponding `Patterns.*()` builder
5. **CDI bean generation** — `SyntheticBeanBuildItem` produces the `ExecutionModel<Object>` as an `@ApplicationScoped` CDI bean with `.setRuntimeInit()` and `@Named` qualifier. The name is the pattern's `name` attribute if non-empty, otherwise the annotated method name. Build-time validation error if two patterns produce the same `@Named` value. Injection: `@Inject @Named("review") ExecutionModel<Object> reviewModel`. In the `@Case` integration path (D3), the engine resolves the specific `ExecutionModel` by capability name mapped to the `@Named` qualifier.

### Governance annotation processing

For each governance annotation found on a method:

1. **Context detection** — is this on a `@Worker` method (engine Layer 1) or a pattern method (blocks Layer 2)?
2. **Descriptor generation** — create a `GovernanceDescriptor` recording the governance type and configuration
3. **Wiring generation** — produce the appropriate CDI beans (see §Governance wiring mechanisms for details):
   - `@OversightGate` → `@RiskClassifier`-qualified `ActionRiskClassifier` bean
   - `@TrustRouted` → `TrustRoutingPolicyProvider` bean (displaces `DefaultTrustRoutingPolicyProvider`)
   - `@CbrRouted` → `CbrOutcomeWeights` bean (displaces `DefaultCbrOutcomeWeights`)
   - `@Attestation` → `LifecycleAttestationObserver` bean + lifecycle event observer

### Governance wiring mechanisms

Each governance annotation produces specific CDI beans that integrate with the engine's runtime pipeline:

**@OversightGate → `@RiskClassifier`-qualified `ActionRiskClassifier` bean.** The blocks build extension produces a `SyntheticBeanBuildItem` for the developer-specified `ActionRiskClassifier` class, qualified with `@RiskClassifier`. The engine's `ChainedActionRiskClassifier` (which is `@ApplicationScoped`) injects `@RiskClassifier Instance<ActionRiskClassifier> classifiers` and aggregates all classifiers via `mostRestrictive()`. Scoping is at the classifier level — the classifier's `classify(PlannedAction, ClassificationContext)` method receives the action and context, so the classifier itself determines whether it applies to a given worker/action. No per-worker CDI qualification is needed; the classifier implementation gates on action type, capability name, or other context fields.

**@TrustRouted → `TrustRoutingPolicyProvider` CDI bean.** The blocks build extension generates a `TrustRoutingPolicyProvider` implementation that returns a `TrustRoutingPolicy` constructed from the annotation attribute values. This bean is NOT `@DefaultBean`, so it displaces `DefaultTrustRoutingPolicyProvider` (which is `@DefaultBean @ApplicationScoped`). The provider's `forCapability(capabilityName)` method returns the annotation-configured policy for capabilities matching the annotated worker/pattern's name, and delegates to `TrustRoutingPolicy.DEFAULT` for others. The `TrustRoutingPolicyResolver` and preference store are bypassed — annotation values are compile-time constants that don't need runtime preference lookup.

**@CbrRouted → `CbrOutcomeWeights` CDI bean.** The blocks build extension generates a `CbrOutcomeWeights` implementation with weights from the annotation attributes. This bean is NOT `@DefaultBean`, so it displaces `DefaultCbrOutcomeWeights` (which is `@DefaultBean @ApplicationScoped`). The four annotation weights (`successWeight`, `gateExpiredWeight`, `gateRejectedWeight`, `failureWeight`) map directly to `RoutingOutcome` keys. The remaining three outcomes (`DECLINED`, `CANCELLED`, `OBSOLETE`) default to 0.0; custom weights for these use `@Customize` with a custom `CbrOutcomeWeights` bean.

**@Attestation → `LifecycleAttestationObserver` CDI bean + lifecycle event wiring.** The blocks build extension resolves the observer's generic type parameter `E` via Jandex type hierarchy inspection. It produces a CDI observer method (via `ObserverBuildItem`) that listens for worker lifecycle completion events of type `E`. When fired, the observer invokes `LifecycleAttestationObserver.observe(event, context)` and writes the resulting `AttestationIntent` list via `AttestationIntentWriter.write()`. The `capabilityTag` attribute scopes the observer to the specific capability, so only matching lifecycle events trigger attestation.

### @Customize integration

`@Customize` (annotation from engine-annotations-runtime) provides the escape hatch for configuration beyond annotation attributes.

**Engine vs blocks processing:** The engine's `@Customize` handler supports single-parameter methods only — it finds methods matching `(CaseDefinition.Builder)` and invokes with just the builder (`CaseDefinitionRecorder` lines 277-285). The blocks build extension implements its **own enhanced `@Customize` processing** for pattern builder methods, adding CDI parameter resolution. This is new code in `blocks-annotations-deployment`, not a modification to the engine's handler. The two processors handle different builder types and do not conflict.

**Blocks `@Customize` method contract:**

```java
@Customize
static void customize(DebateBuilder<?> builder,
                      AgentProvider agentProvider,
                      ChannelSource channelSource) {
    var judge = AgentRef.external("judge", ctx ->
            agentProvider.invoke(AgentSessionConfig.of("You are the judge.", ctx.toString()))
                         .collect().asList()
                         .map(events -> new AgentResult("judge", /* ... */)));
    builder.convergence(new JudgeConvergence<>(judge, 10))
           .backend(ExecutionBackend.choreographed(channelSource));
}
```

`@Customize` methods are `static`. The first parameter must be a pattern builder type (`DebateBuilder`, `SupervisorBuilder`, etc.). Subsequent parameters are CDI-resolved — the blocks build extension detects all parameter types via Jandex at build time and generates recorder code that resolves them via `Arc.container().select(ParamType.class).get()` at `RUNTIME_INIT`. Build-time validation ensures all non-builder parameters are CDI-resolvable types. The blocks build extension invokes `@Customize` methods after annotation-derived configuration is applied.

Engine issue filed to upstream multi-parameter support into the engine's `@Customize` handler (engine#963).

### Build-time validation rules

The build extension enforces the following constraints at build time:

| Rule | Error |
|------|-------|
| Two pattern annotations on the same method (`@Debate` + `@Supervisor`) | *"Method X has multiple pattern annotations — only one pattern per method"* |
| `@Worker` + pattern annotation on the same method | *"Method X has both @Worker and @Debate — patterns are standalone interfaces, reference via @Worker capability"* |
| `AgentRef` parameter without a role annotation (`@Agent`, `@Debater`, `@Voter`, `@Judge`) | *"AgentRef parameter X on method Y has no role annotation — use @Agent, @Debater, @Voter, or @Judge"* |
| Pattern annotation without role-annotated parameters (where required) | *"@Debate method X has no @Debater parameters"* |
| Role annotation with both `systemPrompt` and `agentId` specified | *"@Agent parameter X specifies both systemPrompt and agentId — use one"* |
| Role annotation with neither `systemPrompt` nor `agentId` | *"@Agent parameter X must specify systemPrompt or agentId"* |
| `agentId` used without eidos-annotations on classpath | *"agentId 'X' requires casehub-eidos-annotations on the classpath"* |
| SPI class not resolvable (see §SPI instantiation strategy) | *"SPI class X must be a CDI bean or have a public no-arg constructor"* |
| `@Attestation` observer class with unresolvable generic type | *"LifecycleAttestationObserver X: cannot resolve generic type E via type hierarchy"* |
| Governance annotation without valid target (neither `@Worker` nor pattern annotation on the method) | *"@OversightGate on method X has no @Worker or pattern annotation to govern"* |
| `@Customize` method with non-CDI-resolvable parameter (blocks processing) | *"@Customize parameter X of type Y is not a CDI bean"* |

---

## Usage Examples

### Governed debate — compliance review

```java
public interface ComplianceReview {

    @Debate(maxRounds = 5)
    @OversightGate(ComplianceRiskClassifier.class)
    @TrustRouted(threshold = 0.7)
    String review(
        @Debater(role = "critic",
                 systemPrompt = "Challenge every compliance claim...")
        AgentRef critic,
        @Debater(role = "advocate",
                 systemPrompt = "Defend the compliance position...")
        AgentRef advocate,
        @Judge(systemPrompt = "Evaluate the debate and declare a winner...")
        AgentRef judge,
        String document);
}
```

### Governed supervisor — incident response

```java
public interface IncidentResponse {

    @Supervisor(maxIterations = 15)
    @OversightGate(IncidentRiskClassifier.class)
    @TrustRouted(threshold = 0.8)
    @Attestation(observer = IncidentAttestationObserver.class,
                 capabilityTag = "incident-triage")
    String triage(
        @Agent(name = "triage", systemPrompt = "Triage and categorise the incident...")
        AgentRef triageAgent,
        @Agent(name = "containment", systemPrompt = "Recommend containment actions...")
        AgentRef containmentAgent,
        @Agent(name = "forensics", systemPrompt = "Analyse evidence and identify root cause...")
        AgentRef forensicsAgent,
        String incidentReport);
}
```

### Multi-agent voting — wildfire resource allocation

```java
public interface WildfireResourceConsensus {

    @Voting(strategy = MajorityVote.class)
    @OversightGate(EvacuationRiskClassifier.class)
    String allocate(
        @Voter(role = "fire-chief",
               systemPrompt = "Prioritise containment...")
        AgentRef fireChief,
        @Voter(role = "medic-lead",
               systemPrompt = "Prioritise evacuation...")
        AgentRef medicLead,
        @Voter(role = "logistics",
               systemPrompt = "Assess resource feasibility...")
        AgentRef logistics,
        String situationReport);
}
```

### Integration with @Case — pattern as worker

```java
@Case(namespace = "ops", name = "Wildfire Response",
      planning = PlanningMode.GOAP)
public interface WildfireCase {

    // This worker is backed by the WildfireResourceConsensus pattern above
    @Worker(capability = "resource-allocation")
    @Bind(contextChange = ".resourcesNeeded == true")
    String allocateResources(String situationReport);

    @Worker(capability = "deploy")
    @Bind(contextChange = ".resourcesAllocated == true")
    @SystemPrompt("Deploy allocated resources to designated zones...")
    String deploy(String allocationPlan);

    // ...
}
```

---

## Testing Strategy

### Unit tests (runtime module)
- Annotation presence and attribute validation (reflection tests)
- `PatternDescriptor` construction from annotated interfaces
- `GovernanceDescriptor` construction from governance annotations

### Build extension tests (deployment module)

Using the Jandex Indexer API pattern (GE-20260819-e4a624 — avoids `QuarkusUnitTest` discovery issues):

```java
private Index indexClasses(Class<?>... classes) throws IOException {
    Indexer indexer = new Indexer();
    for (Class<?> clazz : classes) {
        String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
            indexer.index(stream);
        }
    }
    return indexer.complete();
}
```

- Pattern annotation validation (missing participants, invalid attribute values)
- Governance annotation validation (classifier class not found, invalid trust parameters)
- `@Customize` method detection and parameter type validation
- Dual-annotation conflict detection (`@Worker` + `@Debate` on same method → build error)
- Build step ordering verification

### Integration tests (example modules)
- Generated `ExecutionModel` matches builder-equivalent model
- Governance wiring produces correct CDI beans
- Standalone execution via `execute()`
- Case-integrated execution via `@Worker` capability reference
- Mixed mode: annotation-defined patterns with builder-defined governance (and vice versa)

---

## Example Modules

Three example modules extending engine#945 base cases. Each demonstrates progressive disclosure — the engine example shows the case, the blocks example adds governance and orchestration.

| Module | Domain | Engine base | Blocks patterns | Governance |
|--------|--------|-------------|----------------|------------|
| `incident-response-blocks` | Cybersecurity | engine#945 | `@Supervisor` (triage), `@Debate` (containment strategy) | `@OversightGate`, `@TrustRouted`, `@Attestation` |
| `aircraft-maintenance-blocks` | Aviation MRO | engine#945 | `@Debate` (repair strategy) | `@OversightGate` (sign-off), `@CbrRouted` |
| `wildfire-response-blocks` | Disaster mgmt | engine#945 | `@Voting` (consensus), `@Htn` (multi-phase) | `@OversightGate` (evacuation) |

Each example module depends on its engine#945 base and adds blocks-specific annotations to selected workers. Coverage: 4 pattern annotations (`@Supervisor`, `@Debate`, `@Voting`, `@Htn`) + all 4 governance annotations across 3 domains. The governed supervisor in `incident-response-blocks` is the flagship use case — `@Supervisor` + `@OversightGate` + `@TrustRouted` + `@Attestation` demonstrates the full annotation model value proposition.

---

## Dependencies and Ordering

| Dependency | Status | Notes |
|-----------|--------|-------|
| engine#909 | **Shipped** | Engine-annotations Layer 1 (`@Case`, `@Worker`, `@Customize`) — runtime + deployment live |
| engine#945 | **Shipped** | Engine example bases (incident-response, aircraft-maintenance, wildfire-response, etc.) — available for blocks examples to extend |
| eidos#139 | **Shipped** | Eidos-annotations (`@Identity`, `@Disposition`) — compose onto pattern interfaces |
| blocks#150 | **Separate** | LC4j integration module — dual-track Track 2. Not blocking blocks-annotations |

---

## What Stays Builder/YAML Only

| Capability | Why |
|-----------|-----|
| Event-driven execution (`EventSource`, `ChoreographedDriver`) | Runtime abstraction — `@Customize` escape hatch |
| Custom `TerminationCondition` composition | Too expressive for annotation attributes — use `@Customize` |
| Dynamic routing logic | Runtime decisions need code |
| `FailurePolicy` fine-tuning | Backoff, retry, replan policies — use `@Customize` |
| Advanced `DecompositionHeuristic` composition | `CompositeHeuristic` weights — use `@Customize` |

The annotation model handles the 80% case. The builders handle the 20% that needs runtime flexibility. `@Customize` bridges the gap.

**YAML relationship:** Annotations define pattern structure at compile time. YAML configuration (`application.properties`) can override scalar parameters at runtime (e.g., `casehub.blocks.patterns.my-debate.max-rounds=10`). Annotations take precedence for structural configuration (which SPI classes, which agents); YAML provides runtime tuning. YAML-driven pattern definitions (defining entire patterns in YAML without annotations) are a separate concern not addressed by this spec. The epic's principle 4 ("Full interop with existing fluent builders and YAML") is satisfied for builders via `@Customize`; YAML overlay design is tracked separately (blocks#153).

---

## References

- ADR-0004: `docs/adr/0004-own-orchestration-annotations.md`
- Engine-annotations spec: `casehub-engine/docs/specs/issue-909-engine-annotations/2026-08-16-annotation-driven-programming-model-design.md`
- Engine-annotations decisions: `casehub-engine/docs/specs/issue-909-engine-annotations/decisions.md`
- `io.casehub.blocks.agentic.pattern.Patterns` — 8 pattern builders
- `io.casehub.blocks.agentic.model.ExecutionModel` — 5-SPI composition record
- `io.casehub.blocks.agentic.pattern.AbstractPatternBuilder` — base builder with SPI setters
- `io.casehub.blocks.agentic.pattern.DebateBuilder` — debate-specific builder
- GE-20260818-d7915b — SyntheticBeanBuildItem.supplier() non-recordable objects
- GE-20260817-48caeb — setRuntimeInit() required for RUNTIME_INIT recorders
- GE-20260614-efee3b — SyntheticBeanBuildItem without addInjectionPoint
- GE-20260604-9d91f9 — Propagate inherited interceptor bindings
- GE-20260819-e4a624 — Test build extension validation with Jandex Indexer API
- GE-20260521-977e3e — Void @BuildStep silently elided
- LC4j source analysis: `SupervisorPlanner`, `PlannerAgent`, `GoalOrientedPlanner`, `DebatePlanner`, `VotingPlanner`
- Blog: `docs/blog/2026-08-22-mdp01-annotation-boundaries-follow-execution-models.md`
- LC4j integration: blocks#150
- Platform boundary-rules: `casehub-parent/docs/platform/boundary-rules.md`
- Platform capability-ownership: `casehub-parent/docs/platform/capability-ownership.md`
