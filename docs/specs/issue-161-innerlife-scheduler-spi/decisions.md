## D1: Formalise CognitionCore as the scheduler

**Choice:** Make CognitionCore's ordering declarative via a phase model and bring InnerLife under its ordering contract, rather than extracting a separate CognitionScheduler SPI.
**Alternatives:**
- Extract a separate `CognitionScheduler` SPI — adds indirection without clear consumer benefit; CognitionCore already owns the composition
**Rationale:** CognitionCore is the natural composition root. The problem is implicit ordering and InnerLife's exclusion, not who schedules.
**Trade-offs:** CognitionCore gains more responsibility (phase dispatch) but avoids a new abstraction layer
**Sources:** CognitionCore.java (lines 97-137), InnerLifeOrchestrator.java (line 97)
**Exploration:** quick
**Status:** captured

## D2: Open participation with built-in defaults

**Choice:** Consumers can register custom `CognitionTickParticipant` implementations at specific phases. By default, only the built-in orchestrators are registered — consumers who don't extend get the same behaviour as today.
**Alternatives:**
- Configuration-only — consumers toggle built-in orchestrators but cannot inject custom participants. Simpler but closed to extension.
**Rationale:** Open participation is opt-in. No consumer pays complexity unless they add custom participants. Future-proofs without penalising the default case.
**Trade-offs:** Slightly more complex participant registration API
**Sources:** Issue #161 scope ("consumer implementations can extend with timer-based or event-based strategies")
**Exploration:** quick
**Status:** captured

## D3: Phase enum for ordering

**Choice:** Named phases (`FOUNDATION`, `SOURCE`, `SOURCE_PER_SUBJECT`, `DERIVED`, `TERMINAL`) with participants registered at a phase. Within a phase, ordering is undefined.
**Alternatives:**
- Dependency declaration (`after(X)` / `before(Y)`) — flexible but can produce surprising orderings and cycle errors
- Integer priority — simple but not self-documenting, fragile to collisions
**Rationale:** Phases capture the actual semantic structure (foundation → source → derived → terminal). Self-documenting, readable, avoids graph resolution complexity.
**Trade-offs:** Less flexible than dependency graphs — a participant that needs "after X but before Y within the same phase" has no mechanism
**Sources:** CognitionCore.java tick ordering analysis, #136 spec ordering constraint
**Exploration:** quick
**Status:** captured

## D4: Per-subject loop stays special

**Choice:** The per-subject iteration (userModel, mentalModel) remains hardcoded in CognitionCore as the `SOURCE_PER_SUBJECT` phase. Custom per-subject participants are not supported through the SPI.
**Alternatives:**
- Subject-aware participant interface with `tickPerSubject()` variant — adds interface complexity for a pattern used by exactly two orchestrators
**Rationale:** Per-subject ticking is a specific pattern with no consumer extension need. A consumer needing per-subject custom ticking can register a global participant that runs its own subject loop internally.
**Trade-offs:** Consumers cannot register per-subject participants — must handle subject iteration themselves
**Sources:** CognitionCore.java (lines 125-132), SubjectResolver SPI
**Exploration:** quick
**Status:** captured

## D5: Ordering-only integration with runtime enforcement (revised twice)

**Choice:** InnerLife does NOT join the automatic tick cycle. The redundant `driveOrchestrator.tick()` call inside InnerLife.doTick() is removed. A precondition check is added to `InnerLife.tick()` — asserts that `drives.currentDrives(agentId, tenantId)` is present, providing runtime enforcement that CognitionCore has ticked through DERIVED. InnerLife remains on-demand via `evaluateProactive()` with channel context.
**Alternatives:**
- Full TERMINAL integration — InnerLife registers at TERMINAL and runs every tick cycle. Resolves the unenforced protocol concern (decision review R1-02) but introduces a worse regression: InnerLife's tick consumes the event buffer and evaluates with `channelContext=null`, so a later `evaluateProactive(channelContext)` finds an empty buffer and returns Silent. Channel-aware motivation assessment is lost (spec review R1-02).
- Ordering-only without enforcement (original D5) — creates unenforced protocol with no API-level guarantee. Rejected by decision review R1-02.
**Rationale:** InnerLife needs channel context to evaluate properly — "should I speak in Slack?" is a different question from "should I speak in email?". Channel context isn't available during a scheduled tick cycle. The precondition check provides runtime enforcement without forcing InnerLife into a periodic tick that loses semantic information.
**Trade-offs:** InnerLife remains a special case outside the phase model. The precondition check is runtime enforcement, not structural — a consumer bypassing CognitionCore could still trigger InnerLife with stale drives, but would get a clear error.
**Sources:** InnerLifeOrchestrator.java (doTick line 97, buffer consumption lines 104-111, assemblePrompt channelContext usage line 191), ProactiveSpeechSupport.java, SocialAvatarCognition.java, decision review R1-01/R1-02/R1-03, spec review R1-02
**Exploration:** quick → surfaced-by-review → surfaced-by-review
**Status:** revised

## D6: Fix drive ordering — move after source orchestrators

**Choice:** Move drives from current position 4 (before strategy, userModel, mentalModel) to DERIVED phase (after SOURCE and SOURCE_PER_SUBJECT), fixing the data staleness for 3 of 4 drive sources.
**Alternatives:**
- Keep current ordering — drives sees one-tick-stale data from CompetenceDrive, AffiliationDrive, AutonomyDrive. Preserves current behaviour but contradicts #136 spec.
**Rationale:** The #136 spec says "source orchestrators → DriveOrchestrator → GoalProposalOrchestrator". Current ordering violates this. CompetenceDrive reads StrategyLearningOrchestrator, AffiliationDrive reads UserModelOrchestrator, AutonomyDrive reads MentalModelOrchestrator — all tick after drives today.
**Trade-offs:** Changes observable behaviour — drives will now see current-tick data instead of previous-tick data. Unlikely to cause issues but is a semantic change.
**Sources:** DriveOrchestrator.java (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive), #136 spec ordering constraint, CognitionCore.java tick ordering
**Exploration:** quick
**Status:** captured
