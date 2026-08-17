# Annotation-Driven Agent Programming Model for CaseHub

**Date:** 2026-08-14
**Status:** Draft
**Scope:** engine-api, eidos-api, work-api, blocks, blocks-engine-adapter
**Epic:** TBD (to be filed before implementation begins)
**Companion:** `embabel-comparison.md` (competitive analysis, outside git)

## Motivation

CaseHub is architecturally deeper than competing frameworks (embabel-agent, Spring AI) across governance, deliberation, planning, and case management. But the entry cost is high — 5 SPIs, builder chains, sealed hierarchies. embabel demonstrates that an annotation-driven model can make agent development accessible without sacrificing composability.

CaseHub depends on LangChain4j 1.14.1 (`langchain4j-core`). The `langchain4j-agentic` module provides 36 annotations covering basic workflow patterns, processed by the Quarkus LangChain4j build extension at build time. CaseHub adopts this as the base layer and extends with domain-specific annotations for case management, governance, and deliberation.

**Dependency status:** `langchain4j-agentic` is not yet on the CaseHub classpath. It must be added as an explicit dependency when the module is available and its API has stabilised. If the module's release is delayed or its annotation surface changes materially, CaseHub provides equivalent Layer 0 annotations in `casehub-engine-api` and migrates to LC4j annotations when they ship. The Layer 1/2 annotations are agnostic to whether Layer 0 is LC4j-native or CaseHub-provided — the build extension architecture handles both scenarios via the composition model described in §Build Extension Architecture.

The goal: a developer can build a working CaseHub agent in 20 lines. A platform-aware developer can add oversight, trust, and CBR routing with 3 more annotations. Both produce the same underlying types as the existing fluent builders.

## Design Principles

1. **Adopt, don't reinvent** — use LangChain4j annotations for patterns LC4j already covers.
2. **Annotations at the API layer** — each annotation lives in the module that owns its concept.
3. **Progressive disclosure** — simple annotations for the 80% case; power annotations for governance.
4. **Full interop** — annotations produce the same types as builders. Mix freely.
5. **Build-time wiring** — Quarkus build extensions for validation and wiring; CDI for discovery.
6. **Upstream when general** — contribute debate, voting, cost/value, and extension hooks to LC4j.

---

## Layer 0: LangChain4j Agentic (adopt as-is)

**Module:** `langchain4j-agentic` (dependency, no CaseHub code)
**Processor:** Quarkus LangChain4j extension (exists)

### Annotations adopted

| Annotation | Target | Purpose |
|---|---|---|
| `@Agent` | METHOD | Base agent declaration — name, description, outputKey, async, optional |
| `@SupervisorAgent` | METHOD | Autonomous coordination of sub-agents via LLM |
| `@SequenceAgent` | METHOD | Ordered sub-agent pipeline |
| `@ParallelAgent` | METHOD | Concurrent sub-agent execution |
| `@ParallelMapperAgent` | METHOD | Parallel instances per collection item |
| `@LoopAgent` | METHOD | Iterative execution until exit condition |
| `@ConditionalAgent` | METHOD | Predicate-based routing to sub-agents |
| `@PlannerAgent` | METHOD | Custom planner-driven execution |
| `@McpClientAgent` | METHOD | Wraps MCP tool as non-AI agent |
| `@A2AClientAgent` | METHOD | A2A protocol client agent |
| `@HumanInTheLoop` | METHOD | Human input during workflow |
| `@RegistryAgent` | METHOD | Loads agent from registry by name |
| `@ActivationCondition` | METHOD | Predicate for `@ConditionalAgent` (static, returns boolean) |
| `@ExitCondition` | METHOD | Loop exit predicate (static, returns boolean) |
| `@ErrorHandler` | METHOD | Error recovery (static, returns `ErrorRecoveryResult`) |
| `@Output` | METHOD | Output transformation combining scope states |
| `@K` | PARAMETER | Typed state key injection (`TypedKey<T>`) |
| `@V` | PARAMETER | Template variable injection |
| `@LoopCounter` | PARAMETER | Loop iteration count injection |
| `@ChatModelSupplier` | METHOD | Per-agent LLM model (static supplier) |
| `@StreamingChatModelSupplier` | METHOD | Streaming model supplier |
| `@PlannerSupplier` | METHOD | Custom planner supplier |
| `@ToolsSupplier` | METHOD | Tool objects supplier |
| `@ToolProviderSupplier` | METHOD | ToolProvider supplier |
| `@ChatMemorySupplier` | METHOD | Chat memory supplier |
| `@ContentRetrieverSupplier` | METHOD | RAG content retriever supplier |
| `@RetrievalAugmentorSupplier` | METHOD | RAG augmentor supplier |
| `@SystemMessageProviderSupplier` | METHOD | Dynamic system message |
| `@UserMessageProviderSupplier` | METHOD | Dynamic user message |
| `@AgentListenerSupplier` | METHOD | Observability listener |
| `@McpClientSupplier` | METHOD | MCP client for `@McpClientAgent` |
| `@A2AClientCustomizer` | METHOD | A2A client configuration |
| `@SupervisorRequest` | METHOD | Request string for supervisor |
| `@ParallelExecutor` | METHOD | Executor for parallel agents |
| `@Tool` | METHOD | LLM-callable function (from `langchain4j-core`) |
| `@P` | PARAMETER | Tool parameter description |
| `@SystemMessage` | TYPE, METHOD | System prompt template |
| `@UserMessage` | METHOD, PARAMETER | User prompt template |
| `@InputGuardrails` | TYPE, METHOD | Input validation guardrails |
| `@OutputGuardrails` | TYPE, METHOD | Output validation guardrails |

### Programming model

Agents are methods on interfaces. Configuration via static supplier methods on the same interface:

```java
public interface DocumentAnalyser {

    @Agent(description = "Analyse a document and extract key findings")
    @SystemMessage("You are an expert document analyst...")
    @UserMessage("Analyse this document: {{document}}")
    String analyse(@V("document") String document);

    @ChatModelSupplier
    static ChatModel model() {
        return OpenAiChatModel.builder().modelName("gpt-4.1").build();
    }
}
```

Orchestration composes agents via `subAgents`:

```java
public interface ReviewPipeline {

    @SequenceAgent(outputKey = "review",
                   subAgents = { DocumentAnalyser.class, RiskAssessor.class, SummaryWriter.class })
    String review(@V("document") String document);
}
```

---

## Layer 1: CaseHub Engine Annotations (case lifecycle)

**Module:** `casehub-engine-api`
**Processor:** New Quarkus build extension in `casehub-engine`
**Produces:** `CaseDefinition`, `Worker`, `Capability`, `Binding` instances (same types as builders)

### New annotations

#### `PlanningMode` (engine-api enum)
```java
public enum PlanningMode {
    EXPLICIT,  // default — requires @Bind triggers for execution ordering
    GOAP       // auto-infer dependencies from @Worker parameter/return types
}
```

#### `@Case`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface Case {
    String namespace();
    String name();
    String version() default "1.0.0";
    String title() default "";
    String description() default "";
    PlanningMode planning() default PlanningMode.EXPLICIT;
}
```

Marks an interface as a case definition. The build extension generates a `CaseDefinition` from the annotated interface's methods.

When `planning = PlanningMode.GOAP`, the build extension infers dependencies from `@Worker` method parameter/return types and generates `GoapAction` preconditions/effects (see Gap 1). When `planning = PlanningMode.EXPLICIT` (default), `@Bind` triggers must explicitly define execution ordering.

#### `@Worker`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Worker {
    String capability() default "";          // single capability name (convenience — mutually exclusive with capabilities)
    String[] capabilities() default {};      // multiple capability names (maps to Worker.capabilityNames)
    String description() default "";
    int cost() default 0;       // GOAP planner cost; 0 = unweighted
    int value() default 0;      // GOAP planner value; 0 = unweighted
    int timeoutMs() default 0;  // 0 = use system default (casehub.engine.worker.default-timeout-ms)
    int maxRetries() default 0; // 0 = no retries
}
```

Declares a method as a worker implementation for one or more named capabilities. The method signature defines the worker function — parameters are inputs, return type is output. The underlying `Worker` record uses `Set<String> capabilityNames` — `capability` is sugar for the single-capability case.

`cost` and `value` feed into `GoapPlanner` path optimisation and `HeuristicDecomposition` method ranking. For dynamic cost/value, use `@WorkerCost`/`@WorkerValue` supplier methods (see Gap 3) — dynamic suppliers take precedence over static annotation attributes.

`timeoutMs` and `maxRetries` map to `ExecutionPolicy` fields. The build extension generates the equivalent `ExecutionPolicy` and `RetryPolicy`. For advanced retry configuration (backoff, delays), use the builder API.

#### `@Worker` → `Worker` type mapping

The `@Worker` annotation method becomes a `Worker` instance via the engine build extension. The mapping is:

| Annotation concept | Builder/type equivalent | How |
|---|---|---|
| Method name | `Worker.name` | Derived from method name (camelCase) |
| `capability`/`capabilities` | `Worker.capabilityNames` | Direct mapping |
| Method return type | `Worker.outputType` | `Class<?>` from method's return type via Jandex |
| Method parameter types | `Worker.inputSchema` | Generated JSON Schema from parameter types + `@V` names |
| `@V`-annotated parameters | `WorkerFunction` input keys | Each `@V("name")` parameter becomes a named input field in the worker's input map |
| `cost`, `value` | `Worker.cost`, `Worker.value` | Direct mapping — feed into `GoapPlanner` and `HeuristicDecomposition` |
| `timeoutMs`, `maxRetries` | `ExecutionPolicy` | Build extension generates `ExecutionPolicy` record |
| `@SystemMessage` + `@UserMessage` | AI service delegation | Build extension composes with LC4j — generates `WorkerFunction` that delegates to the LC4j AI service bean |

**Input resolution:** When the engine invokes a `@Worker` method, the CDI proxy unpacks the case context into the method's `@V`-annotated parameters by name. Non-`@V` typed parameters (e.g., `AnalysisResult analysis` without `@V`) are resolved from the case context by type — this is the same mechanism used for GOAP dependency inference. The `WorkerFunction.apply(input, scope)` adapter receives `input` as a `Map<String, Object>` built from the case context keys matching the method's parameter names and types.

**Scope access:** The builder API's `WorkerFunction.Sync.fn()` receives `WorkerScope` as its second parameter (case ID, task ID, accumulated state, channel access). Annotation-defined workers access the same `WorkerScope` via CDI injection — the engine provides it as a request-scoped bean during worker execution:

```java
@Worker(capability = "analyse")
AnalysisResult analyse(@V("document") String document) {
    // scope available via CDI injection if needed
}

@Inject
WorkerScope scope;  // request-scoped, available during worker execution
```

`WorkerScope` is an existing type in `casehub-worker-api` (`io.casehub.worker.api.WorkerScope`) — it provides `caseId()`, `taskId()`, `accumulatedState()`, `execute()` for sub-worker dispatch, and `channel()` for data channel access. The engine build extension registers a request-scoped CDI producer that wraps the current execution's `WorkerScope` instance.

#### `@Capability`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Capability {
    String name() default "";            // defaults to method name
    String description() default "";
    String inputJq() default "";         // JQ expression for input transformation
    String inputJsonSchema() default ""; // JSON Schema for input validation
    String outputSchema() default "";
}
```

Declares a capability on a case. When applied alongside `@Worker`, the capability is auto-registered with the worker's capability name. When applied alone, it declares a capability that must be satisfied by an external worker.

**Interaction with `@Worker`:** When both `@Capability` and `@Worker` are present on the same method, `@Worker.capability()` determines the capability name. If `@Capability.name()` is also set and differs, the build extension emits a compile-time error. `@Capability` contributes schema metadata; `@Worker` contributes implementation binding.

#### `@Bind`
```java
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(Bindings.class)
public @interface Bind {
    String capability() default "";        // target capability name — resolves to CapabilityTarget
    String contextChange() default "";     // JQ expression on context change
    String event() default "";             // CloudEvent type name
    String cron() default "";              // cron schedule expression
    boolean scopeActivated() default false; // fires when lifecycle scope activates
    String listenLayer() default "";       // layer-specific context listening (maps to ContextChangeTrigger.listenLayer)
    String when() default "";              // JQ guard expression (additional filter applied after trigger matches)
    LifecycleScope scope() default LifecycleScope.CASE;      // io.casehub.api.model.LifecycleScope
    Participation participation() default Participation.PARTICIPANT; // io.casehub.api.model.Participation
    String conflictStrategy() default "";  // LAST_WRITER_WINS, FIRST_WRITER_WINS, FAIL — default: LAST_WRITER_WINS
    String[] producedKeys() default {};    // context keys this binding writes (enables static conflict detection)
}
```

Binds a method to a trigger, targeting a specific capability. Trigger type is determined by which attribute is set — exactly one of `contextChange`, `event`, `cron`, or `scopeActivated` must be non-default. The build extension validates mutual exclusivity at build time.

`@Repeatable` allows multiple bindings on the same method for different triggers. `capability` names the target capability — the build extension resolves it to a `CapabilityTarget` wrapping the matching `@Capability` or `@Worker` declaration on the same `@Case` interface. When `capability` is omitted, the binding target must be configured via `@Customize("methodName")` with `Binding.Builder` (for sub-case or human-task targets).

**Trigger mapping:** Each trigger attribute maps to an existing `Trigger` implementation:
- `contextChange` → `ContextChangeTrigger(filter, listenLayer)` — `listenLayer` is optional, defaults to all layers
- `event` → `CloudEventTrigger(type)` — pending engine support (see `CaseDefinitionYamlMapper` TODO)
- `cron` → `ScheduleTrigger(expression)`
- `scopeActivated` → `ScopeActivatedTrigger()`

**Naming rationale:** The annotation is `@Bind`, not `@Trigger`, to avoid collision with the existing `io.casehub.api.model.Trigger` interface in the same module. Trigger attributes are inlined into `@Bind` because the trigger was only used as a nested annotation inside `@Bind` — flattening removes a layer of nesting without loss of expressiveness.

**Existing types referenced:** `LifecycleScope` (`BINDING`, `COMPOUND`, `CASE`) and `Participation` (`PARTICIPANT`, `COMPANION`) are existing enums in `io.casehub.api.model`.

#### `@Goal`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Goal {
    String value();  // goal description
}
```

#### `@Milestone`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Milestone {
    String name();
    String entryCriteria() default "";
    String completionCriteria() default "";
    SlaStartFrom slaStartFrom() default SlaStartFrom.MILESTONE_ACTIVATED; // io.casehub.api.model.SlaStartFrom
}
```

**Existing type referenced:** `SlaStartFrom` (`CASE_CREATED`, `MILESTONE_ACTIVATED`) is an existing enum in `io.casehub.api.model`.

#### `@Completion`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Completion {}
```

Marks a method that returns the case's completion `GoalExpression`.

### Example: annotated case definition

```java
@Case(namespace = "legal", name = "Document Review", version = "1.0.0")
public interface DocumentReviewCase {

    @Worker(capability = "analyseDocument")
    @SystemMessage("You are a document analyst. Extract key findings.")
    @UserMessage("Analyse: {{document}}")
    AnalysisResult analyse(@V("document") String document);

    @Worker(capability = "extractClauses")
    @SystemMessage("Extract legally significant clauses.")
    @UserMessage("Document: {{document}}")
    List<Clause> extractClauses(@V("document") String document);

    @Worker(capability = "assessRisk")
    @SystemMessage("Assess legal risk based on analysis and clauses.")
    RiskAssessment assessRisk(@V("analysis") AnalysisResult analysis,
                              @V("clauses") List<Clause> clauses);

    @Bind(capability = "analyseDocument", contextChange = ".status == 'received'")
    default void onReceived(@V("document") String doc) { analyse(doc); }

    @Bind(capability = "extractClauses", contextChange = ".analysisComplete == true")
    default void afterAnalysis(@V("analysis") AnalysisResult a,
                               @V("document") String doc) { extractClauses(doc); }

    @Bind(capability = "assessRisk", contextChange = ".clausesExtracted == true",
          when = ".priority == 'high'")
    default void afterClauses(@V("analysis") AnalysisResult a,
                              @V("clauses") List<Clause> c) { assessRisk(a, c); }

    @Milestone(name = "Analysis Complete",
               entryCriteria = ".status == 'received'",
               completionCriteria = ".analysisComplete == true")
    default void analysisComplete() {}

    @Goal("Document fully reviewed with risk assessment")
    @Completion
    default GoalExpression done() {
        return GoalExpression.allOf(
            GoalExpression.goal("analysisComplete"),
            GoalExpression.goal("clausesExtracted"),
            GoalExpression.goal("riskAssessed"));
    }

    @ChatModelSupplier
    static ChatModel model() {
        return AnthropicChatModel.builder().modelName("claude-sonnet-5").build();
    }
}
```

Compare to the current builder equivalent — this is roughly 60% less code, and the structure is visible at a glance.

### Interop with existing builders

The build extension generates a `CaseDefinition` bean. A builder-defined case and an annotation-defined case are the same type:

```java
// Annotation-defined worker used in a builder-defined case
@Case(namespace = "legal", name = "Mixed")
public interface MixedCase {
    @Worker(capability = "analyse")
    String analyse(@V("doc") String doc);
}

// Builder code can reference the generated worker
var definition = CaseDefinition.builder()
    .namespace("legal").name("Extended")
    .workers(generatedAnalyseWorker, manuallyBuiltWorker)
    .build();
```

### `@Customize` — escape hatch for advanced configuration

**Module:** `casehub-engine-api` (the annotation is a generic marker — engine-api owns it because the primary targets are engine types: `CaseDefinition.Builder`, `Binding.Builder`. The blocks build extension also scans for it when the parameter type is a pattern builder (`DebateBuilder<T>`, `SupervisorBuilder<T>`, etc.), following the same cross-module scanning pattern as eidos and work annotations.)

Annotation-defined cases cover the 80% of `CaseDefinition` fields that most cases need. For advanced fields (authorization, routing strategies, cognitive demands, channels, etc.), a `@Customize` method receives the generated builder before the bean is registered:

#### `@Customize`
```java
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(Customizers.class)
public @interface Customize {
    String value() default "";  // for Binding.Builder: the @Bind method name to target
}
```

```java
@Case(namespace = "legal", name = "Governed Review")
public interface GovernedReviewCase {

    @Worker(capability = "analyse")
    AnalysisResult analyse(@V("document") String document);

    @Bind(capability = "analyse", contextChange = ".status == 'received'")
    default void onReceived(@V("document") String doc) { analyse(doc); }

    @Customize
    static void customize(CaseDefinition.Builder builder) {
        builder.authorization(Map.of(AclAction.READ, List.of("legal-team")))
               .planningStrategy("goap")
               .cognitiveDemand("analyse", new CognitiveDemand(5, Set.of("legal-analysis")))
               .channel("findings", Finding.class);
    }

    @Customize("onReceived")
    static void customizeReceivedBinding(Binding.Builder builder) {
        builder.outcomePolicy(new OutcomePolicy(...))
               .executionMode(ExecutionMode.DURABLE);
    }
}
```

**Semantics:**
- The `@Customize` method must be `static` and accept exactly one parameter — the parameter type determines what is customized
- `CaseDefinition.Builder` — customizes the generated case definition (access to all ~40 fields). `value()` is ignored.
- `Binding.Builder` — customizes a specific binding. `value()` is **required** and must name the `@Bind`-annotated method whose binding is being customized. The build extension emits a compile error if `value()` is empty or names a method without `@Bind`.
- Pattern builder (e.g., `DebateBuilder<T>`, `SupervisorBuilder<T>`, `HtnBuilder<T>`) — customizes the generated execution model. The build extension determines the expected builder type from the orchestration annotation on the interface. `value()` is **required** and must name the orchestration method being customized. Provides full access to the pattern builder's SPIs: routing, decomposition, activation, aggregation, termination, listeners, backend. There is no `ExecutionModel.Builder` — each pattern has its own concrete builder in `io.casehub.blocks.agentic.pattern`.
- The build extension calls the customizer after processing all annotations, so annotation-set values are already on the builder
- Multiple `@Customize` methods are allowed — `@Repeatable` supports customizing multiple bindings or combining case + binding customizers

This eliminates the "abandon annotations entirely" cliff: annotation-defined cases get the full builder API for the long tail of configuration fields, without losing the annotation model's readability for the common fields.

### Binding execution model

When a `@Bind` default method invokes a `@Worker` method, the call flows through the engine's worker execution system, not as a direct method call:

1. **Dispatch:** The CDI proxy intercepts the `@Worker` method call and dispatches it through the engine's worker executor. The call is asynchronous — it does not block the event loop or the binding trigger thread.

2. **Context writes:** The worker's return value is automatically written to the case context using the capability name as the key (e.g., `analyse` returns `AnalysisResult` → context key `"analyseDocument"` is set). This context write triggers re-evaluation of other bindings' trigger expressions.

3. **Error handling:** If the worker call fails (LLM timeout, rate limit, content filter), the failure is handled by the case's failure policy (see `@OnFailure` in Layer 2). Without `@OnFailure`, `FailurePolicy.defaults()` applies — 3 retries with fixed 1-second backoff, `FAIL` on exhaustion.

4. **Concurrency:** When multiple `@Bind` methods match the same context change, the engine executes them according to the case's binding execution model. `conflictStrategy` on `@Bind` controls concurrent write resolution. `producedKeys` enables the engine to detect potential write conflicts at build time.

5. **Ordering:** Bindings do not have guaranteed execution order — they are event-driven. A binding's context writes trigger other bindings reactively. This is the engine's existing execution model, not something the annotation layer reinvents. If deterministic ordering is needed, use explicit sequencing via context guards (`when` expressions) or `planning = PlanningMode.GOAP`.

### Serverless Workflow integration

`@Worker` methods can dispatch to Serverless Workflow steps via `casehub-engine-flow`:

```java
@Worker(capability = "orchestrateReview")
default WorkerResult orchestrate(WorkerInput input) {
    return CasehubFlow.dispatch("analyseDocument", input);
}
```

---

## Layer 2: CaseHub Domain Annotations

This layer spans multiple modules, following Design Principle 2 — each annotation lives in the module that owns its concept. The blocks build extension in `casehub-blocks-engine-adapter` composes across all modules at build time, but annotations themselves live in their home module's api package.

| Sub-domain | Module | Annotations |
|---|---|---|
| Orchestration patterns | `casehub-blocks` (api) | `@DebateAgent`, `@VotingAgent`, `@HtnAgent`, `@Convergence`, role annotations, `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation`, `@OnFailure`, `@Decompose` |
| Agent identity | `casehub-eidos-api` | `@Identity`, `@Disposition`, `@AgentGoals`, `@AgentConstraints`, `@Discoverable` |
| Human-in-the-loop | `casehub-work-api` | `@HumanApproval`, `@RequiresQuorum`, `@Escalate` |

### Orchestration annotations (blocks patterns)

**Module:** `casehub-blocks` (api package)
**Processor:** New Quarkus build extension in `casehub-blocks-engine-adapter`
**Produces:** `ExecutionModel`, `PatternBuilder` results, interceptor wiring (same types as `Patterns.*()`)

#### CaseHub-specific orchestration patterns

#### `@DebateAgent`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface DebateAgent {
    String name() default "";
    String description() default "";
    int maxRounds() default 5;
    Class<? extends AggregationStrategy> aggregation() default MajorityVote.class;
}
```

No LC4j equivalent. Maps to `Patterns.debate()`.

#### `@VotingAgent`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface VotingAgent {
    String name() default "";
    String description() default "";
    Class<? extends AggregationStrategy> aggregation() default MajorityVote.class;
}
```

No LC4j equivalent. Maps to `Patterns.voting()`.

#### `@HtnAgent`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface HtnAgent {
    String name() default "";
    String description() default "";
    int maxDepth() default 1;
    Class<? extends io.casehub.engine.plan.DecompositionStrategy> decomposition()
        default CapabilityDependencyDecomposition.class;
}
```

No LC4j equivalent. Maps to `Patterns.htn()`.

**Note:** `DecompositionStrategy` is `io.casehub.engine.plan.DecompositionStrategy<T>` — a single interface. All blocks implementations (`CapabilityDependencyDecomposition`, `HeuristicDecomposition`, `HybridDecomposition`, etc.) implement this engine interface.

#### Role annotations (for debate/voting patterns)

```java
@Retention(RUNTIME) @Target(PARAMETER)
public @interface Debater {
    String role();
    String systemPrompt() default "";
}

@Retention(RUNTIME) @Target(PARAMETER)
public @interface Voter {}

@Retention(RUNTIME) @Target(PARAMETER)
public @interface Judge {}
```

#### `ConvergenceStrategy` (blocks enum)
```java
public enum ConvergenceStrategy {
    STRUCTURAL,          // ConvergencePolicies.structural(threshold, staleRounds)
    COMMON_GROUND_RATIO  // ConvergencePolicies.commonGroundRatio(threshold, deadlockDisputeRatio)
}
```

#### `@Convergence`
```java
@Retention(RUNTIME)
@Target(METHOD)  // standalone — presence on method = explicit convergence
public @interface Convergence {
    ConvergenceStrategy strategy() default ConvergenceStrategy.STRUCTURAL;
    double threshold() default 0.85;
    int staleRounds() default 2;                // STRUCTURAL only — rounds without new points before DIMINISHING_RETURNS
    double deadlockDisputeRatio() default 0.3;  // COMMON_GROUND_RATIO only — disputed fraction triggering DEADLOCK
}
```

`@Convergence` is a standalone method-level annotation. Its presence on an orchestration method means "explicit convergence was set" — its absence means "use the pattern's default termination." This maps directly to `DebateBuilder.convergenceExplicitlySet`.

The build extension maps `ConvergenceStrategy` to the corresponding `ConvergencePolicies` factory method:
- `STRUCTURAL` → `ConvergencePolicies.structural(threshold, staleRounds)` — `deadlockDisputeRatio` is ignored
- `COMMON_GROUND_RATIO` → `ConvergencePolicies.commonGroundRatio(threshold, deadlockDisputeRatio)` — `staleRounds` is ignored

The build extension validates at build time that only strategy-relevant parameters are set to non-default values. Custom convergence policies (e.g., `composite()`) use the builder API — the annotation model covers the two standard strategies.

### Agent identity annotations (eidos integration)

**Module:** `casehub-eidos-api` (annotations follow Design Principle 2 — eidos owns identity concepts)
**Processor:** Blocks build extension in `casehub-blocks-engine-adapter` (cross-module composition)

CaseHub's eidos module provides a full agent identity and personality system (`AgentDescriptor`, `AgentDisposition`, `AgentGoal`, `AgentConstraint`). No competing framework has anything equivalent. These annotations make eidos identity declarative.

#### `@Identity`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface Identity {
    String slot();
    String provider() default "";
    String modelFamily() default "";
    String jurisdiction() default "";
    String dataHandlingPolicy() default "";
    String briefing() default "";
    String vocabulary() default "";  // domain vocabulary URI
}
```

Declares eidos identity metadata on an agent interface. The build extension generates an `AgentDescriptor` and registers it with `AgentRegistry`.

#### `@Disposition`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface Disposition {
    String socialOrient() default "";
    String ruleFollowing() default "";
    String riskAppetite() default "";
    String autonomy() default "";
    String conflictMode() default "";
    boolean delegation() default false;
    String[] dispositionProfile() default {};
}
```

Declares personality axes on the 5 eidos `DispositionAxis` dimensions. Values are vocabulary-controlled terms. The `SystemPromptRenderer` automatically renders disposition into system prompts.

**Type mapping:** Each `String` axis value maps to `DispositionValue.of(term)` — a single-term value with default weight 1.0. This matches the `AgentDisposition.Builder` simple API (e.g., `builder.socialOrient("collaborative")`). For multi-value axes or weighted values, use `AgentDisposition.builder()` directly.

**`dispositionProfile`:** Maps to `AgentDisposition.dispositionProfile` (`List<DispositionValue>`). Each string becomes `DispositionValue.of(term)`. This field supports disposition values outside the five named axes.

**Field naming:** Annotation fields match the `AgentDisposition` record fields exactly (`socialOrient`, not `socialOrientation`) to maintain consistency with the eidos API.

#### `@AgentGoals`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface AgentGoals {
    AgentGoalDef[] value();
}

@Retention(RUNTIME)
@Target({})
public @interface AgentGoalDef {
    String name();
    String description() default "";
    GoalPriority priority() default GoalPriority.MEDIUM;
    Visibility visibility() default Visibility.PUBLIC;
    String[] capabilities() default {};
}
```

#### `@AgentConstraints`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface AgentConstraints {
    AgentConstraintDef[] value();
}

@Retention(RUNTIME)
@Target({})
public @interface AgentConstraintDef {
    String name();
    String description() default "";
    ConstraintSeverity severity() default ConstraintSeverity.MUST;
    Visibility visibility() default Visibility.PUBLIC;
}
```

#### Example: fully identified agent

```java
@Identity(slot = "legal-analyst",
          provider = "casehub",
          modelFamily = "claude",
          jurisdiction = "EU",
          dataHandlingPolicy = "gdpr-compliant",
          briefing = "Senior legal analyst specialising in regulatory compliance")
@Disposition(socialOrient = "collaborative",
             ruleFollowing = "strict",
             riskAppetite = "cautious",
             autonomy = "guided",
             conflictMode = "accommodating")
@AgentGoals({
    @AgentGoalDef(name = "accurate-analysis",
                  description = "Produce accurate legal analysis",
                  priority = GoalPriority.HIGH,
                  capabilities = {"analyseDocument"}),
    @AgentGoalDef(name = "regulatory-compliance",
                  description = "Ensure all outputs meet regulatory requirements",
                  priority = GoalPriority.CRITICAL)
})
@AgentConstraints({
    @AgentConstraintDef(name = "no-legal-advice",
                        description = "Must not provide binding legal advice",
                        severity = ConstraintSeverity.MUST)
})
public interface LegalAnalystAgent {

    @Agent(description = "Analyse document for regulatory compliance")
    @SystemMessage("You are a senior legal analyst...")
    String analyse(@V("document") String document);
}
```

The build extension generates an `AgentDescriptor`, registers it with `AgentRegistry`, and the `SystemPromptRenderer` automatically incorporates the disposition and constraints into the agent's system prompt at runtime.

This is a massive differentiator — no other framework allows declarative agent personality that shapes LLM behavior and influences routing decisions (via `DispositionAwareRouting`).

### Human-in-the-loop annotations (work integration)

**Module:** `casehub-work-api` (annotations follow Design Principle 2 — work owns human task concepts)
**Processor:** Blocks build extension in `casehub-blocks-engine-adapter` (cross-module composition)

CaseHub's work module provides enterprise-grade HIL infrastructure — 11-state WorkItem lifecycle, AI-powered routing, quorum patterns, workflow suspension, templates. LC4j's `@HumanInTheLoop` reads from console. These annotations bridge the work module into the annotation model.

#### `@HumanApproval`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface HumanApproval {
    String title();
    String[] candidateGroups() default {};
    String[] candidateUsers() default {};
    WorkItemPriority priority() default WorkItemPriority.MEDIUM;
    String claimDeadline() default "";   // ISO-8601 duration
    String expiresAt() default "";       // ISO-8601 duration
    Class<?> outcomeType() default Void.class;
}
```

Creates a `WorkItem` and suspends the agent workflow via `HumanTaskFlowBridge`. Resumes when a human completes/rejects the task. Far richer than LC4j's `@HumanInTheLoop`.

#### `@RequiresQuorum`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface RequiresQuorum {
    int instances();
    int required();
    String[] candidateGroups() default {};
    OnThresholdReached onThresholdReached() default OnThresholdReached.CANCEL_REMAINING;
    boolean allowSameAssignee() default false;
}
```

Multi-instance approval pattern — spawns N WorkItems, proceeds when M complete.

#### `@Escalate`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Escalate {
    String onExpiry() default "";          // target group
    String onClaimDeadline() default "";   // target group
    String deadline() default "";          // ISO-8601 duration
    boolean generateSummary() default true; // LLM-drafted escalation briefing
}
```

Composes with `@HumanApproval` — defines escalation policy when deadlines pass.

#### Example: governed agent with human approval gate

```java
public interface TradeExecutionAgent {

    @SupervisorAgent(subAgents = { MarketAnalyst.class, RiskAssessor.class })
    @OversightGate(TradeRiskClassifier.class)
    @HumanApproval(title = "Trade execution approval",
                   candidateGroups = "senior-traders",
                   priority = WorkItemPriority.HIGH,
                   claimDeadline = "PT30M",
                   expiresAt = "PT2H")
    @Escalate(onExpiry = "trading-desk-manager",
              deadline = "PT4H",
              generateSummary = true)
    TradeDecision execute(@V("trade") TradeRequest trade);
}
```

This declares: the supervisor coordinates analysis, the oversight gate classifies risk, if `GateRequired` the system creates a WorkItem for senior traders with a 30-minute claim deadline, and if unclaimed for 2 hours it escalates to the trading desk manager with an LLM-drafted briefing.

### Composable governance annotations (meta-annotations)

These compose onto ANY agent annotation — LC4j or CaseHub. They are the progressive-disclosure power layer.

#### `@OversightGate`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface OversightGate {
    Class<? extends ActionRiskClassifier> value();
}
```

Wires the agent's execution through the `ChainedActionRiskClassifier` (`io.casehub.api.spi`). If the classifier returns `GateRequired`, execution pauses for human approval via `OversightGateService`.

**Deployment requirement:** `OversightGateService` is an engine-api SPI with a `NoOpOversightGateService` `@DefaultBean` in `casehub-engine` that returns `GateOutcome.Autonomous()` for all requests (logging a startup warning). The `casehub-work` module provides the production implementation that creates `WorkItem`s and suspends execution. The build extension emits a **compile-time warning** (not error) when `@OversightGate` is used and no non-`@DefaultBean` `OversightGateService` implementation is detected on the classpath — this allows development without the full stack while making the governance gap visible.

#### `@TrustRouted`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface TrustRouted {
    double minimumScore() default 0.0;
    int minimumObservations() default 0;
    double borderlineMargin() default 0.1;
}
```

Wires trust-weighted routing via `TrustRoutingPolicyResolver`. Agents below `minimumScore` are excluded from candidate selection.

#### `@CbrRouted`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface CbrRouted {
    Class<? extends CbrOutcomeWeights> weights() default DefaultCbrOutcomeWeights.class;
}
```

Wires CBR evidence routing via `CbrAgentRoutingStrategy`. Historical case outcomes influence agent selection.

#### `@Attestation`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Attestation {
    Class<? extends LifecycleAttestationObserver> observer();
    String capabilityTag() default "";
}
```

Wires attestation lifecycle observation. Agent execution results are recorded as `AttestationIntent` via `AttestationIntentWriter`.

#### `@OnFailure`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface OnFailure {
    RoutingFailureAction onRoutingFailure() default RoutingFailureAction.FAIL;
    AggregationFailureAction onDeadlock() default AggregationFailureAction.FAIL;
    int maxRetries() default 3;
    String retryBackoff() default "PT1S";     // ISO-8601 duration
    BackoffStrategy backoffStrategy() default BackoffStrategy.FIXED;
    AgentFailureAction onRetryExhausted() default AgentFailureAction.FAIL;
    int maxReplans() default 0;               // 0 = no replanning
    RoutingFailureAction replanFallback() default RoutingFailureAction.FAIL;
}
```

Composable failure policy for any orchestration annotation. Maps directly to `FailurePolicy` record fields. Without `@OnFailure`, `FailurePolicy.defaults()` applies (3 retries, fixed 1s backoff, FAIL on exhaustion).

**Type mapping:** `RoutingFailureAction` (`FAIL`, `RETRY_BROADER`, `ESCALATE`), `AggregationFailureAction` (`FAIL`, `ESCALATE`, `RETRY_DIFFERENT`), `BackoffStrategy` (`FIXED`, `EXPONENTIAL`, `EXPONENTIAL_WITH_JITTER`), `AgentFailureAction` (`FAIL`, `ESCALATE`, `SKIP`) — all existing enums nested in `io.casehub.blocks.agentic.FailurePolicy`.

**Build extension mapping to `FailurePolicy`:** The build extension applies conditional logic when constructing `FailurePolicy` from `@OnFailure`:
- `maxReplans == 0` → `replanPolicy = null` (no replanning — uses `FailurePolicy` 3-arg constructor)
- `maxReplans >= 1` → `replanPolicy = new ReplanPolicy(maxReplans, replanFallback)` (uses 4-arg constructor)

This is necessary because `ReplanPolicy`'s compact constructor validates `maxReplans >= 1` — the annotation's `maxReplans = 0` default means "no replanning", not "create a ReplanPolicy with 0 replans".

```java
@DebateAgent(maxRounds = 5)
@OnFailure(backoffStrategy = BackoffStrategy.EXPONENTIAL_WITH_JITTER,
           onRetryExhausted = AgentFailureAction.SKIP,
           maxReplans = 2)
String review(@Debater(role = "critic") AgentRef critic, ...);
```

#### `@Decompose`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Decompose {
    Class<? extends io.casehub.engine.plan.DecompositionStrategy> value();
    int maxDepth() default 1;
}
```

Overrides the default decomposition strategy for any orchestration annotation.

### Dynamic composition annotations

**Module:** `casehub-eidos-api` (`@Discoverable` — eidos owns agent discovery as an extension of agent identity)
**Module:** `casehub-blocks` (api) (`@DiscoverFrom`, `@Route` — blocks owns orchestration composition)

#### `@Discoverable`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface Discoverable {
    String[] capabilities();
    String[] tags() default {};
}
```

Marks an agent interface for auto-registration into eidos `AgentRegistry` at startup. The build extension generates an `AgentDescriptor` from `@Identity` + `@Disposition` + `@Discoverable` capabilities and registers it.

#### `@DiscoverFrom`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface DiscoverFrom {
    Class<?> value(); // registry class (e.g., AgentRegistry.class)
}
```

Tells the build extension to inject registry-based agent discovery instead of static `subAgents`. At runtime, the registry provides all `@Discoverable`-registered agents matching the method's capability requirements.

#### `@Route`
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Route {
    Class<?> strategy(); // routing strategy class (e.g., LlmSelectedRouting.class)
}
```

Selects the routing strategy for dynamically discovered agents. Composes with `@TrustRouted`, `@CbrRouted` — the routing strategy runs first, then trust/CBR filters apply.

### Example: governed debate

```java
public interface ComplianceReview {

    @DebateAgent(maxRounds = 5)
    @OversightGate(ComplianceRiskClassifier.class)
    @TrustRouted(minimumScore = 0.8)
    @Attestation(observer = ComplianceObserver.class)
    @OnFailure(backoffStrategy = BackoffStrategy.EXPONENTIAL_WITH_JITTER,
               onRetryExhausted = AgentFailureAction.SKIP)
    String review(
        @Debater(role = "critic",
                 systemPrompt = "Challenge every compliance claim...")
        AgentRef critic,

        @Debater(role = "advocate",
                 systemPrompt = "Defend the document's compliance...")
        AgentRef advocate,

        @Judge AgentRef judge,

        @V("document") String document);

    @ChatModelSupplier
    static ChatModel model() {
        return AnthropicChatModel.builder().modelName("claude-opus-5").build();
    }
}
```

This is ~25 lines for a governed multi-agent debate with oversight, trust routing, attestation, failure policy, and judge-driven convergence. The `@Judge` parameter creates `JudgeConvergence` termination automatically — no explicit `@Convergence` needed. The builder equivalent is 60+ lines.

**`AgentRef` parameter wiring:** The build extension resolves `@Debater`/`@Judge`-annotated `AgentRef` parameters into agent instances at build time. For each annotated parameter, the extension generates an `AgentRef.WorkerAgent` wrapping an LC4j-generated AI service configured with the annotation's `systemPrompt` and the interface's `@ChatModelSupplier`. The `role` attribute maps to the agent's identity within the debate pattern. CDI injection provides the resolved `AgentRef` instances at runtime. `AgentRef` is a sealed interface (`io.casehub.blocks.agentic.AgentRef`) with 5 variants: `WorkerAgent`, `ChannelAgent`, `HumanAgent`, `ExternalAgent`, `ComposedAgent`.

### Example: composing governance onto LC4j annotations

```java
public interface GovernedSupervisor {

    @SupervisorAgent(subAgents = { Analyst.class, Writer.class, Reviewer.class },
                     maxAgentsInvocations = 10)
    @OversightGate(FinancialRiskClassifier.class)
    @TrustRouted(minimumScore = 0.7)
    @CbrRouted
    String process(@V("request") String request);
}
```

The LC4j `@SupervisorAgent` defines the orchestration pattern. CaseHub's `@OversightGate`, `@TrustRouted`, `@CbrRouted` layer governance on top — the build extension wraps the LC4j-generated bean with interceptors.

---

## Build Extension Architecture

### Extension composition model

```
                    ┌────────────────────────────────┐
                    │     Application Code           │
                    │  (@Case + @SupervisorAgent +   │
                    │   @OversightGate + @Worker)     │
                    └──────────┬─────────────────────┘
                               │ annotations
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
    ┌─────────────────┐ ┌───────────┐ ┌──────────────────┐
    │ LC4j Quarkus    │ │  Engine   │ │  Blocks          │
    │ Extension       │ │  Build    │ │  Build           │
    │ (exists)        │ │  Ext      │ │  Extension       │
    │                 │ │  (new)    │ │  (new)           │
    │ Processes:      │ │           │ │                  │
    │ @*Agent         │ │ @Case     │ │ @DebateAgent     │
    │ @Tool           │ │ @Worker   │ │ @VotingAgent     │
    │ @SystemMessage  │ │ @Bind     │ │ @HtnAgent        │
    │ @*Supplier      │ │ @Goal     │ │ @OversightGate   │
    │                 │ │ @Milestone│ │ @TrustRouted     │
    │                 │ │           │ │ @CbrRouted       │
    │                 │ │           │ │ @Attestation     │
    └────────┬────────┘ └─────┬─────┘ └────────┬─────────┘
             │                │                │
             ▼                ▼                ▼
    ┌─────────────────────────────────────────────────────┐
    │              CDI Bean Registry                      │
    │                                                     │
    │  Agent beans (from LC4j)                           │
    │  CaseDefinition beans (from engine ext)            │
    │  ExecutionModel beans (from blocks ext)            │
    │  Interceptor wiring (from blocks ext)              │
    │                                                     │
    │  All same types as builder-produced instances       │
    └─────────────────────────────────────────────────────┘
```

### Engine build extension responsibilities

1. **Scan** for `@Case`-annotated interfaces at build time
2. **Validate** — every `@Bind` references a declared `@Worker` or `@Capability`; every `@Goal` has a completion expression; trigger expressions are syntactically valid
3. **Generate** synthetic CDI beans:
   - `CaseDefinition` per `@Case` interface
   - `Worker` per `@Worker` method
   - `Capability` per `@Capability` annotation
   - `Binding` per `@Bind` method
4. **Compose** with LC4j — if a `@Worker` method also has `@SystemMessage`/`@UserMessage`, the generated worker delegates to the LC4j-generated AI service

### Blocks build extension responsibilities

1. **Scan** for `@DebateAgent`, `@VotingAgent`, `@HtnAgent` at build time
2. **Scan** for governance meta-annotations (`@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation`) on any `@*Agent`-annotated or `@Worker`-annotated method
3. **Scan** for eidos annotations (`@Identity`, `@Disposition`, `@AgentGoals`, `@AgentConstraints` — defined in `casehub-eidos-api`) and work annotations (`@HumanApproval`, `@RequiresQuorum`, `@Escalate` — defined in `casehub-work-api`) to generate cross-module wiring
4. **Validate** — referenced classifier/observer/weights classes exist; trust parameters are in valid ranges
5. **Generate** synthetic CDI beans:
   - `ExecutionModel` per CaseHub orchestration annotation (via `Patterns.*()` builders)
   - `AgentDescriptor` per `@Identity`-annotated interface (registered with `AgentRegistry`)
   - `WorkItem` configuration per `@HumanApproval`-annotated method (wired to `HumanTaskFlowBridge`)
   - Interceptor bindings for governance annotations
6. **Compose** with LC4j — governance annotations on LC4j `@SupervisorAgent` etc. wrap the LC4j-generated bean with blocks interceptors

**Module ownership vs processing:** Annotations live in their owning module's API (Design Principle 2). The blocks build extension in `casehub-blocks-engine-adapter` processes annotations from multiple modules because it handles cross-cutting composition. This follows the same pattern as LC4j's Quarkus extension: annotations in `langchain4j-agentic`, processing in `quarkus-langchain4j`.

### `ExecutionModel<T>` type parameter inference

`ExecutionModel<T>` is parameterized — all five SPIs share the type parameter `T` (the context type threading through the execution pipeline). Annotations cannot express generic type parameters directly. The build extension infers `T` using these rules:

| Rule | Condition | `T` resolves to |
|---|---|---|
| **Return type** | Orchestration method has a non-`void`, non-`String` return type | The return type (e.g., `ReviewResult review(...)` → `T = ReviewResult`) |
| **String return** | Return type is `String` | `String` — valid for text-in/text-out patterns |
| **Void return** | Return type is `void` | `Void` — side-effect-only orchestrations (rare but valid) |
| **Explicit override** | `@Customize` method accepting a pattern builder (e.g., `DebateBuilder<T>`) | Customiser receives the builder with inferred `T`, can narrow or reconfigure |

The return type rule covers the vast majority of cases — the orchestration method's return type IS the pipeline's output type, which is exactly what `T` parameterizes. This matches the fluent builder pattern where `T` is specified at the entry point: `Patterns.<ReviewResult>debate()...`.

For cases where `T` must differ from the return type (e.g., internal pipeline context differs from the external return type), `@Customize` provides the escape hatch — same principle as `@Customize` for `CaseDefinition.Builder`.

### Fallback composition architecture (without upstream hooks)

The Upstream Candidates table lists build extension hooks as HIGH priority. If LC4j does not accept the extension hooks PR, CaseHub's build extension uses Quarkus's existing `BeanDiscoveryFinishedBuildItem` to observe LC4j's generated beans without modifying them:

**Phase 1 — Bean observation:** The blocks build extension runs AFTER the LC4j extension (Quarkus build step ordering via `@Consume(BeanDiscoveryFinishedBuildItem.class)`). It reads the generated bean metadata from Quarkus's `BeanInfo` registry — class names, qualifiers, and injection points are stable Quarkus API, not LC4j internals.

**Phase 2 — Interceptor wrapping:** Governance annotations (`@OversightGate`, `@TrustRouted`) generate CDI interceptor bindings that wrap the LC4j-generated bean. Interceptors compose via standard CDI — no LC4j-specific hooks needed. The interceptor observes the bean's method invocation and applies the governance logic around it.

**Phase 3 — Bean replacement (if needed):** For orchestration annotations that need to REPLACE LC4j's generated logic (e.g., `@DiscoverFrom` overriding static `subAgents`), the blocks extension uses Quarkus's `BeanDefiningAnnotationBuildItem` + `AdditionalBeanBuildItem` to register an alternative bean with higher CDI priority (`@Alternative @Priority`).

**Stability guarantee:** This architecture depends on Quarkus CDI build API (`BeanInfo`, `BeanDiscoveryFinishedBuildItem`, `AdditionalBeanBuildItem`), NOT on LC4j internals. Quarkus's build API is versioned and stable. LC4j extension updates that change generated bean names or types do not break CaseHub's interceptor wiring — only the bean's CDI qualifiers matter, and those are derived from the user's source annotations which CaseHub controls.

**Residual risk:** If LC4j's extension changes how it maps annotations to CDI qualifiers (e.g., changing the qualifier annotation class), CaseHub's interceptor binding could break. This is a manageable risk — the blocks build extension's integration tests (§Testing Strategy) validate three-extension composition, catching such breakage at build time. The upstream hooks PR eliminates this risk entirely if accepted.

### Build-time validation examples

| Check | Error message |
|---|---|
| `@Bind` references unknown capability | `@Bind on method 'afterAnalysis' references capability 'analyse' which is not declared on this @Case` |
| `@Bind` with no trigger set | `@Bind on method 'onReceived' has no trigger — set exactly one of contextChange, event, cron, or scopeActivated` |
| `@Bind` with multiple triggers | `@Bind on method 'onReceived' has multiple triggers (contextChange, cron) — set exactly one per @Bind` |
| `@Bind` producedKeys conflict | `@Bind methods 'afterAnalysis' and 'afterClauses' both declare producedKey 'status' — add conflictStrategy or remove overlap` |
| `@OversightGate` classifier not found | `@OversightGate references ComplianceRiskClassifier which is not a CDI bean` |
| `@TrustRouted` with invalid score | `@TrustRouted minimumScore 1.5 is outside valid range [0.0, 1.0]` |
| `@DebateAgent` without `@Debater` params | `@DebateAgent on 'review' has no @Debater-annotated parameters` |
| `@DebateAgent` with `@Judge` + `@Convergence` on same method | `@DebateAgent on 'review' has both @Judge parameter and @Convergence annotation — these are mutually exclusive (judge creates JudgeConvergence)` |
| `@Convergence` threshold out of range | `@Convergence threshold 2.0 is outside valid range [0.0, 1.0]` |
| `@Convergence` mismatched strategy params | `@Convergence on 'review' sets staleRounds=5 with COMMON_GROUND_RATIO strategy — staleRounds only applies to STRUCTURAL` |
| `@Worker` with both capability and capabilities | `@Worker on 'analyse' sets both capability and capabilities — use one or the other` |
| GOAP duplicate return type | `Workers 'extractTags' and 'extractErrors' both return List<String> — ambiguous GOAP effect. Add @Effect("name") to disambiguate` |

---

## Upstream Candidates for LangChain4j

Changes to contribute upstream that benefit both CaseHub and the broader ecosystem:

### High priority

| Change | What | Why |
|---|---|---|
| Build extension hooks | Extension point interface in Quarkus LC4j for downstream extensions to compose | Without this, CaseHub's extension must reverse-engineer LC4j's generated beans |
| Meta-annotation support | Framework recognises composable annotations on `@*Agent` methods | `@OversightGate` composable on any agent — better solved once in LC4j's processor |
| `@DebateAgent` | Multi-agent debate pattern with convergence | General-purpose — not CaseHub-specific |
| `@VotingAgent` | Multi-agent voting with pluggable aggregation | General-purpose — MajorityVote etc. |

### Medium priority

| Change | What | Why |
|---|---|---|
| Cost/value on agents | `@Agent(cost=N)` or `@Cost` dynamic method | Cost-aware planning is universally useful |
| Richer `Planner` interface | Support HTN/GOAP/heuristic via `@PlannerSupplier` | Current interface is thin — needs decomposition strategy support |
| `@ChannelAgent` | Channel-based inter-agent communication | As A2A ecosystem grows, structured channels > blackboard |

---

## Phased Delivery

### Phase 1: LC4j adoption + engine core annotations

**Scope:** Adopt LC4j agentic annotations. Implement `@Case`, `@Worker`, `@Capability`, `@Bind`, `@Goal`, `@Milestone`, `@Completion` in engine-api with build extension.

**Deliverables:**
- Annotations in `casehub-engine-api`
- Quarkus build extension in `casehub-engine`
- Build-time validation for case definitions
- 3 example cases (simple, multi-worker, with milestones)

**Exit criteria:** An annotated `@Case` interface compiles to the same `CaseDefinition` as the equivalent builder code. Existing builder-defined cases continue to work unchanged.

### Phase 2: Blocks core + governance + cross-module annotations

**Scope:** Implement `@DebateAgent`, `@VotingAgent`, `@HtnAgent` plus role annotations. Implement `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation` as composable meta-annotations. Implement eidos identity annotations (`@Identity`, `@Disposition`, `@AgentGoals`, `@AgentConstraints`) in `casehub-eidos-api` and work annotations (`@HumanApproval`, `@RequiresQuorum`, `@Escalate`) in `casehub-work-api`.

**Deliverables:**
- Blocks annotations in `casehub-blocks` (api package)
- Eidos annotations in `casehub-eidos-api`
- Work annotations in `casehub-work-api`
- Quarkus build extension in `casehub-blocks-engine-adapter` (processes all three)
- Build-time validation for governance annotations
- 3 examples (simple debate, governed supervisor, CBR-routed HTN)

**Exit criteria:** Annotated agents produce the same `ExecutionModel` as `Patterns.*()` builders. Governance annotations compose onto both LC4j and CaseHub orchestration annotations.

### Phase 3: Upstream contributions + refinement

**Scope:** Contribute `@DebateAgent`, `@VotingAgent`, build extension hooks, and meta-annotation support to LangChain4j. Refine CaseHub annotations based on Phase 1-2 learnings.

**Deliverables:**
- LC4j PRs for upstream candidates
- Migration of CaseHub `@DebateAgent`/`@VotingAgent` to LC4j annotations if accepted
- Documentation and tutorials

**Upstream migration path:** When LC4j accepts an upstream candidate (e.g., `@DebateAgent`), the migration follows three stages:

1. **Deprecate** — CaseHub's `io.casehub.blocks.agentic.DebateAgent` is marked `@Deprecated` with a `forRemoval = true` and `since` pointing to the LC4j version that includes the upstream equivalent. The build extension emits a compile-time warning for all usages.

2. **Bridge** — CaseHub's build extension recognises BOTH the CaseHub annotation and the LC4j annotation for one release cycle. CaseHub-specific attributes that LC4j does not adopt (e.g., `@Convergence` if LC4j ships a simpler convergence model) become composable annotations that work alongside the LC4j annotation:
   ```java
   // Before: CaseHub-only
   @io.casehub.blocks.agentic.DebateAgent(maxRounds = 5)
   @io.casehub.blocks.conversation.Convergence(strategy = ConvergenceStrategy.STRUCTURAL, threshold = 0.85)

   // After: LC4j base + CaseHub extension
   @dev.langchain4j.agentic.DebateAgent(maxRounds = 5)
   @Convergence(strategy = ConvergenceStrategy.STRUCTURAL, threshold = 0.85)
   ```

3. **Remove** — CaseHub's `@DebateAgent` is deleted. Only the LC4j annotation + CaseHub's composable extensions remain. The blocks build extension processes the LC4j annotation via the composition architecture (upstream hooks or fallback, per §Fallback composition architecture).

---

## Examples Strategy

### Principle: teach one thing at a time, then combine

Each repo's `examples/` folder teaches the concepts of **that module only** — narrow, focused, understandable in isolation. Cross-module composition lives in `casehub-examples`, where use cases demonstrate how modules combine.

### Per-repo examples — narrow and focused

Each repo that gains annotations should have annotation-driven examples alongside its existing YAML and builder examples. The three programming models are differentiated by naming convention:

| Suffix | Model | Example |
|---|---|---|
| `-annotated` | Annotations | `document-review-annotated` |
| `-yaml` | YAML configuration | `document-review-yaml` |
| `-builder` | Fluent Java DSL | `document-review-builder` |
| (no suffix) | Existing examples (pre-annotation model) | `document-review` — leave as-is, retrofit suffix later |

Where it's valuable, the same use case implemented in all three models makes the equivalence concrete. Engineers see the same case definition as annotations, YAML, and builders — and understand they're interchangeable.

### Per-repo example scope

**Note:** Annotations marked "(future — not in this spec)" in the table below are directional — they illustrate where the annotation model could expand. Each module's annotation support will be specified separately. GitHub issues will be filed for each proposed module extension before this spec is finalised.

| Repo | `examples/` teaches | Annotation examples |
|---|---|---|
| **qhorus** | Channels, speech acts, projections, watchdog | `channel-messaging-annotated` — `@Channel`, `@Semantic`, `@Observer` (future) |
| **eidos** | Agent identity, disposition, goals | `agent-identity-annotated` — `@Identity`, `@Disposition`, `@AgentGoals` |
| **engine** | Cases, workers, bindings, milestones | `simple-case-annotated` — `@Case` + `@Worker` hello world. `multi-worker-annotated` — `@Bind` triggers + milestones. `goap-case-annotated` — `planning = PlanningMode.GOAP` with inferred dependencies. |
| **work** | Human tasks, approval, quorum | `approval-gate-annotated` — `@HumanApproval`. `quorum-review-annotated` — `@RequiresQuorum` + `@Escalate`. |
| **blocks** | Agentic patterns, governance | `debate-annotated` — `@DebateAgent` with convergence. `governed-supervisor-annotated` — `@OversightGate` + `@TrustRouted` on `@SupervisorAgent`. |
| **desiredstate** | Reconciliation, fault policies | `pipeline-annotated` — `@DesiredState` + `@NodeSpec` + `@FaultPolicy` (future) |
| **ledger** | Audit, compliance | `audited-worker-annotated` — `@Audited` + `@ComplianceSupplement` (future) |

Each example should be runnable standalone with `mvn quarkus:dev`. The simplest annotation example at each layer should be under 30 lines — demonstrating that CaseHub's entry cost matches embabel's.

### casehub-examples — cross-module composition

`casehub-examples` is where modules combine to solve real use cases. These examples import multiple CaseHub modules and demonstrate the full annotation stack. Same naming convention applies — annotation-driven examples use the `-annotated` suffix.

| Example | Modules combined | What it teaches |
|---|---|---|
| `document-review-annotated` | engine + blocks + eidos + work | Annotated case with LLM workers, agent identity, oversight gate, human approval on high-risk findings |
| `trade-execution-annotated` | engine + blocks + eidos + work + ledger | Governed supervisor, trust-routed agents, quorum approval, compliance-audited, attestation lifecycle |
| `incident-response-annotated` | engine + desiredstate + ras + work | Desired-state reconciliation, RAS pattern detection triggering case creation, auto-remediation with human escalation |
| `multi-agent-debate-annotated` | blocks + eidos + qhorus + ledger | Debate over qhorus channels, disposition-matched debaters, convergence detection, audited outcomes |
| `open-composition-annotated` | blocks + eidos | `@Discoverable` agents, registry-based dynamic composition, trust + CBR routing |

Each composition example should include a brief header comment explaining which modules are involved and what the example demonstrates — so engineers landing on one example understand the full picture without reading every module's docs first.

### What stays YAML/builder-only in examples

Some modules are YAML-first or config-first by design. Their examples don't need annotation variants:

- **workers** — MCP/HTTP/K8s/Script endpoints configured via `application.properties`
- **connectors** — Connector endpoints configured via `application.properties`
- **RAS** — Situation definitions and ganglia declared in YAML (this is intentional — ops teams configure detection rules, not developers)

These modules may gain annotation support later if demand warrants it, but the examples should not be forced into annotations just for consistency.

## Closing the Remaining Gaps

Three areas where embabel currently leads. All closable by wiring existing infrastructure to the annotation model.

### Gap 1: Auto-Inference (GOAP-connected annotations)

embabel auto-infers preconditions from method signatures and lets GOAP discover execution order. CaseHub has `GoapPlanner` in engine-api but it's not connected to annotations.

**Closing move:** the engine build extension infers dependencies from `@Worker` parameter types and generates `GoapAction` preconditions/effects.

```java
// Planner-inferred execution order — no @Bind triggers needed
@Case(namespace = "legal", name = "Review", planning = PlanningMode.GOAP)
public interface DocumentReview {

    @Worker(capability = "analyse", cost = 2)
    AnalysisResult analyse(@V("document") String document);

    @Worker(capability = "extractClauses", cost = 3)
    List<Clause> extract(@V("document") String document,
                         AnalysisResult analysis);  // inferred: analyse must complete first

    @Worker(capability = "assessRisk", cost = 5)
    @OversightGate(LegalRiskClassifier.class)
    RiskAssessment assess(AnalysisResult analysis,
                          List<Clause> clauses);    // inferred: both must complete first

    @Goal("Document reviewed")
    @Completion
    GoalExpression done() { return GoalExpression.goal("riskAssessed"); }
}
```

**How it works:**
1. Build extension scans `@Worker` method parameter types via Jandex at build time
2. If a parameter type matches another `@Worker`'s return type on the same `@Case`, infer a dependency
3. Generate `GoapAction` with preconditions (parameter types present in world state) and effects (return type added to world state)
4. `GoapPlanner` discovers the cheapest execution path at runtime
5. Cost via `@Worker(cost=N)` feeds planner optimisation

**Type-to-boolean mapping rules:**

`GoapAction` uses `Map<String, Boolean>` for preconditions and effects. The build extension translates Java types into boolean world-state flags as follows:

| Rule | Example | Flag name |
|---|---|---|
| **Naming convention** | `AnalysisResult` return type | `"analysisResultAvailable"` — simple class name, camelCase, + `"Available"` suffix |
| **Parameterized types** | `List<Clause>` return type | `"listOfClauseAvailable"` — `"listOf"` + element simple name + `"Available"`. Jandex preserves `ParameterizedType` — no erasure. |
| **@V parameters excluded** | `@V("document") String document` | Not a dependency — `@V` marks template variable injection, not a type dependency. Only non-`@V` typed parameters generate preconditions. |
| **Subtype matching** | `DetailedAnalysis extends AnalysisResult` returned → worker B takes `AnalysisResult` | Producer generates effects for both `"detailedAnalysisAvailable"` and `"analysisResultAvailable"`. The build extension walks the Jandex type hierarchy. |
| **Ambiguous producers** | Two workers return `List<String>` | Build-time error: `"Workers 'extractTags' and 'extractErrors' both return List<String> — ambiguous GOAP effect. Add @Effect("name") to disambiguate"` |
| **@Effect override** | `@Effect("documentTags") List<String> extractTags(...)` | Explicit flag name `"documentTags"` overrides the inferred name. Use when type-based inference is ambiguous or the inferred name is unclear. |

**`@Effect` annotation:**
```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface Effect {
    String value();  // explicit GOAP world-state flag name for this worker's output
}
```

**Progressive disclosure:** `@Bind` triggers remain available for explicit control. `planning = PlanningMode.GOAP` opts into auto-inference. Default behaviour (`PlanningMode.EXPLICIT`) requires explicit bindings. Power users can mix: auto-inferred base with explicit `@Bind` overrides for specific edges.

**Upstream candidate:** push `cost()` and `value()` attributes to LC4j's `@Agent` annotation.

### Gap 2: Dynamic Composition ("Open Mode")

embabel's platform dynamically composes across all deployed agents without explicit wiring. CaseHub requires explicit `Patterns.*()` composition.

**Closing move:** `@Discoverable` annotation + `AgentRegistry` bridge.

```java
@Discoverable(capabilities = {"document-analysis", "clause-extraction"})
@Identity(slot = "legal-analyst",
          jurisdiction = "EU")
@Disposition(riskAppetite = "cautious",
             ruleFollowing = "strict")
public interface LegalAnalyst {

    @Agent(description = "Analyse legal documents")
    @SystemMessage("You are a senior legal analyst...")
    String analyse(@V("document") String document);
}
```

(`@Discoverable`, `@DiscoverFrom`, and `@Route` are formally defined in the Layer 2 annotations section above.)

**How it works:**
1. Build extension scans `@Discoverable` interfaces
2. Generates `AgentDescriptor` from `@Identity` + `@Disposition` + `@Discoverable` capabilities
3. Auto-registers into eidos `AgentRegistry` at startup
4. A meta-supervisor queries the registry at runtime:

```java
public interface OpenSupervisor {

    @RegistryAgent(name = "open-supervisor")
    @DiscoverFrom(AgentRegistry.class)
    @Route(strategy = LlmSelectedRouting.class)
    String process(@V("request") String request);
}
```

**Why `@RegistryAgent` instead of `@SupervisorAgent(subAgents = {})`:** LC4j's `@SupervisorAgent` requires non-empty `subAgents` — an empty array is semantically nonsensical for a supervisor and may fail LC4j's build-time validation. LC4j provides `@RegistryAgent` for exactly this pattern — loading agents from a registry by name. `@DiscoverFrom` extends `@RegistryAgent`'s semantics: instead of loading a single named agent, it discovers all matching agents from the eidos `AgentRegistry`. The blocks build extension generates a custom `AgentRegistry` bridge that filters by `@Discoverable` capabilities and routes via the specified `@Route` strategy.

**What CaseHub adds over embabel's open mode:**
- Trust-weighted agent selection (`@TrustRouted`)
- Disposition matching (`DispositionAwareRouting`)
- CBR evidence from past outcomes (`@CbrRouted`)
- Oversight gates on dynamically composed agents (`@OversightGate`)
- Behavioural tracking via eidos

embabel's open mode discovers and composes. CaseHub's discovers, evaluates trust, matches personality, checks evidence, and gates oversight — then composes.

### Gap 3: Cost/Value Optimisation

embabel has `@Action(cost=N)` and `@Cost` dynamic methods for planner optimisation.

**Closing move:** `cost()` and `value()` are now attributes on `@Worker` (see Layer 1 definition). For dynamic cost/value, use supplier methods:

```java
@Retention(RUNTIME)
@Target(METHOD)
public @interface WorkerCost {
    // Dynamic cost supplier — method must be static, return int
}

@Retention(RUNTIME)
@Target(METHOD)
public @interface WorkerValue {
    // Dynamic value supplier — method must be static, return int
}
```

**Precedence:** `@WorkerCost`/`@WorkerValue` dynamic suppliers take precedence over static `@Worker(cost=N, value=N)` attributes. Static attributes are the 80% case; dynamic suppliers handle context-dependent costs.

```java
@Worker(capability = "analyse", cost = 2, value = 10)
AnalysisResult analyse(@V("document") String document);

@Worker(capability = "assessRisk", cost = 5, value = 20)
@WorkerCost  // dynamic cost supplier method below
RiskAssessment assess(AnalysisResult analysis, List<Clause> clauses);

@WorkerCost
static int assessRiskCost(@V("document") String document) {
    return document.length() > 10000 ? 10 : 5;  // longer docs cost more
}
```

Cost and value feed into `GoapPlanner` (path optimisation), `HeuristicDecomposition` (method ranking), and `StructuralCostHeuristic` (tree-walking cost estimation).

**Upstream candidate:** push `cost()`/`value()` to LC4j's `@Agent` annotation.

## Three Programming Models — Annotations, Fluent Builders, YAML

The annotation model is one of three idiomatic ways to define the same things. All three produce the same underlying types and compose freely.

### The principle

Every declarable concept in CaseHub should be expressible in three ways:

| Model | Audience | Strength | When to use |
|---|---|---|---|
| **Annotations** | App developers, rapid prototyping | Visual structure, IDE support, build-time validation | Declaring agents, cases, workers, governance — the 80% case |
| **Fluent builders** | Platform developers, dynamic composition | Full programmatic control, runtime flexibility | Dynamic routing, conditional composition, computed configuration |
| **YAML** | Ops, non-developers, GitOps pipelines | No recompilation, version-controlled config, environment-specific | Deployment topology, capability routing, situation definitions |

### Equivalence example — a worker definition in all three

**Annotations:**
```java
@Case(namespace = "legal", name = "Review")
public interface DocumentReview {
    @Worker(capability = "analyse", cost = 2)
    @SystemMessage("You are a document analyst...")
    AnalysisResult analyse(@V("document") String document);
}
```

**Fluent builder:**
```java
var worker = Worker.builder()
    .name("document-analyst")
    .capabilityName("analyse")
    .<String>fn().returning(AnalysisResult.class)
    .apply((input, scope) -> { ... })
    .description("Analyse documents")
    .build();

var definition = CaseDefinition.builder()
    .namespace("legal").name("Review")
    .workers(worker)
    .build();
```

**YAML:**
```yaml
namespace: legal
name: Review
workers:
  - name: document-analyst
    capability: analyse
    cost: 2
    type: mcp
    endpoint: ${ANALYST_MCP_URL}
```

All three produce the same `CaseDefinition` and `Worker` types. A YAML-defined worker can be referenced from an annotation-defined case. A builder-defined agent can appear in `@SupervisorAgent(subAgents = {...})`. No conversion layer needed — the build extension, builder, and YAML parser all target the same records.

### Where each model already exists

| Module | Annotations (proposed) | Fluent builders (exists) | YAML (exists) |
|---|---|---|---|
| **engine** | `@Case`, `@Worker`, `@Bind`, `@Goal`, `@Milestone` | `CaseDefinition.builder()`, `Worker.builder()`, `Binding.builder()`, `Capability.builder()` | `CaseDefinitionYamlMapper` — full YAML case definitions |
| **blocks** | `@DebateAgent`, `@VotingAgent`, `@HtnAgent`, `@OversightGate`, `@TrustRouted` | `Patterns.supervisor()`, `.debate()`, `.voting()`, `.sequence()`, `.parallel()`, `.loop()`, `.conditional()`, `.htn()` | — (intentional: patterns are code-composed, not config-driven — two-of-three is acceptable here) |
| **eidos** | `@Identity`, `@Disposition`, `@AgentGoals`, `@AgentConstraints` | `AgentDescriptor.builder()`, `AgentDisposition.builder()` | Agent descriptors registered via YAML + `AgentDescriptorRegistrar` SPI |
| **work** | `@HumanApproval`, `@RequiresQuorum`, `@Escalate` | `WorkItemCreateRequest.builder()`, `MultiInstanceConfig.builder()` | WorkItem templates as YAML definitions |
| **qhorus** | `@Channel`, `@Semantic`, `@Observer` (future — not in this spec) | Channel creation via `ChannelStore` API | Channel config via YAML deployment descriptors |
| **desiredstate** | `@DesiredState`, `@NodeSpec`, `@DependsOn`, `@FaultPolicy` (future — not in this spec) | `DesiredStateGraph` functional builders, `ThresholdFaultPolicy.builder()` | Goal declarations as YAML with `GoalCompiler` |
| **RAS** | — (YAML-first by design) | Programmatic `SituationDefinition` construction | YAML situation definitions with NaiveBayes/ExpressionRules ganglia |
| **workers** | — (config-first by design) | `Worker.builder()` with typed function builders | MCP/HTTP/K8s/Script endpoints via `application.properties` |
| **connectors** | — (config-first by design) | Programmatic `Connector` registration | Connector endpoints via `application.properties` |
| **ledger** | `@Audited`, `@ComplianceSupplement` (future — not in this spec) | `LedgerEntry` construction via `LedgerAppender` | Compliance config via properties |
| **ops** | `@ServiceLifecycle`, `@OperationalDimension` (future — not in this spec) | `ServiceCaseDescriptor` programmatic construction | Deployment topology as YAML |

### Design constraint

The annotation model must never be the ONLY way to do something. Every `@Annotation` has a builder equivalent. YAML coverage follows demand — configuration-heavy modules (workers, connectors, RAS) are YAML-first; code-heavy modules (blocks patterns) are builder-first; the annotation model adds a third option where it improves DX.

### Interop guarantee

Any combination of the three models in the same application must work:

```java
// Annotation-defined case with YAML-defined workers
@Case(namespace = "legal", name = "Review", planning = PlanningMode.GOAP)
public interface DocumentReview {

    @Worker(capability = "analyse")  // annotation-defined
    AnalysisResult analyse(@V("document") String document);

    // "extractClauses" capability satisfied by YAML-defined MCP worker
    // "assessRisk" capability satisfied by builder-defined worker in a @Produces method
    
    @Goal("Document reviewed")
    @Completion
    GoalExpression done() {
        return GoalExpression.allOf(
            GoalExpression.goal("analysed"),
            GoalExpression.goal("extracted"),
            GoalExpression.goal("assessed"));
    }
}

// Builder-defined worker registered via CDI
@ApplicationScoped
public class RiskWorkerProducer {
    @Produces
    Worker riskWorker() {
        return Worker.builder()
            .name("risk-assessor")
            .capabilityName("assessRisk")
            .<RiskInput>fn().returning(RiskAssessment.class)
            .apply((input, scope) -> { ... })
            .build();
    }
}
```

The engine's capability resolution doesn't care how a worker was defined — annotation, builder, or YAML. It resolves by capability name.

## What stays as builders only

Not everything maps well to annotations. These stay as fluent DSL / builder code:

| Capability | Why builders are better |
|---|---|
| Dynamic routing logic | Runtime decisions need code, not static annotation values |
| Complex decomposition trees | Nested `CompoundTask` with guards needs builder expressiveness |
| Runtime `ExecutionModel` construction | Programmatic composition for dynamic patterns |
| Event-driven choreography | `EventSource`, `EventConcurrencyPolicy` are runtime abstractions |
| `ConversationOrchestrator` | Turn policies, prompt assemblers need code composition |
| Prompt optimisation batch | `PromptOptimisationBatch` is runtime orchestration |
| Summarisation pipeline | `SummarisationRunner` composition is inherently programmatic |

The annotation model handles the 80% case — declaring agents, wiring orchestration patterns, adding governance. The builders handle the 20% that needs runtime flexibility.

---

## Testing Strategy

### Unit tests

- Annotation presence and attribute validation (reflection tests)
- Generated bean equivalence: `assertThat(annotatedBean).isEqualTo(builderEquivalent)`
- Governance interceptor wiring: verify `@OversightGate` triggers classifier chain

### Integration tests (Quarkus build extension specifics)

- **`@QuarkusTest` with `@RegisterExtension`:** Build extensions run at build time, not runtime. Tests use `@RegisterExtension` to drive the build extension and then verify the generated CDI beans are available and correctly wired.
- **Three-extension composition:** LC4j + engine + blocks extensions processing the same interface. Tests must validate extension ordering and non-interference — each extension processes its own annotations without corrupting the others' output.
- **Negative compilation tests:** Invalid annotation combinations (e.g., `@Bind` referencing unknown capability, `@TrustRouted(minimumScore = 1.5)`) must produce compile-time errors. Use `@ShouldFail` test annotations or Quarkus's `@QuarkusIntegrationTest` failure mode.
- **Cross-module annotation composition:** Eidos annotations (`@Identity`) from `casehub-eidos-api` + blocks annotations (`@OversightGate`) from `casehub-blocks` on the same interface — verify the blocks build extension correctly scans annotations from multiple source modules.

### Drift-protection tests (annotation ↔ builder parity)

The three-programming-models invariant ("every `@Annotation` has a builder equivalent") must be enforced by tests, not trust. Following the precedent set by work module's `builderHasSetterForEveryField` tests:

- **Orchestration parity:** For each `@*Agent` annotation in blocks, assert that `Patterns.*()` has a corresponding builder method. E.g., `@DebateAgent` → `Patterns.debate()` exists, returns a builder with setters matching the annotation's attributes (`maxRounds`, `aggregation`). `@Convergence` is a standalone annotation — its parity test asserts that `DebateBuilder.convergence(TerminationCondition)` exists.
- **Case definition parity:** For each `@Case` attribute (namespace, name, version, planning), assert that `CaseDefinition.builder()` has a matching setter.
- **Worker parity:** For each `@Worker` attribute (capability, cost, value, timeoutMs, maxRetries), assert that `Worker.builder()` has a matching setter or builder method.
- **Governance parity:** For each governance annotation (`@OversightGate`, `@TrustRouted`, `@CbrRouted`), assert that the builder API has equivalent wiring (e.g., `AbstractPatternBuilder.route()` accepts a routing strategy that can incorporate oversight classification).
- **Eidos parity:** For each `@Identity`/`@Disposition` attribute, assert that `AgentDescriptor.builder()`/`AgentDisposition.builder()` has a matching setter.

These tests run as part of the build and fail if either side adds a field without updating the other. The test implementation uses reflection to enumerate annotation attributes and introspects the corresponding builder class for matching setters.

### Interop tests

- Annotation-defined agent referenced from `Patterns.*()` builder
- Builder-defined agent referenced from `@SupervisorAgent(subAgents = {...})`
- Mixed case: some workers annotated, some builder-defined, in same `CaseDefinition`
