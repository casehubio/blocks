## D1: Three separate SPIs, not one unified interface

**Choice:** Split into `SubjectResolver`, `InteractionMapper`, and `NormFilter` — three independent `@FunctionalInterface` SPIs with distinct integration points.
**Alternatives:**
- Single `CognitionContextStrategy` interface with 3 methods — cohesive but `filterNorms()` has no call site in CognitionCore, and `mapInteraction()` return type (`InteractionSignal`) is too narrow for the 4-orchestrator recording surface
- Context object approach (`CognitionContext` record) — forces immutable snapshot (thread-safe) but risks God object growth
- Decorator wrapping CognitionCore — CognitionCore stays unchanged but adds indirection
- Using existing `ObservationFilter` for norm filtering — operates on rendered `ObservationSection`s (sealed: EntityGroup | TextBlock | ItemList), not raw `SocialNorm` domain objects; `normsSection()` renders norms into a single `ItemList` with string items like `"[ESTABLISHED] Be polite"`, losing `participatingAgents`, `adherenceRate`, `strength` etc. needed for fine-grained filtering; `ObservationFilter`'s context is `Set<String> observerTags` (tag-based), not situational context (proximity, relationship state)
**Rationale:** Devil's advocate + judge analysis confirmed two HIGH-severity attacks on the unified approach: (1) `mapInteraction() → InteractionSignal` covers only 1 of 4 orchestrator channels — need `CognitiveImpact` record instead; (2) `filterNorms()` has zero references in CognitionCore — norms are consumed at prompt assembly, not in CognitionCore. Three SPIs respect the actual integration boundaries but at two distinct integration levels: SubjectResolver is a per-call parameter to `CognitionCore.tick()` — not constructor-injected, enabling SocialAvatarCognition to provide different resolvers per call (wrapping caller-provided `activeSubjects` or using a CDI-configured resolver). InteractionMapper is an adapter-layer SPI consumed by SocialAvatarCognition — it transforms domain interactions into CognitiveImpact, which is then passed as a per-call parameter to `CognitionCore.recordInteraction()`. CognitionCore never sees InteractionMapper directly. NormFilter is a domain-level filter at the prompt assembly layer — architecturally distinct from both and grouped as a cognition context concern from the consumer's perspective.
**Trade-offs:** Three SPIs to implement but with two distinct integration patterns: CognitionCore method parameters (SubjectResolver via `tick()`, CognitiveImpact via `recordInteraction()`) and adapter/assembly-layer CDI injection (InteractionMapper into SocialAvatarCognition, NormFilter into the prompt assembly layer). Consumers implementing all three (like wacky-manor) have three classes — but each is a `@FunctionalInterface`, so lambdas work.
**Sources:** CognitionCore.java:135-169 (recordInteraction 4-orchestrator surface), SocialNormDetector.java (no CognitionCore reference), CognitiveObservationSections (norm rendering at prompt assembly), ObservationFilter.java (filters rendered sections by observerTags — different level from NormFilter), ObservationSection.java (sealed interface: EntityGroup | TextBlock | ItemList)
**Exploration:** deep-analysis (devil's advocate + judge)
**Status:** revised (R1-03, R2-02: corrected SPI integration level characterization — SubjectResolver is per-call method parameter not constructor-injected; InteractionMapper is adapter-layer SPI consumed by SocialAvatarCognition not CognitionCore; trade-offs updated to reflect two distinct integration patterns)

## D2: CognitiveImpact record — typed fields per channel

**Choice:** `CognitiveImpact` record with one nullable field per orchestrator channel: `@Nullable InteractionSignal userModelSignal`, `@Nullable MoodSignal moodSignal`, `boolean suppressBdiExtraction`, `@Nullable EngagementSignal strategySignal`. Override semantics: null = CognitionCore runs its default processing for that channel (using raw `userMessage`/`response` parameters); non-null = replace default processing with the provided signal. Static `fromText(String)` factory for the common case (provides UserModel signal from text, delegates all other channels to defaults).
**Alternatives:**
- Map-based generic bag (`Map<String, Object>`) — extensible but loses type safety
- Per-channel nested records (`MoodImpact`, `EngagementImpact`) — over-engineers 4 nullable fields
- Original design with `@Nullable QualitySignal engagementQuality` — too thin for strategy channel; `StrategyLearningOrchestrator.record()` takes `EngagementSignal` (TurnOutcome with EngagementEvent + 14 fields, dimensional snapshot, response excerpt), not a simple quality enum
**Rationale:** Each channel has explicit override semantics. `MoodSignal` already has a `DirectShift` variant for external callers to bypass LLM appraisal — CognitiveImpact surfaces this existing capability. Strategy channel needs full `EngagementSignal` fidelity because `StrategyLearningOrchestrator` uses `EngagementEvent` fields for CBR case construction and trend analysis. `fromText(String)` factory replaces the ambiguous `passthrough(String)` — clearly communicates it provides only the UserModel signal from text.
**Trade-offs:** Adding a 5th orchestrator channel requires changing the record — acceptable since new channels are rare and warrant a deliberate API change.
**Sources:** CognitionCore.java:135-169 (4 orchestrator channels in recordInteraction), MoodSignal.java (DirectShift variant for external override), StrategyLearningOrchestrator.java:99 (record method takes EngagementSignal), EngagementSignal.java (TurnOutcome structure)
**Exploration:** quick
**Status:** revised (R1-05: explicit override semantics; R1-06: replaced QualitySignal with EngagementSignal for strategy channel; R1-07: renamed passthrough to fromText)

## D3: Single method signatures with SPI parameters

**Choice:** Replace existing method signatures with SPI-aware versions. `tick(agentId, tenantId, descriptor, resolver)` takes `SubjectResolver` instead of `Set<String> activeSubjects`. `recordInteraction(agentId, tenantId, subjectId, userMessage, response, impact)` takes an additional `@Nullable CognitiveImpact` parameter — null means all-default processing. No overloads.
**Alternatives:**
- Backward-compatible overloads (original D3) — add new overloads, keep existing methods. Rejected: CognitionCore is an internal class with one production caller per method (SocialAvatarCognition). Overloads for a single-caller internal class are backward-compatibility shims the design philosophy prohibits. Creates permanent maintenance burden: two entry points, two test paths, risk of divergence.
- Replace `Set<String> activeSubjects` entirely — same as chosen approach
**Rationale:** CognitionCore.tick() has exactly two production callers: SocialAvatarCognition.initialize() and SocialAvatarCognition.tick(). recordInteraction() has one: SocialAvatarCognition.recordInteraction(). One-file migration. Breaking the signature forces SocialAvatarCognition to be explicit about subject resolution and interaction mapping. SubjectResolver is the sole authority for subject determination at the CognitionCore level — no ambiguity about "who wins" between resolver and caller-provided set.
**Trade-offs:** SocialAvatarCognition must wrap caller-provided `Set<String> activeSubjects` into a `SubjectResolver` lambda — trivial: `(aid, tid, desc) -> activeSubjects`.
**Depends on:** D1 (separate SPIs), D2 (CognitiveImpact record)
**Exploration:** quick
**Status:** revised (R1-09: eliminated backward-compatible overloads; R1-10: single method per operation; R1-15: SubjectResolver is sole authority, no activeSubjects ambiguity)

## D4: Package locations — split by integration layer

**Choice:** SubjectResolver, InteractionMapper, and CognitiveImpact in `io.casehub.blocks.agentic.social` alongside CognitionCore. NormFilter in `io.casehub.blocks.agentic.social.emergence` alongside SocialNorm and SocialNormDetector.
**Alternatives:**
- All four types in `io.casehub.blocks.agentic.social` (original D4) — contradicts D1's boundary analysis: NormFilter has no CognitionCore integration point, yet sits next to CognitionCore
- NormFilter in `summarisation.observation.affordance` — near its integration point (CognitiveObservationSections) but foreign package for social norm domain types
- New sub-package `io.casehub.blocks.agentic.social.context` — over-organizes 3 types
**Rationale:** D1 established that NormFilter's integration point is at prompt assembly, not CognitionCore. Placing NormFilter in `agentic.social.emergence` co-locates it with the `SocialNorm` domain type it filters, while keeping SubjectResolver and InteractionMapper near CognitionCore where they integrate. This respects both domain ownership (NormFilter filters SocialNorm) and the boundary analysis (NormFilter is not a CognitionCore SPI).
**Trade-offs:** SPIs split across two packages — but this accurately reflects the two distinct integration layers.
**Exploration:** quick
**Status:** revised (R1-12: NormFilter moved to emergence package consistent with D1's boundary analysis)

## D5: AvatarCognition published interface unchanged

**Choice:** `AvatarCognition`'s published interface (in `casehub-blocks-speech-api`) does not change. SocialAvatarCognition adapts between the stable published SPI and the new CognitionCore parameters internally.
**Alternatives:**
- Evolve `AvatarCognition.tick()` to remove `Set<String> activeSubjects` — breaking change in a published cross-module SPI, forces all AvatarCognition callers to adapt
- Add SubjectResolver to `AvatarCognition.tick()` — leaks CognitionCore implementation detail into the speech API module
- Add new AvatarCognition methods alongside existing ones — same backward-compat shim problem as original D3, but at a published SPI boundary where it would actually matter
**Rationale:** AvatarCognition is a published SPI in `casehub-blocks-speech-api` with cross-module consumers. SubjectResolver and InteractionMapper are internal CognitionCore concerns. SocialAvatarCognition bridges the gap: receives `Set<String> activeSubjects` from AvatarCognition callers and wraps into a SubjectResolver; receives `(userMessage, response)` and optionally maps via InteractionMapper to produce `@Nullable CognitiveImpact`. If a custom SubjectResolver is configured (CDI bean), it takes precedence over the caller-provided set.
**Trade-offs:** Game integrations wanting custom SubjectResolver or InteractionMapper configure them as CDI beans injected into SocialAvatarCognition or CognitionCore — not through AvatarCognition. This is correct: game-specific cognitive customization belongs at the cognition layer, not the speech layer.
**Sources:** AvatarCognition.class (published SPI in casehub-blocks-speech-api — tick takes Set<String>, recordInteraction takes raw strings), SocialAvatarCognition.java (adapter between AvatarCognition and CognitionCore)
**Exploration:** implicit decision surfaced in review (R1-14, R1-15)
**Status:** captured
