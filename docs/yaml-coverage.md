# YAML Coverage Matrix

Living document tracking which blocks capabilities are YAML-expressible
and their implementation status. Update as gaps close.

**Goal:** Build meaningful AI applications in just YAML.

**Last audit:** 2026-09-11 (exhaustive — all 13 Maven modules scanned, summary table synced)

---

## Status Key

| Status | Meaning |
|--------|---------|
| Done | Spec record + registry + compiler/tests in agentic-yaml or summarisation-yaml |
| Spec only | Spec record exists, no compiler |
| Gap | YAML-expressible but not yet implemented |
| Partial | Some fields need Java (functional interfaces) |
| Code-only | Inherently procedural — Java required |
| N/A | Output type, utility, or runtime infrastructure |

---

## Module: agentic-yaml (#165)

### 1. Pattern Orchestration

| Capability | Types | Status | Notes |
|-----------|-------|--------|-------|
| Supervisor | `PatternSpec.Supervisor` | Done | Default: FirstMatch routing, MaxIter(10) |
| Debate | `PatternSpec.Debate` | Done | judge + maxRounds |
| Loop | `PatternSpec.Loop` | Done | maxIterations + exitCondition (expression) |
| Parallel | `PatternSpec.Parallel` | Done | Default: SelectAll, CollectAll |
| Voting | `PatternSpec.Voting` | Done | Default: SelectAll, MajorityVote |
| Conditional | `PatternSpec.Conditional` | Done | branches with condition expressions |
| Sequence | `PatternSpec.Sequence` | Done | Default: Sequential, AgentCount |
| HTN | `PatternSpec.Htn` | Done | rootTask with recursive TaskNodeSpec tree |

### 2. Strategy Families

| Family | Subtypes | Status |
|--------|----------|--------|
| Routing | FirstMatch, RoundRobin, Sequential, LlmSelected, SelectAll | Done |
| Termination | MaxIterations, GoalReached, JudgeConvergence, AllAgreed, Supervisor, Contested, Convergence, SinglePass, AgentCount | Done |
| Aggregation | PassThrough, CollectAll, MajorityVote, Auction | Done |
| Activation | OnDispatch, MaxIterationsGuard | Done |
| Decomposition | Identity, Static, Llm, Hybrid, Heuristic, Goap, CapabilityDependency, ForwardReasoning | Done |
| Failure policy | FailurePolicy (reused directly) | Done |
| Agent refs | Worker, Channel, Human, External, Composed | Done |

### 3. Judgment

| Area | Subtypes | Status |
|------|----------|--------|
| Triggers | AlwaysYield, NeverYield, IterationBased, ConfidenceThreshold | Done |
| Caller strategies | Single, FanOut, EscalationChain | Done |
| Agreement | Unanimous, Majority, Threshold | Done |

### 4. Conversation

| Capability | Status | Notes |
|-----------|--------|-------|
| Turn policies (RoundRobin, Addressed, PointAddressed, Free) | Done | |
| Epistemic rules (ExplicitAcknowledgement, TacitAcceptance, CommitmentResolution) | Done | |
| Convergence policies (Structural, CommonGroundRatio, Composite) | Done | |
| Conversation compiler (ConversationOrchestrator wiring) | Done | #249 — ConversationCompiler + CompiledConversation |
| Agent participants (name, role, systemPrompt) | Done | #249 — AgentParticipantSpec |
| Conversation protocol config (sentinel, entry types) | Done | #249 — ConversationProtocolSpec |
| Progress renderer types (percentage, count, step) | Done | #249 — ProgressRendererSpec |

### 5. Negotiation

| Capability | Status | Notes |
|-----------|--------|-------|
| Acceptance policies (Unanimous, Majority, Threshold) | Done | |
| NegotiationSpec (parties, acceptance, termination) | Done | #249 — NegotiationCompiler + CompiledNegotiation |
| AcceptedTermination | Done | #249 — TerminationSpec.Accepted |
| TerminalOutcomeTermination | Done | #249 — TerminationSpec.TerminalOutcome |
| DeadlineTermination (timeout) | Done | #249 — TerminationSpec.Deadline (Duration) |
| NegotiationProjection (parties + acceptance) | Done | #249 — compiled via NegotiationCompiler |

### 6. Normative Conflict Resolution

| Subtypes | Status |
|----------|--------|
| Priority, Specificity, Recency, MostRestrictive, Escalation | Done |

### 7. Expression Compilation (#242)

| Capability | Status | Issue |
|-----------|--------|-------|
| Type-safe MVEL per expression site | Done | #242 |
| Guard → RoutingCandidate context (Map projection) | Done | #242 |
| GoalReached → Map context | Done | #242 |
| ConfidenceThreshold → JudgmentContext (Map projection) | Done | #242 |

### 8. Schema & Infrastructure

| Capability | Status | Issue |
|-----------|--------|-------|
| Schema drift test with committed baseline | Done | #243 |
| AgentDescriptor wiring from spec fields | Done | #244 |
| Registry extensibility | Done | #245 |

---

## Module: summarisation-yaml (#233)

### 9. Summarisation Pipeline

| Capability | Status | Notes |
|-----------|--------|-------|
| PipelineDefinition (name, source, levels) | Done | Full YAML surface |
| LevelDefinition (name, grouping, summariser, emit) | Done | |
| SourceDefinition (type, cloudEventType, typePrefix) | Done | #254 — typePrefix added |
| GroupingDefinition.Windowed (age, count) | Done | |
| GroupingDefinition.Keyed (keyExpr, completionExpr, staleTimeout) | Done | #254 — KeyedSummarisationRunner wired via PipelineCompiler |
| PipelineCompiler (definition → CompiledPipeline) | Done | |
| PipelineValidator | Done | |
| SummariserRegistry (type:name → factory) | Done | |

### 10. Built-in Summariser Types

| Type name | Status | Notes |
|-----------|--------|-------|
| `threshold-classify` | Done | Per-event MVEL predicate rules → category |
| `phase-detect` | Done | Stateful state machine |
| `count` | Done | Event counting |
| `field-extract` | Done | #254 — registered in SummarisationRecorder |
| `pass-through` | Done | Registered in SummariserRegistry constructor |
| `verbatim` | Done | #254 — registered in SummarisationRecorder via asSummariser() bridge |

### 11. Summarisation Extensions (blocks module)

| Capability | Status | Notes |
|-----------|--------|-------|
| SummaryMode (APPEND / EDIT) | Done | #254 — mode field on SummariserDefinition |
| TieredContentSummariser thresholds | Done | #290 — tiered type in SummariserRegistry with recursive delegates |
| LlmContentSummariser (preamble, mode) | Partial | preamble + mode YAML; AgentProvider CDI |
| ContentSummariser.asSummariser() bridge | Done | Built into summarisation-api |

---

## Module: cloudevents

### 12. CloudEvent Bridge

| Capability | Status | Notes |
|-----------|--------|-------|
| CloudEventIngestionAdapter config (typePrefix, tenancyId extension) | Done | #254 — typePrefix on SourceDefinition |
| CloudEventEmitter config (cloudEventType) | Done | Wired via EmitDefinition → PipelineCompiler |
| PipelineTickScheduler (tickInterval) | Done | #254 — tickInterval on PipelineDefinition |
| EventSink | Code-only | @FunctionalInterface |

---

## Module: summarisation-api

### 13. Core Summarisation Types

| Capability | Status | Notes |
|-----------|--------|-------|
| WindowPolicy (maxAge, maxCount) | Done | Compiled from GroupingDefinition |
| EventLevel (name, ordinal) | Done | Used in LevelDefinition |
| Summariser, StatefulSummariser, ContentSummariser | Code-only | @FunctionalInterface — computation logic |
| SummarisationRunner | Done | Wired by PipelineCompiler |
| KeyedSummarisationRunner | Done | #254 — wired via keyed grouping compiler |
| EventStreamBus, EventAccumulator, KeyedAccumulator | Code-only | Runtime infrastructure |
| Compactor | Code-only | @FunctionalInterface |

---

## Module: blocks (main)

### 14. Social Cognition Configs

| Config | What it controls | Status |
|--------|-----------------|--------|
| `DriveConfig` | Intrinsic motivation axis weights, thresholds, modulation | Done | #247 |
| `MoodConfig` | PAD emotional baseline, decay, influence | Done | #247 |
| `PersonalityEvolutionConfig` | Trait drift rate, dampening, L2 ceiling | Done | #247 |
| `UserModelConfig` | Per-user profile signals, cooldown, decay, stage tiers | Done | #247 |
| `StrategyLearningConfig` | Interaction strategy adaptation rates | Done | #247 |
| `MentalModelConfig` | Theory of Mind confidence, projection | Done | #247 |
| `NarrativeConfig` | Episode/theme capacity, salience, reflection cap | Done | #247 |
| `NarrativeSynthesisGate` | Synthesis trigger: count + novelty + quiet period | Done | #247 (nested in NarrativeConfig) |
| `GoalProposalConfig` | Autonomous goal thresholds, capacity, cooldown | Done | #247 |
| `GoalEscalationConfig` | Priority escalation weights and cycles | Done | #247 |
| `NormDetectionConfig` | Social norm emergence thresholds, min agents | Done | #247 |
| `CollectiveGoalConfig` | Group goal alignment threshold, cooldown | Done | #247 |
| `RetentionConfig` | Memory eviction scoring weights | Done | #247 |

### 15. Affordance / World Model

| Capability | Status | Notes |
|-----------|--------|-------|
| ObservableEntity (id, name, description, affordances) | Done | #248 — EntitySpec + InlineEntitySpec |
| Affordance (actionType, label, requiredItem, acceptsItems) | Done | #248 — AffordanceSpec |
| ActionDescriptor (type, description, parameterFormat) | Done | #248 — ActionDescriptorSpec |
| ObservationSection (sealed: EntityGroup / TextBlock / ItemList) | Done | #248 — ObservationSectionSpec polymorphic |
| AnnotatedSection (requiredTags, resolution alternatives) | Done | #248 — inline annotation properties on section specs |
| ObservationPipeline (ordered filter chain) | Done | #248 — ObservationFilterSpec + ObservationFilterRegistry |
| PerceptionFilter (agentTags for visibility gating) | Done | #248 — PerceptionSpec named type |
| TieredObservationRenderer (tier thresholds) | Partial | Thresholds Done (#248 RendererSpec); renderer + key extractor code-only |

### 16. Channel Infrastructure

| Capability | Status | Notes |
|-----------|--------|-------|
| ChannelBinding (channelId, semantic) | Done | #249 — ChannelBindingSpec |
| ChannelExecutionStrategy (Conversation / FanIn / Barrier) | Partial | #249 — Conversation Done; FanIn/Barrier code-only (Function params) |
| AgentParticipant (name, role, systemPrompt) | Done | #249 — AgentParticipantSpec |
| ConversationProtocol config (sentinel, entry types) | Done | #249 — ConversationProtocolSpec |

### 17. Prompt Optimisation

| Capability | Status | Notes |
|-----------|--------|-------|
| OptimiserConfig (maxExamples, qualityThreshold, minOutcomes) | Done | #250 — direct reuse, no spec wrapper |
| SafetyConfig (qualityFloor, circuitBreaker, maxExperiments) | Done | #250 — direct reuse, no spec wrapper |
| PromptOptimiser types: few-shot / instruction | Done | #250 — PromptOptimiserSpec sealed interface |
| DiversityStrategy types: top-n / outcome-aware(weight) | Done | #250 — DiversityStrategySpec sealed interface |
| FewShotExample (input, output, outcome, qualityScore) | Done | #250 — direct reuse, no spec wrapper |
| PromptVariant (examples, instructionDelta, qualityScore) | Done | #250 — declarative subset via pipeline spec |
| PromptSignature (id, description, baseSystemPrompt) | Done | #250 — PromptSignatureSpec with string type refs |
| ConfidenceScorer types: arousal / surprise / composite | Done | #250 — ConfidenceScorerSpec sealed interface |

### 18. Execution Infrastructure

| Capability | Status | Notes |
|-----------|--------|-------|
| ExecutionBackend: reactive / choreographed | Done | #251 — ExecutionBackendSpec sealed interface |
| EventConcurrencyPolicy: serialize / coalesce / coalesce-by-source | Done | #251 — EventConcurrencyPolicySpec sealed interface |
| Listener types: event-log / ledger / metrics | Done | #251 — ExecutionListenerSpec sealed interface |
| CoalitionEvaluator: capability-coverage | Done | #251 — CoalitionEvaluatorSpec sealed interface |
| JointIntention (intentionId, plan, parties) | Done | #251 — JointIntentionSpec adapted record |
| ReconsiderationSignal (reason enum, detail) | Done | #251 — direct reuse |

### 19. Trust & Routing Config

| Capability | Status | Notes |
|-----------|--------|-------|
| TrustRoutingPolicyKeys (threshold, minObservations, blendFactor) | Done | #252 — adapted record spec |
| CbrOutcomeWeights (outcome → weight map) | Done | #252 — weight map spec |
| CoordinationOutcomeWeights (outcome → weight map) | Done | #252 — weight map spec |
| DispositionProfile (desired traits + axis weights) | Done | #252 — direct reuse |
| AttestationContext (tenancyId, caseId, capabilityTag) | N/A | Runtime data — not config |
| AttestationIntent (full attestation payload) | N/A | Runtime data — not config |

### 20. Oversight

| Capability | Status | Notes |
|-----------|--------|-------|
| RiskDecision (Autonomous / GateRequired) | Done | #252 — sealed interface spec |
| ClassificationContext (workerId, caseId, tenancyId) | N/A | Runtime data — not config |
| GateOutcome (Autonomous / GatePending) | N/A | Response type — not config |

---

## Module: engine-adapter

### 21. Engine Integration

| Capability | Status | Notes |
|-----------|--------|-------|
| PatternJudgmentConfig (prompt, callerConfig, verifierStrategy, mode) | Done | #255 — PatternJudgmentConfigSpec + CallerConfigSpec (full mirror) |
| EngineHostedBackend | Done | #255 — ExecutionBackendSpec.EngineHosted |
| CheckpointingListener | Done | #255 — ExecutionListenerSpec.Checkpointing |
| LlmEvaluationVerifier | Done | #255 — VerifierStrategySpec.LlmEvaluation |
| SchemaValidationVerifier | Done | #255 — VerifierStrategySpec.SchemaValidation |

---

## Module: speech-api

### 22. Speech Pipeline Config

| Capability | Status | Notes |
|-----------|--------|-------|
| TranscriptionOptions (audioFormat, languageHint, modelSize, vocabularyHint) | Done | #253 — direct reuse |
| SynthesisOptions (voice, language, audioFormat, includePhonemes) | Done | #253 — direct reuse |
| CleanupConfig (maxDestructiveness + filter list) | Partial | Threshold YAML; TextFilter list CDI |
| CorrectionStrategy (NONE / BASIC / AGGRESSIVE) | N/A | @FunctionalInterface, not enum |
| ConversationTurn (role, content) | N/A | Runtime data |
| AssembledPrompt (systemPrompt, userPrompt, model override) | N/A | Runtime output |
| PromptContext (agentId, tenantId, subjectId) | N/A | Runtime context |

---

## Module: speech-ws

### 23. Avatar WebSocket Config

| Capability | Status | Notes |
|-----------|--------|-------|
| AvatarConfig (sampleRate, maxDestructiveness, systemPrompt, agentId, tenantId, proactiveTickInterval) | N/A | Already @ConfigMapping via Quarkus YAML |
| VisemeMapping (IPA → viseme mapping with weights) | N/A | Static utility class |

---

## Module: speech-sherpa

### 24. Speech Model Configs

| Capability | Status | Notes |
|-----------|--------|-------|
| SherpaConfig (modelDir, numThreads, provider) | Done | #253 — adapted record |
| KokoroConfig (modelDir, voiceId, lengthScale, numThreads) | Done | #253 — adapted record |
| Audio8Config (modelDir, variant, numThreads, temperature, topP, topK) | Done | #253 — adapted record |
| GectorConfig (modelPath, maxIterations, keepConfidence, minErrorProb) | N/A | Filesystem-derived |

---

## Module: annotations

### 25. Pattern Annotations (peer pathway to YAML)

| Annotation | YAML equivalent | Status |
|-----------|----------------|--------|
| `@Supervisor`, `@Debate`, `@Loop`, `@Parallel`, `@Voting`, `@Conditional`, `@Sequence`, `@Htn` | PatternSpec topologies | Done (YAML side) |
| `@Agent`, `@Debater`, `@Judge`, `@Voter` | AgentRefSpec roles | Done (YAML side) |
| `@Attestation`, `@OversightGate`, `@TrustRouted`, `@CbrRouted` | Governance specs | Done | #292 — GovernanceSpec composable on PatternSpec |

---

## Summary

| Module | Section | Total | Done | Gap | Partial | Code-only | N/A |
|--------|---------|-------|------|-----|---------|-----------|-----|
| agentic-yaml | Patterns (1-3) | 41 | **41** | — | — | — | — |
| agentic-yaml | Conversation (4) | 7 | **7** | — | — | — | — |
| agentic-yaml | Negotiation (5) | 6 | **6** | — | — | — | — |
| agentic-yaml | Normative (6) | 5 | **5** | — | — | — | — |
| agentic-yaml | Expression/infra (7-8) | 7 | **7** | — | — | — | — |
| summarisation-yaml | Pipeline (9-10) | 14 | **14** | — | — | — | — |
| summarisation-yaml | Extensions (11) | 4 | 3 | — | 1 | — | — |
| cloudevents | Bridge (12) | 4 | 3 | — | — | 1 | — |
| summarisation-api | Core (13) | 7 | **4** | — | — | 3 | — |
| blocks | Social configs (14) | 13 | **13** | — | — | — | — |
| blocks | Affordance (15) | 8 | **7** | — | 1 | — | — |
| blocks | Channel (16) | 4 | **3** | — | 1 | — | — |
| blocks | Prompt optim (17) | 8 | **8** | — | — | — | — |
| blocks | Execution (18) | 6 | **6** | — | — | — | — |
| blocks | Trust/routing (19) | 6 | **4** | — | — | — | 2 |
| blocks | Oversight (20) | 3 | **1** | — | — | — | 2 |
| engine-adapter | Engine (21) | 5 | **5** | — | — | — | — |
| speech-api | Speech (22) | 7 | **2** | — | 1 | — | 4 |
| speech-ws | Avatar (23) | 2 | — | — | — | — | 2 |
| speech-sherpa | Models (24) | 4 | **3** | — | — | — | 1 |
| annotations | Governance (25) | 3 | **3** | — | — | — | — |
| **Total** | | **164** | **144** | **—** | **4** | **4** | **11** |

**Coverage: 144/149 countable (97%).** Countable excludes N/A (runtime data, static utilities)
and Code-only (functional interfaces). 0 remaining Gaps, 4 Partials at their natural ceiling.
