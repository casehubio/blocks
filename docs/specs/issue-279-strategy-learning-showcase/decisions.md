## D1: Fix 1-signal-per-tick accumulation problem

**Choice:** Fix signal correlation + send ConversationOutcome checkpoints
**Alternatives:**
- Accumulate-don't-drain (change tick semantics) — per-orchestrator fix, doesn't address correlation gap
- Cadenced ticking (tick at different frequencies) — adds state to CognitionCore, doesn't fix correlation
**Rationale:** The existing ConversationOutcome path in StrategyLearningOrchestrator was designed for exactly this purpose — batching turns into conversation cases. It just was never wired up. Giving each conversation a stable ID and sending periodic ConversationOutcome signals activates the existing code path without changing orchestrator internals.
**Trade-offs:** Requires ConversationRunner to manage a conversation ID and emit checkpoints — test infrastructure concern, not production. Production consumers (SocialAvatarCognition) would need their own ConversationOutcome emission. For this branch, a "conversation" is the full ConversationRunner run (one shared ID). Platform SPI for conversation boundary detection is a follow-on.
**Sources:** StrategyLearningOrchestrator.java:194-256 (ConversationOutcome matching path), CognitionCore.java:170-183 (random UUID caseId), ConversationRunner.java:88-93 (recordInteraction call site)
**Exploration:** quick
**Status:** captured

## D2: How to integrate reflect() into the conversation loop

**Choice:** Auto-trigger reflect() asynchronously inside tick() when enough cases exist
**Alternatives:**
- Explicit reflect() call at a turn threshold in ConversationRunner — test runner manages brain lifecycle, not platform behavior; every consumer must know when to call reflect()
- Separate reflect cadence in CognitionCore — adds coupling in wrong direction; CognitionCore shouldn't know strategy-specific lifecycle
- Synchronous auto-trigger in tick() — creates unpredictable turn latency; one turn becomes 5-30s slower when reflect fires alongside existing LLM calls (mood appraisal, BDI extraction)
**Rationale:** The orchestrator should be self-sufficient. When it has enough evidence (case count >= minCasesForReflection AND cooldown elapsed), it fires reflect asynchronously via CompletableFuture.runAsync(). The old StrategyProfile continues to be returned by currentStrategy() until the new one lands. The ReentrantLock already handles concurrency between async reflect and synchronous tick. This preserves tick predictability while keeping auto-trigger semantics — the brain learns in the background, just like real cognition.
**Trade-offs:** Brief window where currentStrategy() returns stale profile while async reflect runs. Acceptable — the profile updates within seconds, and the next turn picks up the new guidelines.
**Sources:** StrategyLearningOrchestrator.java:115-129 (tick), StrategyLearningOrchestrator.java:131-140 (reflect with ReentrantLock), StrategyLearningOrchestrator.java:307-358 (doReflect with LLM)
**Exploration:** deep-analysis
**Depends on:** D1 (signal correlation must work so cases get stored before reflect can fire)
**Revised:** R1-05 (synchronous LLM in tick creates unpredictable latency), R1-19 (tick synchronicity constraint)
**Status:** revised

## D3: Cold-start strategy via neurocortex priming

**Choice:** Prime CBR stores with synthetic interaction cases from descriptor data, let the pipeline synthesise
**Alternatives:**
- Construct StrategyProfile directly from descriptor heuristics — bypasses the pipeline; special-case seeding that doesn't demonstrate emergence
- One-shot LLM bootstrap call at construction — adds async startup cost and doesn't exercise the normal reflect pathway
- No seeding, wait for organic evidence — strategy contributes too late (turn 6-8 at best)
**Rationale:** The brain should wake up with memories and think about them through its normal cognitive loop. Priming the CBR store with synthetic cases (derived from descriptor constraints and disposition) means the auto-reflect in tick (D2) fires on the first tick, synthesising guidelines through the same LLM reflection path used for ongoing learning. No special-case code — primed data and lived experience flow through the same pipeline.
**Trade-offs:** Synthetic cases must have deliberately varied feature vectors (different affect shifts, continuation rates, response lengths across cases) to give TrendAnalyzer meaningful slope/volatility data. Identical feature vectors produce zero trends and generic guidelines. Case construction must model different interaction scenarios derived from the agent's constraints. Generalisation to other orchestrators is aspirational (#283) — this branch scopes priming to strategy CBR cases only.
**Sources:** StrategyLearningOrchestrator.java:307-358 (doReflect — processes CBR cases into guidelines), StrategyLearningOrchestrator.java:360-396 (analyzeTrends — needs variance), AgentDescriptor constraints + disposition (source data for synthetic cases), #283 (directive-minimal architecture — broader context)
**Exploration:** deep-analysis
**Depends on:** D1 (case storage), D2 (auto-reflect)
**Revised:** R1-07 (synthetic cases need variance for trend analysis), R1-08 (generalisation scoped to this branch)
**Status:** revised

## D4: UserModel quality signal derivation

**Choice:** Implement mood-derived quality directly in CognitionCore.recordInteraction() using pleasure+arousal convergence
**Alternatives:**
- Pluggable SPI with one implementation — premature abstraction; the SPI's value is adaptive switching which is deferred. Extract SPI when a second implementation exists.
- Always require explicit InteractionMapper — pushes complexity to every consumer
- Simple pleasure-only mapping — conflates emotional valence with relationship quality. Challenging intellectual debates produce negative pleasure but high arousal, marking the best interactions as negative quality.
**Rationale:** When CognitiveImpact.userModelSignal() is null (default path), derive quality from the mood appraisal just recorded. Use pleasure+arousal convergence: high arousal + positive pleasure = POSITIVE (engaged, positive exchange). Low arousal + near-zero/negative pleasure = NEGATIVE (boredom, disengagement). Everything else = NEUTRAL. This correctly handles the showcase scenario where Tesla and Leonardo debate intensely — high arousal signals engagement even when pleasure is low from cognitive strain. No SPI — direct implementation. CognitiveImpact.userModelSignal() (#282) is already the explicit override path for consumers that need full control.
**Trade-offs:** Couples quality derivation to mood state — if mood appraisal fails or is disabled, quality falls back to NEUTRAL. Acceptable — the fallback is the current behavior.
**Sources:** CognitionCore.java:143-157 (hardcoded NEUTRAL), CognitionCore.java:200-236 (mood appraisal produces pleasure/arousal), #282 CognitiveImpact (explicit override path)
**Exploration:** quick
**Revised:** R1-10 (premature SPI), R1-11 (pleasure ≠ quality — use pleasure+arousal convergence)
**Status:** revised

## D5: Goals — cooldown, missing sources, and priming scope

**Choice:** Fix mechanical issues in this branch: turn-based cooldown, wire full CuriosityDrive chain (MemoryHygieneOrchestrator → CuriosityDrive → CuriosityGoalMapper). Goal priming follows D3 pattern as a follow-on.
**Alternatives:**
- Goals-only priming (separate from strategy priming) — duplicates the priming mechanism
- Defer all goal fixes to #283 — leaves goals broken in the showcase
**Rationale:** The 60-minute wall-clock cooldown is wrong for compressed showcase timelines — use turn-based cooldown (e.g., minimum 3 turns between proposals) instead. The CuriosityDrive wiring requires the full chain: construct MemoryHygieneOrchestrator in CognitionStack → pass to CognitionCore (currently null) → create real CuriosityDrive from it → replace the baseline lambda → create CuriosityGoalMapper → add to mapper list. Goal priming is architecturally identical to strategy priming (D3) but scoped as a follow-on — this branch proves the pattern with strategy, #283 extends it.
**Trade-offs:** Wiring MemoryHygieneOrchestrator adds a dependency and construction complexity to CognitionStack. The orchestrator needs ConfidenceScorer and RetentionConfig — use defaults.
**Sources:** GoalProposalConfig.java (60min cooldown default), CognitionStack.java:113-114 (flat curiosity lambda), CognitionStack.java:143 (null MemoryHygieneOrchestrator), CuriosityDrive.java (requires MemoryHygieneOrchestrator)
**Exploration:** quick
**Revised:** R1-13 (turn-based cooldown), R1-14 (full CuriosityDrive wiring chain)
**Status:** revised

## D6: LLM proof tests structure

**Choice:** Per-orchestrator assertion tests in HistoricalEncounterLlmTest, complementing existing stage tests
**Alternatives:**
- Single full-conversation test with post-hoc assertions — coarser, harder to diagnose failures
**Rationale:** Existing stage tests (stage0 through stage6) progressively enable orchestrators and assert total section count. Per-orchestrator tests add precision: assert a SPECIFIC pipeline contributes by a SPECIFIC turn. E.g.: strategyContributesByTurn4(), goalsProposedByTurn6(). These complement stage tests — stages prove "cognition works at this level," per-orchestrator tests prove "this specific pipeline flows end-to-end." Each test runs a short conversation (4-8 turns) to bound LLM cost. Use soft assertions with retry tolerance for LLM nondeterminism — "contributes in N of M runs" rather than hard pass/fail on a single run.
**Trade-offs:** More LLM calls in the test suite. Tests under -Pllm are inherently flaky due to LLM nondeterminism — retry tolerance mitigates but doesn't eliminate. CI cost is bounded by running -Pllm on demand, not every build.
**Sources:** HistoricalEncounterLlmTest.java (existing stage tests), CognitionStack.Stage (stage-gated orchestrator wiring), CognitionConfig (per-orchestrator enable/disable)
**Exploration:** quick
**Revised:** R1-16 (relationship to existing stage tests), R1-17 (LLM cost and nondeterminism)
**Status:** revised
