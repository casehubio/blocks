## D1: Judgment as a first-class execution loop phase

**Choice:** Add JudgmentPolicy as a 6th phase in the execution loop (route → activate → dispatch → aggregate → **judge** → terminate), rather than building separate yield-aware pattern variants.
**Alternatives:**
- Separate pattern variants (SUPERVISOR_WITH_JUDGMENT, DEBATE_WITH_JUDGMENT) — duplicates infrastructure, limits composability, causes variant explosion
- Hybrid (loop phase + pattern defaults) — unnecessary complexity when the loop phase alone gives every pattern yield support automatically
**Rationale:** Every pattern inherently supports yields without modification. Composable with existing TerminationCondition, ActivationRule, and other SPIs. No variant explosion. Patterns can pre-wire sensible JudgmentPolicy defaults via their builders.
**Trade-offs:** Adds a phase to every iteration even when judgment is not needed (mitigated by null JudgmentPolicy short-circuit). Every driver must handle the judgment phase.
**Sources:** `AbstractExecutionDriver.executeIteration()` (blocks), `ExecutionModel` record (blocks), epic #170 scope §3
**Exploration:** quick
**Status:** captured

## D2: JudgmentPolicy on ExecutionModel as @Nullable component

**Choice:** Add JudgmentPolicy as a direct @Nullable field on ExecutionModel (component 12). Null means no yields — existing patterns unchanged.
**Alternatives:**
- JudgmentConfig sub-record — reduces parameter count growth but adds nesting for a single concept
- Separate JudgmentModel — over-separates concerns that are part of the same execution lifecycle
**Rationale:** Follows existing precedent (backend is already @Nullable on ExecutionModel). Simple, no nesting. Null check in the loop is cheap.
**Trade-offs:** ExecutionModel grows to 12 components. Record constructors become long (mitigated by builders).
**Sources:** `ExecutionModel` record (blocks), `ExecutionBackend` @Nullable precedent
**Exploration:** quick
**Status:** captured

## D3: Yield wait mode as configuration, not driver choice

**Choice:** JudgmentPolicy carries a JudgmentWaitMode (SYNC or ASYNC) that determines how the driver waits for judgment responses. SYNC blocks the virtual thread inline (AgentProvider call returns the response directly). ASYNC yields to the engine and waits for completion — ChoreographedDriver via its event queue, OrchestratedDriver via a CompletableFuture that the engine's JudgmentCompletedEvent callback resolves. Both drivers support both modes, but the wait mechanism differs per driver.
**Alternatives:**
- ChoreographedDriver only — clean but prevents sync patterns from using yields
- New JudgmentAwareDriver — isolates concern but creates a third driver with duplicated code
**Rationale:** The wait mode is a policy decision, not a driver concern. Users choose sync or async based on their orchestration needs, not which driver they're using. The implementation detail (queue vs CompletableFuture) is internal to each driver.
**Trade-offs:** OrchestratedDriver needs a CompletableFuture bridge for ASYNC mode — the engine pushes completion via JudgmentCompletedEvent, not via a poll queue. Adds a callback registration mechanism to AbstractExecutionDriver.
**Depends on:** D1 (judgment is a loop phase)
**Sources:** `ChoreographedDriver` event queue (blocks), `OrchestratedDriver` sync loop (blocks), `JudgmentCompletedEvent` (engine)
**Exploration:** quick
**Status:** captured

## D4: LLM-as-caller — already done in engine-adapter (slot 160)

**Choice:** No blocks implementation needed. Slot 160 built `LlmJudgmentScheduler` in engine-adapter using `ChatModelProvider` (langchain4j). It implements `JudgmentScheduler`, dispatches on `CallerConfig.Llm`, publishes responses via Vert.x EventBus. Blocks consumes this via the engine's judgment infrastructure — it does not build its own LLM adapter.
**Alternatives:** (superseded — slot 160 settled this)
- AgentProvider-based adapter in blocks — would duplicate engine-adapter's impl
- ConversationOrchestrator integration — could be a future second JudgmentScheduler impl
**Rationale:** Engine-adapter owns caller adapters (LLM, A2A, Human). Blocks owns orchestration (when/how to trigger yields, fan-out, verification composition). Clean layer split.
**Trade-offs:** Blocks depends on engine-adapter being on the classpath for LLM judgment to work. This is the standard deployment model.
**Sources:** `LlmJudgmentScheduler` (slot 160 engine-adapter), `CallerConfig.Llm` (engine-api)
**Exploration:** quick
**Status:** revised — scope removed, engine owns this

## D5: Mandatory verification with explicit override — blocks composes engine-api's JudgmentVerifier

**Choice:** Every JudgmentPolicy requires a JudgmentVerifier (non-null). The `JudgmentVerifier` SPI and `VerificationResult` sealed hierarchy live in engine-api (slot 160, engine#997). Blocks does NOT redefine these — it composes them. Blocks provides: `CompositeVerifier` (chain multiple verifiers), `ConsensusVerifier` (M-of-N agreement), `EvidencePresenceVerifier` (required fields check), and `NoOpVerifier` (explicit opt-out). Patterns pre-wire sensible defaults.
**Alternatives:**
- Fully mandatory — no way to disable, prevents informational yield use cases
- Optional with encouragement — @Nullable verifier undermines "baked in not bolted on" principle
**Rationale:** Engine-api defines the SPI (`JudgmentVerifier`, `VerificationResult` with Accepted/InsufficientEvidence/TrustTooLow/Rejected). Engine-adapter provides concrete impls (SchemaValidation, LlmEvaluation). Blocks provides composition and enforcement — the orchestration layer.
**Trade-offs:** NoOpVerifier is a code smell detector — its presence in a codebase signals a design review opportunity.
**Depends on:** D1 (judgment is a loop phase)
**Sources:** `JudgmentVerifier` (engine-api, slot 160), `SchemaValidationVerifier`/`LlmEvaluationVerifier` (engine-adapter, slot 160), epic #170 scope §4
**Exploration:** quick
**Status:** revised — scope narrowed to composition layer

## D6: Per-verifier failure policy with re-yield-with-feedback default

**Choice:** Each JudgmentVerifier declares its own VerifierFailurePolicy (retry-with-feedback, escalate-immediately, fail). The default out-of-the-box policy is retry-with-feedback with configurable max retries and escalation after exhaustion.
**Alternatives:**
- Global re-yield with feedback only — simple but prevents domains from escalating immediately on schema failure
- Global escalate immediately — no retry, first caller gets one chance
**Rationale:** Different verification failures warrant different responses. Schema mismatch might need immediate escalation (the caller can't produce valid output). Insufficient evidence might benefit from retry with specific feedback about what's missing. The default (retry with feedback) handles the common case.
**Trade-offs:** More configuration surface. CompositeVerifier reconciles multiple failure policies using most-restrictive-wins: if any child verifier demands escalate-immediately, that takes precedence over retry-with-feedback from other children.
**Depends on:** D5 (mandatory verification)
**Sources:** Epic #170 scope §2 (retry with feedback), `FailurePolicy` precedent in ExecutionModel
**Exploration:** quick
**Status:** captured

## D7: JudgmentPolicy fan-out via CallerStrategy

**Choice:** JudgmentPolicy declares a CallerStrategy (single, fan-out to N callers, sequential escalation chain). Multi-party judgment is a policy concern, not a verifier concern. ConsensusVerifier checks agreement across collected responses.
**Alternatives:**
- Verifier-driven multi-party — ConsensusVerifier requests additional callers, mixing verification with caller management
- Defer multi-party — start single-caller only, add fan-out later
**Rationale:** Clean separation: CallerStrategy decides who judges, JudgmentVerifier decides if the response is acceptable. ConsensusVerifier only needs to check agreement — it doesn't manage caller dispatch.
**Trade-offs:** Fan-out is blocks-level orchestration — blocks calls `JudgmentScheduler.schedule()` N times (once per caller), then collects responses. The engine SPI takes a single request; blocks owns the fan-out loop and response collection. Adds complexity to the LLM-as-caller adapter for the fan-out case.
**Depends on:** D1 (judgment is a loop phase), D4 (LLM-as-caller)
**Sources:** Epic #170 scope §2 (ConsensusVerifier), `AggregationStrategy` fan-out precedent (blocks)
**Exploration:** quick
**Status:** captured

## D8: Extension points for future verifiers (E6/E7)

**Choice:** JudgmentVerifier SPI is open for extension. No placeholder interfaces for signed agent cards (E6) or temporal verification (E7). A well-designed SPI accepts arbitrary verifier implementations — SignedCardVerifier and TemporalPropertyVerifier can be added later without changing the core.
**Alternatives:**
- Placeholder interfaces — documents intent but creates maintenance burden with no implementation
- Ignore future — same outcome without making the extension intent explicit
**Rationale:** YAGNI. The SPI is a @FunctionalInterface that returns a verification result. Any future verifier (crypto, temporal, regulatory) implements the same interface. Placeholder interfaces add noise without value.
**Trade-offs:** No compile-time documentation of future verifier categories. This is acceptable — the SPI design itself is the documentation.
**Sources:** Epic #170 scope §2 (design forward), qhorus E6/E7 roadmap
**Exploration:** quick
**Status:** captured
