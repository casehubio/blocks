# Decisions — #142 Narrative Identity + Social Emergence

## D1: Epic scope — design spec + foundation types

**Choice:** #142 delivers a design spec covering all six children, plus foundation types/SPIs that children implement against
**Alternatives:**
- Design spec only — no code, children start from scratch
- Design spec + first child (#143) — spec and implement together
**Rationale:** Foundation types force the design to be concrete at the interface level. If the types compile and compose correctly, the design is proven before any orchestrator implementation. Children implement against stable interfaces.
**Trade-offs:** More upfront work on #142; types may need revision as children discover implementation-level issues
**Sources:** Layer 1 (#129) and Layer 2 (#136) both delivered foundation types alongside specs
**Exploration:** quick
**Status:** captured

## D2: Thread depth — both threads equally

**Choice:** Full type-level design for both narrative identity (#143-#145) and social emergence (#146-#148)
**Alternatives:**
- Narrative deep, social sketch — defer social emergence types
- Narrative only — defer social emergence entirely
**Rationale:** Social emergence composes with existing multi-agent infrastructure (ConversationOrchestrator, CoalitionFormation, JointIntention). The composition points are concrete enough for type-level design. Deferring would mean designing social emergence without the narrative foundation types it needs to compose with.
**Trade-offs:** Larger spec; social emergence types are more speculative and may change more during implementation
**Sources:** Issue #142 body; blog 2026-08-21-mdp04
**Exploration:** quick
**Status:** captured

## D3: Split architecture — NarrativeSynthesiser (effectful) + NarrativeOrchestrator (compositor)

**Choice:** Two components: (1) NarrativeSynthesiser — effectful, scheduled, calls LLM to produce narrative from reflections, writes to NarrativeStore; (2) NarrativeOrchestrator — true compositor, reads from NarrativeStore on tick(), no LLM, no side effects. Mirrors the MemoryHygieneScheduler/MemoryHygieneOrchestrator split.
**Alternatives:**
- Single NarrativeOrchestrator with LLM in tick() — breaks compositor guarantee (non-deterministic, expensive LLM calls are side effects; ADR-0001 defines compositors as deterministic with no persistence)
- Record + tick — receive narrative-relevant events via record()
- Hybrid — compositor for reflections, record() for narrative-specific events
**Rationale:** LLM synthesis (producing episodes and themes from reflections) is non-deterministic and expensive — it cannot live in a compositor tick(). Splitting into synthesiser + compositor preserves ADR-0001's guarantees while supporting the LLM requirement. The synthesiser is called by the consumer's scheduler; the compositor reads pre-synthesised state from NarrativeStore.
**Trade-offs:** Two components instead of one. The synthesiser needs its own scheduling and the consumer must call it before ticking the orchestrator. This is the same pattern as MemoryHygieneScheduler calling ReflectionOrchestrator before ticking MemoryHygieneOrchestrator.
**Note:** NarrativeSynthesiser reads stored reflections via the `ReflectionQueryStore` SPI (`findSince`, `countSince`), which exists at `io.casehub.blocks.memory.ReflectionQueryStore` with a `NoOpReflectionQueryStore` `@DefaultBean`.
**Sources:** ADR-0001-drive-compositor-pattern.md; MemoryHygieneScheduler.java; MemoryHygieneOrchestrator.java; ReflectionQueryStore.java; decision review R1-02, R1-03, R1-07, R1-11
**Exploration:** quick → revised after decision review
**Status:** revised

## D4: Narrative feedback enters drive system as DriveComposer modulation layer

**Choice:** Third modulation layer in DriveComposer.compose(), alongside mood and personality
**Alternatives:**
- New DriveSource implementation — wrong abstraction; DriveSource extracts raw intensity for one axis from one data source; narrative modulates ALL axes simultaneously based on story themes; Map.put semantics means a NarrativeDriveSource would overwrite existing axis values rather than modulate them
- Direct intensity override — couples narrative to drive internals
**Rationale:** Narrative doesn't generate new raw drive intensities — it contextually amplifies/dampens existing ones based on self-story themes. This is semantically identical to what mood and personality modulation already do. DriveComposer.compose() gains a @Nullable NarrativeState parameter; null = no modulation.
**Trade-offs:** Changes DriveComposer.compose() signature (additive — new nullable parameter). All existing callers pass null until NarrativeOrchestrator is wired in.
**Sources:** DriveComposer design (spec #129); DriveOrchestrator.java; ADR-0001
**Exploration:** deep-analysis
**Status:** captured

## D5: Narrative model — sealed hierarchy with episodes and themes

**Choice:** NarrativeFragment sealed interface with three permits: IndividualEpisode (personal experience with emotional valence), GroupEpisode (collective experience with membership + role attributions), DerivedTheme (typed salience score with per-axis modulation weights). Episodes are raw material; themes are derived. Both share the NarrativeFragment base (temporal span, thematic tags).
**Alternatives:**
- Theme-based only — simpler, maps cleanly to drive modulation, but loses narrative richness
- Episode-based only — richer narrative fidelity, but requires additional extraction step for drive modulation
- Flat hybrid without sealed hierarchy — episodes and themes as separate types with no common interface; loses composition between individual and group scope
**Rationale:** Episodes serve BOTH prompt rendering and drive modulation (episode emotional valence directly informs drive intensity). Themes also serve BOTH consumers (themes appear in agent prompts for self-consistent behavior, and provide structured drive modulation weights). The sealed hierarchy enables individual narrative to reference GroupEpisode instances and group identity to aggregate IndividualEpisode themes — composition across scopes.
**Trade-offs:** More complex type system; LLM synthesis must produce structured output matching the sealed hierarchy. GroupEpisode adds membership and role fields that IndividualEpisode doesn't need.
**Sources:** CognitiveObservationSections.java; DriveComposer (spec #129); blog 2026-08-21-mdp04; decision review R1-03, R1-05, R1-09
**Exploration:** quick → revised after decision review
**Status:** revised

## D6: Narrative update trigger — composite gate (count + novelty + quiet period)

**Choice:** Re-synthesise when: (a) N new reflections accumulated AND at least one scores above a novelty threshold (vs previous narrative state), OR (b) quiet period bypass — re-synthesise after a configurable inactivity duration regardless of count. Modelled after InnerLifeConfig.ContentQualityGate.
**Alternatives:**
- Count-only threshold — regresses from ContentQualityGate pattern; misses urgently relevant reflections and wastes LLM on redundant ones
- Significance-gated — per-reflection significance scoring; too complex for the trigger mechanism
- Time-windowed — fixed interval regardless of reflection volume; wastes LLM on empty windows
**Rationale:** ContentQualityGate combines three mechanisms: count gate (minObservations), quality gate (noveltyThreshold), inactivity bypass (quietPeriodBypass). The narrative trigger follows the same pattern — count bounds cost, novelty ensures quality, quiet period ensures responsiveness to long idle periods.
**Trade-offs:** Novelty scoring requires comparing new reflections against current narrative state. Simple approach: token Jaccard distance (already used by InnerLifeOrchestrator.TokenJaccardDistance) between new reflection text and current narrative text.
**Sources:** InnerLifeConfig.ContentQualityGate; TokenJaccardDistance.java; decision review R1-06
**Exploration:** quick → revised after decision review
**Status:** revised

## D7: Social norm detection via cross-conversation memory

**Choice:** SocialNormDetector reads from a NormObservationStore (CBR-backed) that accumulates interaction patterns across conversations. Compositor pattern — reads accumulated data on tick().
**Alternatives:**
- ConversationOrchestrator listener — real-time observation via ConversationListener.onDispatch(), record+tick pattern
- CommonGroundState projection — reuse epistemic machinery, project established facts across sessions
**Rationale:** Norms emerge across conversations over time, not within a single conversation. CBR storage enables pattern frequency and consistency analysis across agents. CommonGroundState captures epistemic claims, not behavioral patterns (actions, sequences, rituals). The listener approach couples too tightly to a single interaction mechanism.
**Trade-offs:** Requires a new store SPI (NormObservationStore). CBR queries for pattern frequency need careful feature design. Someone must record observations into the store — likely via a ConversationListener or channel observer, but the detector itself is a compositor.
**Sources:** CommonGroundAnalyser.java; ConversationOrchestrator; CbrCaseMemoryStore (neocortex)
**Exploration:** quick
**Status:** captured

## D8: Collective goal formation via drive profile aggregation

**Choice:** CollectiveGoalFormation reads DriveProfiles via a consumer-provided profile resolver (`Function<String, Optional<DriveProfile>>`), not from DriveOrchestrator directly. Alignment is computed as cosine similarity of normalised drive intensity vectors — proportional similarity regardless of magnitude. JointIntention is proposed when alignment exceeds a configurable threshold.
**Alternatives:**
- Goal overlap detection — scan individual currentProposals() for similar names; depends on name heuristics
- Explicit negotiation — full NegotiationProjection cycle; heavyweight but robust
- Dominant-axis match for alignment metric — finds agents with same primary drive; misses proportional alignment across all axes
- Per-axis absolute difference for alignment metric — finds agents with similar raw intensities; biased toward low-drive agents being always "aligned"
- Direct DriveOrchestrator injection — reads from in-memory ConcurrentHashMap; single-process only, fails silently in multi-process deployments
**Rationale:** Drive alignment is more fundamental than goal naming similarity — two agents may express the same drive through differently-named goals. JointIntention already handles the commitment lifecycle (form/activate/reconsider/drop/fulfill). CollectiveGoalFormation bridges DriveProfiles to JointIntention proposals. Consumer-provided profile resolver decouples from the deployment model — single-process consumers wire it to DriveOrchestrator.currentDrives(); multi-process consumers wire it to a shared store. Cosine similarity is the right default because drive alignment is about proportional emphasis, not absolute magnitude — two agents both emphasising AFFILIATION > CURIOSITY > COMPETENCE > AUTONOMY are aligned even if their absolute intensities differ.
**Trade-offs:** Consumer must wire the profile resolver. Cosine similarity may over-trigger for agents with naturally similar profiles (e.g., same personality archetype); the configurable threshold mitigates this.
**Sources:** JointIntention.java; CoalitionProposal.java; DriveProfile (spec #129); decision review R1-04, R1-05
**Exploration:** quick → revised after decision review
**Status:** revised

## D9: Group identity shares narrative type system via sealed hierarchy

**Choice:** GroupIdentity uses the NarrativeFragment sealed hierarchy (D5). GroupEpisode is a permit of NarrativeFragment alongside IndividualEpisode and DerivedTheme. Group-specific fields (membership set, role attributions, consensus level) live on GroupEpisode — not forced into the base interface.
**Alternatives:**
- Distinct type system — separate GroupNarrative hierarchy with no composition with individual narrative
- Flat shared types — same type for both scopes, generalized fields for group-specific dimensions
**Rationale:** The sealed hierarchy preserves composition (individual narrative can reference GroupEpisode instances, group identity can aggregate IndividualEpisode themes) while allowing structural differences (GroupEpisode tracks membership dynamics, IndividualEpisode tracks protagonist introspection). Identity persists despite member turnover — the group narrative is maintained by the GroupNarrativeOrchestrator, not by any individual member.
**Trade-offs:** Group narratives require a mechanism for negotiated identity — members may disagree about the group story. This is deferred to GroupNarrativeOrchestrator implementation (#148), not solved at the type level.
**Depends on:** D5 (sealed hierarchy)
**Sources:** Blog 2026-08-21-mdp04 (group identity section); decision review R1-09
**Exploration:** quick → revised after decision review
**Status:** revised

## D10: Tick ordering — narrative before drives, two-phase

**Choice:** Scheduler ordering: source orchestrators → NarrativeSynthesiser (if triggered) → NarrativeOrchestrator → DriveOrchestrator → GoalProposalOrchestrator. NarrativeOrchestrator sees current reflections but previous-cycle drives. DriveOrchestrator sees current narrative.
**Alternatives:**
- Drives before narrative — narrative would see current drives but drives wouldn't reflect current narrative themes; backwards dependency
- Simultaneous / lag-one on both — breaks the derivability guarantee; both see stale data from the other
**Rationale:** D3 (narrative reads reflections) and D4 (drives read narrative) create a feedback loop. The loop is broken by making NarrativeOrchestrator tick before DriveOrchestrator in each cycle. Narrative state changes slowly (only on synthesis, gated by D6 composite trigger), so the one-cycle lag on drives is semantically negligible. The scheduler owns this ordering — same pattern as Layer 1's "source ticks must precede drive tick."
**Trade-offs:** NarrativeSynthesiser (if triggered in this cycle) must complete before the compositor chain runs. LLM synthesis latency gates the tick cycle when synthesis is triggered.
**Sources:** ADR-0001 ordering constraint; Layer 2 spec ordering section; decision review R1-10
**Exploration:** quick (surfaced by decision review)
**Status:** captured

## D11: Layer 2 deferrals — cross-axis composition and governed priority escalation

**Choice:** Explicitly address two capabilities Layer 2 deferred to Layer 3:
(1) **Cross-axis goal composition** — compound goals like "learn about X by engaging with Y" (curiosity + affiliation). NarrativeOrchestrator naturally identifies cross-axis themes from episodes. When a DerivedTheme has high salience across multiple axes, the GoalProposalOrchestrator (or a future CrossAxisGoalMapper) can propose compound goals. Foundation type support: DerivedTheme carries per-axis modulation weights; when multiple weights are significant, the theme is cross-axis.
(2) **Governed priority escalation** — narrative context justifies elevating a drive-sourced goal from SECONDARY to PRIMARY. The NarrativeState provides the provenance: "this goal aligns with my core identity theme with salience > threshold." The escalation mechanism is a new GoalPriorityEscalationPolicy SPI in engine-api, checked by GoalFormationService before registration.
Both are designed at the type level in this spec; orchestrator implementation is deferred to children.
**Alternatives:**
- Re-defer entirely — push to a hypothetical Layer 4; leaves Layer 2's design debts unresolved
**Rationale:** These were explicit design debts assigned to Layer 3 by the Layer 2 spec (D4 and D6 in #136 decisions). The type system being designed in #142 can accommodate both without additional architectural complexity — DerivedTheme's per-axis weights naturally support cross-axis detection, and NarrativeState provides the provenance for priority escalation.
**Trade-offs:** GoalPriorityEscalationPolicy adds a new SPI to engine-api. Implementation requires engine changes (GoalFormationService checks the policy).
**Governance contract:** GoalPriorityEscalationPolicy implementations must enforce: (1) sustained alignment — escalation requires theme-drive alignment to persist across multiple synthesis cycles, not trigger on a single high-salience snapshot; (2) revocability — if NarrativeState changes and alignment drops, the escalation must be reversible (goal demoted back to SECONDARY); (3) configurable threshold source — per-deployment or per-agent threshold configuration. These are SPI contract requirements addressing Layer 2 D6's concern about ungoverned escalation pathways.
**Sources:** Layer 2 spec D4 (cross-axis composition deferred), Layer 2 spec D6 (priority escalation deferred); decision review R1-10, R1-12
**Exploration:** quick (surfaced by decision review) → revised after decision review
**Status:** revised

## D12: Agent discovery for collective goal formation

**Choice:** CollectiveGoalFormation receives the agent set as a `Supplier<List<String>>` constructor parameter scoped to a single tenant. The supplier is called on each tick(), providing the current agent population. The consumer (quarkmind scheduler, claudony) provides the supplier from its own agent registry. CollectiveGoalFormation does not discover agents itself.
**Alternatives:**
- Static `List<String>` — simple but stale immediately in dynamic populations (quarkmind agent creation/destruction, casehub-life onboarding); reconstructing CollectiveGoalFormation on every change destroys accumulated alignment analysis state
- DriveOrchestrator enumeration — add a method to list known agents from its internal ConcurrentHashMap; leaks internal state
- AgentRegistry injection — inject eidos AgentRegistry directly; couples blocks to eidos runtime
**Rationale:** The consumer already knows which agents are active in its tenant context. A supplier (rather than a static list) accommodates dynamic agent populations without reconstructing the component. Calling on each tick() ensures alignment analysis always sees the current agent set.
**Trade-offs:** The supplier is called on every tick; consumers should ensure it's cheap (return cached list, update on agent events).
**Depends on:** D8 (drive profile aggregation)
**Sources:** ConversationOrchestrator constructor pattern; AgentParticipant; decision review R1-04, R1-08, R1-12
**Exploration:** quick (surfaced by decision review) → revised after decision review
**Status:** revised

---

# #143 NarrativeOrchestrator + NarrativeSynthesiser — Implementation Decisions

## D13: Synthesis strategy — incremental merge

**Choice:** Incremental merge — new episodes are added to existing fragments, existing episodes preserved. Avoids re-deriving the full narrative each synthesis cycle.
**Alternatives:**
- Full replacement — each synthesis produces complete NarrativeState replacing all fragments; risks losing fragments the LLM forgets, expensive per call
**Rationale:** Reflections accumulate incrementally. Episodes represent distinct experiences and should persist once created. Full replacement forces the LLM to carry the entire narrative history every call, which is both expensive and unreliable.
**Trade-offs:** Requires capacity management (pruning) and theme re-derivation strategy.
**Sources:** MemoryHygieneOrchestrator pattern (incremental consolidation); D3 (split architecture)
**Exploration:** quick
**Status:** captured

## D14: Hybrid incremental — episodes additive, themes re-derived

**Choice:** Episodes are incremental (new ones added from new reflections). Themes are fully re-derived each synthesis — the LLM sees all existing episode summaries plus new reflections and produces the complete theme set.
**Alternatives:**
- Pure incremental (both episodes and themes additive) — LLM produces theme deltas; risks drift since salience can't be holistically recalibrated
**Rationale:** Themes are derived patterns across ALL episodes. A new crisis episode should shift the "crisis-helper" theme's salience globally. The LLM needs the full episode set to produce coherent salience scores. Cost is manageable — maxEpisodes is 50, maxThemes is 10.
**Trade-offs:** Theme section of the prompt grows with episode count. Mitigated by maxEpisodes cap and episode summaries (descriptions only, not full reflections).
**Depends on:** D13 (incremental merge)
**Sources:** DerivedTheme.salience semantics (spec D5); NarrativeConfig.maxEpisodes; DriveComposer modulation algebra (spec D4)
**Exploration:** quick
**Status:** captured

## D15: LLM response schema — flat JSON with index references

**Choice:** LLM returns flat JSON with `newEpisodes` array (description, emotionalValence, thematicTags, fromReflections as indices) and `themes` array (label, salience, thematicTags, axisWeights as DriveAxis→Double map). The synthesis prompt includes existing theme labels as anchoring context, enabling the LLM to reuse stable labels for continuing themes rather than producing semantically equivalent but lexically different labels. Synthesiser handles all ID generation (UUIDs for episodes), reflection index→sourceCaseId mapping, and theme supportingFragmentIds computation via thematic tag matching.
**Alternatives:**
- Rich JSON with explicit fragment references — LLM manages IDs directly; error-prone, complex prompts
- Semantic similarity matching for theme identity — use TokenJaccardDistance between new and existing labels; adds fuzzy matching complexity, threshold tuning, and still doesn't guarantee correct identity
**Rationale:** Keeps the LLM focused on semantic content. ID management is mechanical and better handled deterministically by the synthesiser. Simpler prompts produce more reliable outputs. Including existing theme labels in the prompt grounds the LLM's output, making label stability an emergent property of prompt design rather than a post-hoc matching problem.
**Trade-offs:** supportingFragmentIds for themes are computed by tag matching rather than explicit LLM assignment — may be less precise but more robust. Theme label stability depends on prompt quality — if the LLM ignores existing labels, theme continuity breaks. Mitigated by D14's full re-derivation: even if a label changes, the new theme captures the same pattern from the full episode set.
**Depends on:** D14 (hybrid incremental — themes are full set, episodes are new only)
**Sources:** InnerLifeOrchestrator JSON parsing pattern; AgentEvent text extraction (GE-20260801-0aee7e); decision review R1-11
**Exploration:** quick → revised after decision review
**Status:** revised

## D16: TokenJaccardDistance made public

**Choice:** Change TokenJaccardDistance from package-private to public. NarrativeSynthesiser (in social.narrative sub-package) needs it for novelty scoring in the synthesis gate.
**Alternatives:**
- Duplicate the utility in the narrative package — violates DRY
- Move to a shared utility package — over-engineering for a single class
**Rationale:** TokenJaccardDistance is a stateless utility with no encapsulation concern. Making it public is a one-word change. Java sub-packages don't inherit parent package access.
**Trade-offs:** None — the class has no mutable state and is already used by InnerLifeOrchestrator.
**Sources:** TokenJaccardDistance.java (social package); NarrativeSynthesisGate.noveltyThreshold (spec D6)
**Exploration:** quick
**Status:** captured

## D17: NarrativeSynthesiser as @ApplicationScoped CDI bean

**Choice:** @ApplicationScoped with @Inject constructor (AgentProvider, NarrativeStore, ReflectionQueryStore, NarrativeConfig) plus package-private test constructor accepting Clock. Follows DriveOrchestrator pattern.
**Alternatives:**
- Consumer-constructed (like VouchService) — no CDI; requires consumer to wire dependencies manually
**Rationale:** All constructor parameters are CDI-resolvable. The consumer calls synthesiseIfNeeded() at the correct tick ordering point but doesn't need to construct the object. Same injection pattern as DriveOrchestrator (CDI constructor + test constructor with Clock).
**Trade-offs:** Consumer must control tick ordering via scheduler, not via construction. This is the same responsibility as with DriveOrchestrator.
**Sources:** DriveOrchestrator CDI constructor pattern; InnerLifeOrchestrator @ApplicationScoped; spec D3 (split architecture)
**Exploration:** quick
**Status:** captured

## D18: Pruning and theme identity

**Choice:** After merge: (a) episodes exceeding maxEpisodes pruned by oldest `from` timestamp, (b) themes exceeding maxThemes pruned by lowest salience, (c) themes below themeSalienceFloor pruned regardless of count. Theme identity matched by label (case-insensitive) — re-derived themes with matching labels replace existing ones.
**Alternatives:**
- Episode pruning by emotional significance (keep highest |valence|) — more interesting but loses temporal continuity
- Theme identity by thematic tag overlap — fuzzy matching, fragile
**Rationale:** Temporal pruning preserves the most recent narrative, which is most relevant for current behavior. Label-based theme identity is simple and deterministic — the LLM controls theme labels, making them stable across synthesis cycles.
**Trade-offs:** Old but emotionally significant episodes may be lost. Acceptable because themes capture the enduring patterns — a pruned crisis episode still contributes through the "crisis-helper" theme it helped establish.
**Depends on:** D13 (incremental merge), D14 (themes re-derived)
**Sources:** NarrativeConfig (maxEpisodes, maxThemes, themeSalienceFloor); memory hygiene eviction pattern (RetentionScore)
**Exploration:** quick
**Status:** captured

## D19: NarrativeModulation per-axis clamping

**Choice:** NarrativeModulation.compute() clamps the computed modulation per axis to [-1, 1] after summing across all themes. The additive composition is preserved (multiple themes reinforcing the same axis accumulate), but the result is bounded.
**Alternatives:**
- Normalise by theme count — penalises agents with more themes; weakens well-supported modulation
- Normalise by total salience — similar penalty, non-intuitive behaviour when saliences are low
- Use max rather than sum — loses multi-theme composition; a single theme dominates regardless of supporting evidence from other themes
- No clamping (current code) — unbounded sum grows linearly with theme count; with maxThemes=10, modulation can reach 10× the single-theme maximum, overwhelming DriveComposer despite narrativeModulationStrength scaling
**Rationale:** The additive sum correctly models reinforcement — multiple themes pointing in the same direction should produce stronger modulation than one. But the raw sum must be bounded to prevent theme count from dominating the modulation algebra. Clamping at [-1, 1] mirrors the per-theme weight bounds (DerivedTheme.axisModulationWeights enforces [-1, 1] per weight) and preserves the semantics: modulation for each axis ranges from "fully dampen" (-1) to "fully amplify" (+1), regardless of how many themes contribute.
**Trade-offs:** With clamping, adding a 6th theme with the same axis weight may not increase modulation if the sum is already at the bound. This is correct — beyond a certain point, additional thematic reinforcement doesn't produce a qualitatively different behavioral effect.
**Surfaced by:** R1-02 (reviewer)
**Depends on:** D4, D5
**Sources:** NarrativeModulation.java; DerivedTheme.axisModulationWeights validation; DriveComposer.compose() narrativeModulation application
**Exploration:** surfaced-by-review
**Status:** captured

## D20: DerivedTheme carries typed DriveAxis modulation weights

**Choice:** DerivedTheme.axisModulationWeights is `Map<DriveAxis, Double>`, not `Map<String, Double>`. The narrative type system is intentionally coupled to the drive axis enum.
**Alternatives:**
- Generic `Map<String, Double>` — decouples narrative from drives at the cost of type safety; string keys enable misconfiguration ("AFILIATION" typo compiles but has no effect), and the DriveAxis mapping still happens somewhere (deferred to NarrativeModulation boundary)
- Modulation target interface — DerivedTheme carries weights for arbitrary modulation targets; over-engineered for the current four-axis model with no concrete second consumer
**Rationale:** Narrative identity exists TO modulate the drive system. This is not incidental coupling — it's the core purpose of DerivedTheme. DriveAxis is a sealed enum representing fundamental psychological drives; adding a fifth axis IS a major architectural change that should cascade through the entire drive algebra (new DriveSource, new modulation semantics, new goal mappers). Type safety catches misconfiguration at compile time. If group narratives modulate different systems in the future, GroupDerivedTheme can carry different weight maps without affecting the individual DerivedTheme.
**Trade-offs:** Adding a new DriveAxis requires re-synthesising themes. Acceptable — a new axis changes the entire drive algebra and themes need new weights for it regardless of how the weights are stored.
**Surfaced by:** R1-14 (reviewer)
**Sources:** DerivedTheme.java; NarrativeModulation.java; DriveComposer.compose()
**Exploration:** surfaced-by-review
**Status:** captured

## D21: Compositor gracefully degrades on synthesis failure

**Choice:** NarrativeOrchestrator reads from NarrativeStore without detecting synthesis failure. If the synthesiser fails (LLM timeout, parse error, store write failure), the compositor sees stale state and produces output from the most recent successful synthesis. No error signal propagates to the compositor.
**Alternatives:**
- Error signal propagation — NarrativeStore tracks synthesis attempts alongside state; compositor detects "attempted but failed" vs "not needed". Adds complexity to the store SPI for marginal benefit.
- NarrativeSynthesisTick forwarding — pass synthesis outcome to the compositor. Couples the compositor to the synthesiser lifecycle, violating the compositor pattern's decoupling.
**Rationale:** Consistent with the compositor pattern (ADR-0001). DriveOrchestrator also operates on stale source state without error detection — if MoodOrchestrator hasn't ticked, drives still compute from the most recent mood. Synthesis failures are transient — the next synthesis cycle (gated by D6) will attempt again. The composite gate's quiet period bypass (D6) ensures synthesis is retried after inactivity. Stale narrative state is semantically valid — it represents the agent's most recently established self-narrative, which remains relevant even if a newer synthesis failed.
**Trade-offs:** Persistent synthesis failure (e.g., ongoing LLM outage) produces indefinitely stale narrative state. Acceptable — the agent operates with a frozen self-narrative rather than failing entirely. Consumer logging of NarrativeSynthesisTick.Skipped(reason) provides operational visibility without coupling to the compositor.
**Surfaced by:** R1-15 (reviewer)
**Sources:** ADR-0001 (compositor pattern guarantees); NarrativeSynthesisTick sealed interface; NarrativeStore SPI
**Exploration:** surfaced-by-review
**Status:** captured
