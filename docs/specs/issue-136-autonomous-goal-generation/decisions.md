# Decisions — #136 Autonomous Goal Generation

## D1: Integration point — compose with engine's existing goal lifecycle

**Choice:** Bridge drives into the engine's existing GoalFormation pipeline via a new `GoalFormationService` SPI in engine-api. Blocks' `GoalProposalOrchestrator` produces proposals; the scheduler calls `GoalFormationService` to register them. Engine refactoring (extract service from `GoalFormationEvaluator`) included in #136 scope.
**Alternatives:**
- Blocks-direct registration via AgentRegistry (bypasses engine validation/audit) — simpler but duplicates validation logic and loses audit trail
- CDI event bridge (AutonomousGoalProposedEvent) — indirection layer with no clear benefit over direct SPI call
- Blocks calls GoalFormationStrategy.propose() directly + AgentRegistry.register() — duplicates the validation (MAX_GOALS, name length, deduplication), audit logging, cooldown, and autoApprove governance embedded in GoalFormationEvaluator
**Rationale:** The engine already has GoalFormationEvaluator, GoalRevisionEvaluator, DefaultGoalDecomposer, GoalSignalProvider, GoalAbandonmentEvaluator, and AgentGoalCompletionMarker. The SPI types (`GoalFormationContext`, `GoalFormationStrategy`, `GoalFormationProposal`) are already case-independent. The gap is that the registration orchestration — validation, deduplication, audit logging, cooldown, and autoApprove governance — is embedded in `GoalFormationEvaluator.evaluate(String, CaseInstance, List<String>)`, which requires `CaseInstance` as its entry point. Extracting a `GoalFormationService` makes this orchestration reusable for any goal formation source, not just case-bound evaluation. Blocks implements the drive-triggered path; the engine handles validation, registration, and audit.
**Trade-offs:** Requires engine-api + engine runtime changes (new SPI + implementation). Pre-release platform, so breaking changes are acceptable. GoalFormationContext should NOT be extended with drive-specific fields (e.g., DriveAxis) — that would couple engine-api to blocks' drive concept, violating the dependency rule.
**Sources:** `GoalFormationEvaluator.java` (engine runtime, 333 lines), `GoalFormationContext.java` (engine-api), `GoalFormationStrategy.java` (engine-api), `GoalFormationProposal.java` (engine-api), `DefaultGoalDecomposer.java` (engine planning)
**Exploration:** deep-analysis
**Status:** revised — corrected rationale: SPI types are already case-independent; the gap is the case-bound registration orchestration in GoalFormationEvaluator. Added alternative for direct SPI + AgentRegistry usage. Clarified GoalFormationContext must not gain drive-specific fields.

## D2: Scope — engine refactoring included in #136

**Choice:** Engine-side changes (GoalFormationService extraction from GoalFormationEvaluator) are part of #136, not a separate engine issue.
**Alternatives:**
- Separate engine issue with #136 depending on it — cleaner issue tracking but adds sequencing friction
- Both (file issue AND do inline) — redundant coordination
**Rationale:** Pre-release platform, cross-repo consolidation approved in CLAUDE.md. The engine refactoring is small (extract the validation/registration/audit/cooldown orchestration from GoalFormationEvaluator into a reusable GoalFormationService) and #136 can't ship without it. No GoalFormationContext extension needed — the SPI types are already case-independent.
**Trade-offs:** #136 touches engine repos. Acceptable for pre-release.
**Sources:** CLAUDE.md §Cross-Repo Consolidation Commits
**Exploration:** quick
**Status:** revised — corrected scope description: extraction of registration orchestration, not SPI type changes

## D3: Trigger mechanism — separate GoalProposalOrchestrator

**Choice:** New `GoalProposalOrchestrator` in blocks following the compositor pattern (tick() produces and caches proposals, no side effects). Reads DriveProfile, evaluates goal proposals via DriveGoalMappers, caches the result. Registration is a separate concern — the caller (scheduler) reads cached proposals and submits them to GoalFormationService. Separate from InnerLifeOrchestrator.
**Alternatives:**
- Generalise InnerLife to cover goal activation — expands InnerLife's scope, mixes LLM-driven speech evaluation with heuristic goal mapping
- InnerLife delegates to GoalProposal internally — tight coupling, InnerLifeTick sealed interface changes
- Effectful orchestrator (tick() calls GoalFormationService directly) — breaks compositor guarantees: not replayable, double-tick produces duplicate registrations, ordering-sensitive with consumers
**Rationale:** InnerLife uses LLM for motivation scoring + content generation. Goal proposal is heuristic (drive-to-goal mapping). Different evaluation mechanisms belong in different orchestrators. Follows the compositor pattern established by ADR-0001 (DriveOrchestrator): tick() evaluates + caches, accessor returns cached state, no side effects. The scheduler is responsible for the effectful step (registration). This enables inspection of proposals before registration and aligns with the engine's existing `autoApprove` governance concept.
**Ordering constraint:** source orchestrators → DriveOrchestrator → GoalProposalOrchestrator. The scheduler owns this sequence, same as the #129 constraint that drive ticks follow source orchestrator ticks.
**Trade-offs:** Another orchestrator to schedule. Registration as a separate step adds a scheduler responsibility. But it preserves compositor guarantees (replayability, no ordering sensitivity) and enables proposal review before registration.
**Sources:** `DriveOrchestrator` (#129 spec), ADR-0001 (drive compositor pattern), research blog `2026-08-21-mdp04-what-does-the-agent-want.md`
**Exploration:** deep-analysis
**Status:** revised — restructured as true compositor (tick caches proposals, no registration side effect). Added explicit ordering constraint. Added effectful alternative as rejected option.

## D4: Drive-to-goal mapping — heuristic base + optional LLM refinement

**Choice:** Per-axis `DriveGoalMapper` implementations produce goal proposals heuristically from source data as blocks-internal types. Default is zero-LLM-cost. `GoalFormationStrategy` available as opt-in refinement for richer descriptions. Cross-axis goal composition is deferred to Layer 3.
**Alternatives:**
- LLM-first — richer output but every goal evaluation costs an LLM call
- Pure heuristic — fast and free but goals may lack nuance for complex domains
- Cross-axis composition in Layer 2 — compound goals like "learn about X by engaging with Y" (curiosity + affiliation) — requires holistic self-model that belongs in Layer 3 narrative identity
**Rationale:** Follows the HeuristicMessageSummariser / LlmContentSummariser pattern already established in blocks' summarisation framework. Drive sources already carry provenance data (knowledge gap topics, declining dimensions, neglected subjects, misaligned intentions) — enough for concrete heuristic proposals. LLM refinement adds value for consumers who want richer goal descriptions.
**Heuristic/LLM layering:** Heuristic mappers produce a blocks-internal proposal type (e.g., `DriveGoalProposal`). Conversion to engine-api `GoalFormationProposal` happens at the registration boundary. If LLM refinement via `GoalFormationStrategy` is desired, a `GoalFormationContext` is constructed with: drive trigger strings as `reflectionInsights`, current goals from `AgentDescriptor`, and `remainingCapacity` computed from MAX_GOALS. Template-generated goal names must use normalised keys (lowercase, trimmed) for deduplication against `GoalFormationEvaluator`'s exact-name-match deduplication.
**Known limitation (LLM refinement):** Reusing `GoalFormationStrategy` for LLM refinement creates a semantic mismatch — `LlmGoalFormationStrategy` prompts the LLM as a "goal discovery analyst" processing "recent reflection insights" (confirmed in source at `LlmGoalFormationStrategy.buildPrompt()`, line ~97). Drive trigger strings like "3 low-retention memories across 5 groups" are not reflection insights — they're drive evaluation summaries. The LLM will likely produce reasonable goals regardless, but a purpose-built `DriveGoalFormationStrategy` with drive-specific prompt framing is the proper future improvement. Acceptable for Layer 2 since LLM refinement is opt-in.
**Trade-offs:** Heuristic goals are template-based ("reconnect with {subject}"), which may feel mechanical. LLM refinement opt-in handles this for consumers who care. Per-axis mappers cannot compose goals across axes — acceptable for Layer 2 where independent goals are sufficient.
**Sources:** `CuriosityDrive.java`, `CompetenceDrive.java`, `AffiliationDrive.java`, `AutonomyDrive.java` (#129 drive source specs), `HeuristicMessageSummariser.java`, `LlmContentSummariser.java` (blocks summarisation pattern)
**Exploration:** quick
**Status:** revised — added cross-axis composition alternative (deferred to Layer 3), clarified heuristic/LLM layering boundary, specified normalised deduplication keys for template goals

## D5: Source data — mappers read source orchestrators directly

**Choice:** Each per-axis `DriveGoalMapper` injects its source orchestrator (MemoryHygieneOrchestrator, UserModelOrchestrator, etc.) and reads the same data the DriveSource reads. DriveIntensity stays lean.
**Alternatives:**
- Enrich DriveIntensity with structured provenance — DriveIntensity grows, carries data most consumers don't need
- New DriveGoalContext alongside DriveProfile — another context type to manage
- DriveEvaluation record (intensity + goal suggestions from DriveSource) — eliminates parallel hierarchy but breaks Layer 1/2 separation: DriveSource is a Layer 1 (#129) interface and should not carry Layer 2 goal concepts. Layer 1 implementations would need to produce goal suggestions even when Layer 2 isn't in use.
**Rationale:** Same dependency pattern as the drive sources themselves. CuriosityDrive injects MemoryHygieneOrchestrator; CuriosityGoalMapper does the same. DriveIntensity reports intensity (a scalar), the mapper reads the details when a goal is warranted. Clean separation between "how strong is the drive?" and "what specific goal does it suggest?" The parallel constructor dependencies are the cost of clean Layer 1/2 separation — the drive system (#129) is a complete layer with clear boundaries; goal mapping (#136) builds on it without modifying it.
**Trade-offs:** Mappers have the same constructor dependencies as drive sources — parallel hierarchy. Acceptable because: (1) Layer 1/2 separation is preserved — DriveSource stays a pure Layer 1 interface, (2) each mapper is small and focused, (3) the dependency is on the same public API surface. Implementation note: if redundant data reading becomes a concern, drive sources can cache their intermediate data (e.g., CuriosityDrive.lastGaps()) for mapper consumption — this eliminates redundant reads without breaking layers.
**Sources:** `CuriosityDrive.java`, `CompetenceDrive.java`, `AffiliationDrive.java`, `AutonomyDrive.java` (#129 drive source specs)
**Depends on:** D1
**Exploration:** quick
**Status:** revised — added DriveEvaluation alternative (rejected for layer separation), added implementation note about cached intermediate data, fixed dependency from D2 to D1

## D6: Priority model — SECONDARY default, engine handles sequencing

**Choice:** Drive-sourced goals always use `GoalPriority.SECONDARY`. No PRIMARY escalation in Layer 2. The engine's existing priority system handles sequencing — SECONDARY goals execute only when no PRIMARY (case-assigned) work is available.
**Alternatives:**
- Dynamic priority based on drive intensity — complex, needs new priority levels beyond PRIMARY/SECONDARY
- Explicit idle-time detection — engine has no idle concept; building one is out of scope for Layer 2
- PRIMARY escalation via configurable intensity threshold — removed: creates an ungoverned pathway where runaway drives (e.g., accumulated curiosity from many low-retention memories) could preempt case-assigned work. The engine has no concept of goal provenance — a PRIMARY goal is treated equivalently regardless of source. PRIMARY escalation requires governance (human approval or Layer 3 narrative justification) before it's safe.
**Rationale:** The priority system IS the idle-time mechanism. Goals are always formed, but only executed when higher-priority work isn't available. No idle detection needed. GoalPriority enum already exists in eidos-api; SECONDARY is the natural default for self-generated goals. Sub-prioritization within SECONDARY (human-assigned vs drive-sourced) is unnecessary when capacity allocation (D7) ensures drive-sourced goals don't crowd out human-assigned ones.
**Trade-offs:** No dynamic priority escalation in Layer 2. Layer 3 narrative identity can add governed PRIMARY escalation with provenance tracking. GoalPriority extensibility for Layer 3 (enum in eidos-api, adding values breaks switch expressions) is a forward design concern — potential mitigation: a separate priority metadata field (ordinal weight within a tier) rather than enum extension.
**Sources:** `GoalPriority.java` (eidos-api — `enum GoalPriority { PRIMARY, SECONDARY }`), `GoalFormationEvaluator.java` line ~228 in `validateAndConvert()` (defaults to SECONDARY when `suggestedPriority()` is null)
**Exploration:** quick
**Status:** revised — removed PRIMARY escalation pathway (ungoverned, Layer 2 insufficient for safe governance). Added forward design note on GoalPriority extensibility for Layer 3.

## D7: Drive-sourced goal capacity allocation

**Choice:** Drive-sourced goals have a separate capacity budget within the engine's MAX_GOALS=10 limit. Configurable `maxDriveGoals` (default: 3) caps how many of the 10 goal slots drive-sourced proposals can occupy. A dedicated cooldown (`driveFormationCooldownMinutes`, default: 60) prevents rapid re-evaluation. The GoalProposalOrchestrator checks remaining drive-goal capacity before proposing. When proposals exceed `maxDriveGoals`, selection is by highest modulated drive intensity — the `DriveProfile` already carries per-axis intensities, so proposals are ranked by their originating drive's modulated intensity and the top N are selected.
**Alternatives:**
- Shared budget (no distinction) — drive-sourced and case-sourced goals compete equally for MAX_GOALS. Risk: active drives fill the cap, leaving no capacity for case-bound goal formation.
- Per-axis budget — each drive axis gets its own cap (e.g., max 1 curiosity goal). More granular but potentially over-constrained.
- No budget, rely on cooldown only — cooldown limits frequency but not quantity. 4 axes × 1 goal each = 4 goals per cycle, potentially filling 40% of capacity.
**Selection criterion:** When 4 axes produce proposals but only 3 slots are available, rank by modulated drive intensity (highest wins). This is data-driven (uses the drive system's own evaluation), non-biased (no axis is systematically favored — the weakest drive is excluded, which is correct), and deterministic (same inputs → same selection). Ties are broken by `DriveAxis` ordinal (CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY) for determinism.
**Rationale:** Resource allocation between drive-sourced and case-sourced goals is a fundamental design constraint. Without explicit capacity allocation, drive-sourced goals can crowd out human-assigned work. A separate cap ensures case-bound goal formation always has headroom. The cooldown aligns with GoalFormationEvaluator's existing 60-minute cooldown but is independently configurable.
**Trade-offs:** Adds configuration surface. Configurable default (3 of 10) is conservative — can be tuned per deployment. Intensity-based selection means a consistently weak axis (e.g., autonomy in a cooperative environment) naturally gets fewer goal slots — this is desired behavior, not a bias.
**Sources:** `GoalFormationEvaluator.java` (MAX_GOALS=10, maxNewPerReflection=2, cooldownMinutes=60), `DriveProfile` (#129 spec — carries per-axis modulated intensities)
**Surfaced by:** R1-11 (reviewer)
**Depends on:** D1, D3, D9
**Exploration:** surfaced-by-review
**Status:** revised — added explicit selection criterion (highest modulated intensity wins) for when proposals exceed maxDriveGoals. Added tie-breaking rule.

## D8: Drive-sourced goal lifecycle management

**Choice:** Drive-sourced goals have a relevance re-evaluation mechanism. On each GoalProposalOrchestrator tick, existing drive-sourced goals are checked against the current drive state. If the drive intensity that produced a goal drops below a configurable `relevanceThreshold` (default: 0.2) for a sustained period (`staleAfterMinutes`, default: 120), the goal is marked for removal from the AgentDescriptor. This supplements the engine's `GoalAbandonmentEvaluator` (failure-count-based) which cannot detect stale goals that were never attempted.
**Alternatives:**
- TTL expiry — drive-sourced goals expire after a fixed duration regardless of drive state. Simpler but discards goals where the drive is still active.
- No lifecycle management — rely on GoalAbandonmentEvaluator. Fails for drive-sourced goals: if no work is dispatched against a self-generated goal, it accumulates zero failures and persists indefinitely, consuming capacity against MAX_GOALS.
- GoalAbandonmentEvaluator extension — add a "no-progress" detection mode. Engine-side change for a blocks-specific concern; better kept in blocks.
**Rationale:** GoalAbandonmentEvaluator checks `failureCount >= threshold` (default 5). Drive-sourced goals that are never worked on accumulate no failures and persist indefinitely. Over time, this consumes goal capacity (against MAX_GOALS=10 and D7's maxDriveGoals=3) with stale goals. Relevance re-evaluation ties goal lifecycle to the drive that produced it — when the drive subsides, so does the goal.
**Trade-offs:** Adds complexity to GoalProposalOrchestrator (must track which goals are drive-sourced and evaluate their continued relevance). Goal provenance tracking (source=drive, axis=CURIOSITY) is required for this mechanism.
**Sources:** `GoalAbandonmentEvaluator.java` (engine runtime — failure-count-only abandonment), #129 spec (DriveProfile carries per-axis intensities)
**Surfaced by:** R1-22 (reviewer)
**Depends on:** D7, D3, D9
**Exploration:** surfaced-by-review
**Status:** captured

## D9: Goal provenance via AgentGoal attributes

**Choice:** Add `@Nullable Map<String, String> attributes` to `AgentGoal` in eidos-api. Drive-sourced goals store `{"source": "drive", "driveAxis": "CURIOSITY"}` (or whichever axis). Case-sourced goals store `{"source": "reflection"}` or remain null (backward compatible). Provenance is persisted with the AgentDescriptor via AgentRegistry — survives restarts.
**Alternatives:**
- In-memory map in GoalProposalOrchestrator (`Map<String, DriveAxis>` keyed by goal name) — lost on restart. D7's capacity allocation and D8's relevance evaluation become non-functional after restart until the next full evaluation cycle. Non-starter for reliable operation.
- Separate GoalProvenanceStore SPI — over-engineered. Adds a persistence layer, a new SPI, and registration-time coordination with GoalFormationService for one attribute. The provenance belongs on the goal itself.
- Naming convention encoding (e.g., prefix `[drive:curiosity]`) — fragile, pollutes goal names visible to the LLM system prompt renderer (`CognitiveObservationSections.goalsSection()` renders goal names directly), breaks if convention is not enforced everywhere.
- Typed `GoalSource` enum on AgentGoal — couples eidos-api to specific source concepts (reflection, drive). A generic `Map<String, String>` is more extensible and follows the established platform pattern.
**Rationale:** D7 needs provenance to enforce `maxDriveGoals` (count goals where `attributes.get("source").equals("drive")`). D8 needs provenance AND the originating `DriveAxis` to evaluate relevance (check if the drive that produced the goal is still active). `AgentGoal` currently has no provenance field. The `Map<String, String> attributes` pattern is established in the platform — `Memory(memoryId, entityId, domain, tenantId, caseId, text, attributes, createdAt, importance)` uses the same approach. Pre-release platform, so the record shape change is acceptable (same argument as D1/D2). The field is nullable for backward compatibility — existing goals (from CaseDefinition, from reflection-based formation) can have null attributes.
**Trade-offs:** Changes eidos-api record shape (AgentGoal gains a field). eidos-api is a zero-dep foundation module consumed by engine, apps, and tools. Pre-release, so acceptable. AgentGoal.Builder gains an `attributes()` method. GoalFormationEvaluator's `validateAndConvert()` should propagate the attributes field when constructing AgentGoal from ProposedGoal. AgentDescriptor validation (`goals.size() > 10`) is unchanged — attributes don't affect capacity counting.
**Sources:** `AgentGoal.java` (eidos-api — current record has 5 fields, no attributes), `Memory.java` (neocortex-memory-api — established `Map<String, String> attributes` pattern), `AgentDescriptor.java` (eidos-api — validates and persists goals list)
**Surfaced by:** R2-01 (reviewer)
**Depends on:** D1
**Exploration:** surfaced-by-review
**Status:** captured
