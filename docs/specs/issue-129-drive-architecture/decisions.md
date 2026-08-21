# Design Decisions — Drive Architecture (#129)

## D1: Input model

**Choice:** Pull — drives read `currentXxx()` from source orchestrators
**Alternatives:**
- Tick outputs as signals — wire *Tick outcomes as DriveSignals via adapter. Adds push plumbing.
- Event-bus subscription — EventStreamBus pub/sub. Overkill for 4 data sources.
**Rationale:** Drives are fundamentally consumers of existing orchestrator state. Pull model avoids new signal types and wiring.
**Trade-offs:** Drives depend on source orchestrators being ticked first. Caller must ensure ordering.
**Sources:** Research blog (2026-08-21-mdp04), existing orchestrator record()/tick() pattern
**Exploration:** quick
**Status:** captured

## D2: Drive structure

**Choice:** Distinct classes per drive — CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive
**Alternatives:**
- Generic DriveSource<T> — single parameterised class. Extraction logic differs enough to make the generic forced.
- Enum-driven with strategy — DriveAxis enum with DriveEvaluator per axis. Conflates identity with evaluation.
**Rationale:** Each drive reads fundamentally different data (reflections vs engagement trends vs familiarity decay vs intention projections). Distinct classes match distinct extraction logic.
**Trade-offs:** More classes. Acceptable given each is small and focused.
**Sources:** Issue #129 epic children, SDT (Deci & Ryan, 2000) three-axis model (autonomy, competence, relatedness) + intrinsic motivation literature (Oudeyer & Kaplan 2007, Berlyne 1954) for curiosity as a fourth axis
**Exploration:** quick
**Status:** revised — corrected SDT attribution from four to three axes; curiosity sourced from intrinsic motivation literature, not SDT

## D3: Tick pattern

**Choice:** Hybrid — tick() evaluates + caches DriveProfile, currentDrives() returns cached state, no record()
**Alternatives:**
- tick() only (no cache) — recomputes every call. Breaks orchestrator pattern.
- Pure accessor (no tick) — always recomputes. No lifecycle control.
**Rationale:** Architecturally novel: the drive orchestrator is a compositor that reads derived state, not a signal accumulator. It has no raw signals to record — the source orchestrators already handle signal accumulation. tick() evaluates + caches, currentDrives() reads cached state. This is a deliberate departure from the universal record() + tick() pattern established by the six social cognition orchestrators.
**Trade-offs:** Breaks the record() + tick() contract that all other orchestrators follow. Justified because drives consume derived state, not raw events — adding record() would create a vestigial method with nothing to accumulate.
**Sources:** PersonalityEvolutionOrchestrator, MoodOrchestrator patterns (contributor guide), research blog "The Social Brain" (record() + tick() universal pattern)
**Exploration:** quick
**Status:** revised — acknowledged tick-only as architecturally novel (compositor pattern), not "established pattern minus record()"

## D4: Composer scope

**Choice:** Separate DriveComposer — composition is a distinct concern from evaluation
**Alternatives:**
- Internal to DriveOrchestrator — composition runs inside tick(). One class, one tick call. But conflates evaluation delegation (via DriveSource SPI) with composition, and hardwires composition for Layer 3's narrative feedback.
- DriveComposer as SPI — pluggable composition strategy. Full extensibility but premature if composition doesn't vary.
**Rationale:** Composition modulated by personality and mood is a distinct concern from drive evaluation. The research blog and issue #129 both model DriveComposer as separate. D8's DriveSource SPI already delegates evaluation; composition is the remaining concern inside the orchestrator. Separating it enables: (1) independent testing of composition logic, (2) Layer 3 narrative feedback can alter composition without refactoring orchestrator internals, (3) clear single-responsibility boundary. DriveOrchestrator orchestrates the lifecycle; DriveComposer owns the weighting/modulation algebra.
**Trade-offs:** One extra class. Justified by testability, Layer 3 trajectory, and alignment with the stated architecture.
**Sources:** Research blog Layer 1 description, research blog architecture diagram (DriveOrchestrator ──── DriveComposer), issue #129 children (DriveComposer as separate child)
**Exploration:** quick
**Status:** revised — separated DriveComposer from DriveOrchestrator per source material and Layer 3 trajectory

## D5: Modulation

**Choice:** Personality via AgentDescriptor.disposition() (tick parameter), mood via MoodOrchestrator.currentMood()
**Alternatives:**
- Constructor takes PersonalityEvolutionOrchestrator — INVALID: PersonalityEvolutionOrchestrator has no public accessor for current traits. It has only record() and tick(). Personality state flows through AgentDescriptor, which carries AgentDisposition with disposition profile values.
- Config-only modulation — static personality-to-drive mappings. Loses dynamic emergence.
- Signal-based modulation — personality/mood push signals. Contradicts D1 pull model.
**Rationale:** Personality state is carried on AgentDescriptor.disposition(), which is an immutable record passed per-tick. DriveOrchestrator.tick() takes AgentDescriptor as a parameter (same as PersonalityEvolutionOrchestrator.tick() and InnerLifeOrchestrator.tick()). Mood is read via MoodOrchestrator.currentMood(). DriveComposer (see D4) receives AgentDisposition from the tick parameter and MoodState from MoodOrchestrator — two modulation inputs with clean provenance.
**Trade-offs:** tick() signature requires AgentDescriptor parameter. This is the established pattern (PersonalityEvolutionOrchestrator and InnerLifeOrchestrator already take AgentDescriptor in tick).
**Sources:** Research blog ("personality modulates drive intensity"), AgentDescriptor.disposition() (eidos-api), MoodOrchestrator.currentMood() API
**Exploration:** quick
**Status:** revised — personality source corrected from nonexistent PersonalityEvolutionOrchestrator accessor to AgentDescriptor.disposition() tick parameter

## D6: Persistence

**Choice:** In-memory only — drives are derived state, recomputed on tick
**Alternatives:**
- DriveStore SPI with CbrDriveStore — persist for historical analysis. YAGNI for Layer 1.
- Interface-only (no-op default) — reserves extension point. Middle ground but still YAGNI.
**Rationale:** Drive state is fully derivable from source orchestrators' state. If the agent restarts, source orchestrators restore from their stores, and the next tick() recomputes drives. Same pattern as MoodOrchestrator.
**Trade-offs:** No drive history for trend analysis. Can add a store when Layer 2/3 needs it.
**Sources:** MoodOrchestrator (no store), StrategyLearningOrchestrator (has store — for non-derivable state)
**Exploration:** quick
**Status:** captured

## D7: MotivationAssessment integration

**Choice:** Leave as-is in this issue. File follow-up issue for InnerLife integration.
**Alternatives:**
- Evolve in this issue — InnerLife derives score from DriveProfile. Expands scope of #129.
- Replace entirely — DriveProfile subsumes MotivationAssessment. Largest scope expansion.
**Rationale:** Keeps #129 focused on the drive system. Integration is tracked as a follow-up, not forgotten.
**Trade-offs:** Temporary disconnection between drives and InnerLife's proactive initiation. Tracked via issue.
**Sources:** MotivationAssessment.java (package-private record in social/)
**Exploration:** quick
**Status:** captured

## D8: Type system architecture

**Choice:** DriveSource SPI + DriveOrchestrator composition
**Alternatives:**
- Monolithic DriveOrchestrator with inline evaluation — all drive logic in private methods. Simpler but harder to test individually, doesn't compose for Layer 3.
**Rationale:** DriveSource is a lightweight functional interface (one method). Each drive is independently testable. New drive axes can be added without touching the orchestrator. Layer 3 narrative feedback can add new sources. Precedent: TraitPressureSource<E> in personality evolution — a typed event-to-activation translator. TraitPressureSource is push-based (receives events via translate(E, AgentDescriptor)), while DriveSource is pull-based (reads orchestrator state). The push-vs-pull distinction is principled: trait pressure translates raw events at record-time; drive evaluation reads derived state at tick-time.
**Trade-offs:** One extra interface. Justified by testability and extensibility for Layer 3.
**Sources:** GE-20260729-172d18 (check before creating — confirmed no existing DriveSource SPI), research blog Layer 3 feedback loop, TraitPressureSource<E> (push-based precedent for typed source SPI)
**Exploration:** deep-analysis
**Depends on:** D2 (distinct classes implement DriveSource)
**Status:** revised — added TraitPressureSource precedent and push-vs-pull distinction

## D10: Per-subject data aggregation into agent-level drives

**Choice:** DriveSource implementations own aggregation — each source reads from the appropriate store's findByAgent() and applies domain-appropriate aggregation
**Alternatives:**
- tick() takes a subject list parameter — caller provides context, drives evaluate over specified subjects. Requires the caller to know which subjects are relevant.
- DriveOrchestrator maintains subject tracking — drives record subject IDs when source orchestrators tick. Adds record()-like state to a compositor that shouldn't have it (contradicts D3).
- Agent-level accessors on source orchestrators — new currentXxx(agentId, tenantId) methods that internally aggregate per-subject data. Pushes aggregation into source orchestrators that are intentionally per-subject.
**Rationale:** Aggregation is domain-specific. AffiliationDrive should aggregate familiarity across subjects differently from how AutonomyDrive aggregates intention alignment. The per-subject stores already support enumeration: UserProfileStore.findByAgent(agentId, tenantId) → List<UserProfile> and MentalModelStore.findByAgent(agentId, tenantId) → List<MentalModelSnapshot>. Each DriveSource reads from the store directly (not the orchestrator), applies its domain-appropriate aggregation (e.g., minimum familiarity, maximum misalignment, weighted average), and returns a scalar drive intensity. This keeps aggregation logic co-located with the domain expertise of each drive.
**Trade-offs:** DriveSource implementations depend on stores, not just orchestrators. This is a tighter coupling to persistence, but the alternative — adding aggregation methods to source orchestrators — would violate their per-subject design intent.
**Sources:** UserProfileStore.findByAgent(), MentalModelStore.findByAgent(), D8 (DriveSource SPI)
**Exploration:** quick (surfaced by reviewer)
**Depends on:** D8 (DriveSource SPI), D9 (API boundary)
**Status:** captured

## D9: API boundary for drive source data

**Choice:** Public API only — drives read existing public accessors or new ones added where needed.
**Alternatives:**
- Package-private access — put drives in same package. Tight coupling, no package separation.
- Intermediate signal types — new records per data flow. Most decoupled but most new types.
**Rationale:** Clean boundaries between drive sources and their upstream orchestrators. Required API surface per drive:
- **CuriosityDrive** (← MemoryHygiene): HygieneTick.Completed provides evicted/consolidated/totalScored counts and List<RetentionScore>. NEW ACCESSOR NEEDED: a knowledge-gap signal. Two candidate data sources to evaluate at implementation time: (A) retention data — derive gaps from eviction patterns and low-retention scores (indirect but self-contained within MemoryHygieneOrchestrator); (B) reflection outputs — derive gaps from ReflectionOrchestrator.reflect() insights that surface epistemic gaps like "agent keeps encountering topic X with no stored knowledge" (direct epistemic signal but adds a dependency on ReflectionOrchestrator from neocortex-memory-api). MemoryHygieneScheduler.maintain() already chains both: tick() → doReflection() via ReflectionOrchestrator. The right source depends on whether CuriosityDrive needs lifecycle-derived signals (what's fading) or epistemically-derived signals (what's missing).
- **CompetenceDrive** (← StrategyLearning): currentStrategy() returns StrategyProfile with dimensions and guidelines. NEW ACCESSOR NEEDED: engagement trend data (direction of improvement, response rate trajectory). StrategyProfile is a point-in-time snapshot; CompetenceDrive needs the derivative (improving/declining).
- **AffiliationDrive** (← UserModel): currentProfile() returns UserProfile with familiarityScore, relationshipStage, signal counts. SUFFICIENT for affiliation evaluation. Subject enumeration via UserProfileStore.findByAgent() (see D10).
- **AutonomyDrive** (← MentalModel): project() returns List<MentalProjection> with BDI conditions and confidence. SUFFICIENT for intention-alignment evaluation. Subject enumeration via MentalModelStore.findByAgent() (see D10).
- **Personality modulation**: AgentDescriptor.disposition() — no API addition needed, passed as tick parameter (see D5).
- **Mood modulation**: MoodOrchestrator.currentMood() — exists, no addition needed.
**Trade-offs:** Two new public accessors required (MemoryHygiene knowledge-gap, StrategyLearning engagement-trend). These are genuinely useful beyond drives — knowledge gaps inform InnerLife topic selection; engagement trends inform reflection scheduling.
**Sources:** Orchestrator public APIs (currentMood, currentProfile, project, currentStrategy), UserProfileStore.findByAgent(), MentalModelStore.findByAgent()
**Exploration:** quick
**Depends on:** D1 (pull model), D8 (DriveSource SPI)
**Status:** revised — enumerated required API additions per drive source with gap analysis
