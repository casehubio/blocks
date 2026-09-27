# Decisions — blocks#296 Goal-Aware Cognitive Loop

## D1: Unified goal model for LLM prompt

**Choice:** MindMap cognitive goals and drive-based goals merge into a single priority-ranked list for the LLM
**Alternatives:**
- Dual streams — two separate prompt sections, LLM integrates. Preserves provenance ("From your drives:" vs "From your understanding:") and avoids the normalization problem. Simpler, but LLM gets no unified ranking signal.
- MindMap-primary — drives feed INTO MindMap via GoalRecognitionPhase, LLM only sees MindMap. Architecturally cleaner but requires drive-to-MindMap bridging that doesn't exist.
**Rationale:** The LLM needs one goal picture ranked by priority, not two unrelated lists it must mentally merge. Both sources carry priority signals (drive intensity, MindMap composite priority) that can be normalized into a common ranking.
**Normalization function:** Both signals are [0,1]. Drive intensity measures internal motivational pressure; MindMap priority measures external goal-state assessment (urgency × feasibility × importance). Normalize via weighted sum: `composite = α × drive_intensity + (1-α) × mindmap_priority`, where α is configurable per agent via CognitiveDefaults (default 0.5). Goals with only one source use that source's value directly. The weight controls whether the agent is more drive-responsive or situation-responsive.
**Dedup strategy:** Goals link via `eidos-goal-name` property on MindMap nodes ↔ `DriveGoalProposal.goalName()`. When both exist, the cognitive goal's state (priority, urgency, affect) is authoritative; the drive goal's metadata (axis, trigger) is preserved as provenance. Unlinked goals from either source participate without dedup.
**Trade-offs:** GoalPromptSection becomes more complex — must merge, dedup, and rank two heterogeneous sources. The normalization weight α is a tuning parameter that needs calibration per agent type.
**Sources:** GoalPromptSection.java, GoalProposalOrchestrator.java, GoalPrioritizationPhase.java
**Exploration:** quick
**Review findings:** R1-02 (normalization specified), R1-03 (acknowledged but unified model chosen for ranking signal), R1-04 (dedup strategy specified)
**Status:** revised

## D2: CognitiveGoalOrchestrator via CognitionTickParticipant + dedicated prompt section

**Choice:** CognitiveGoalOrchestrator registered as a CognitionTickParticipant at TERMINAL phase. A new CognitiveGoalPromptSection takes CognitiveGoalOrchestrator directly as a constructor dependency. GoalPromptSection is extended to merge both sources when both are present.
**Alternatives:**
- New CognitionCore constructor field — adds 14th nullable parameter to an already-telescoping constructor. Deepens the god-object anti-pattern.
- Extend GoalProposalOrchestrator — mixes two distinct responsibility domains.
- Delegation (wrap GoalProposalOrchestrator) — couples the two orchestrators.
**Rationale:** CognitionCore.addParticipant(TERMINAL, participant) was designed for exactly this extension pattern. GoalPromptSection already takes GoalProposalOrchestrator directly (not through CognitionCore) — the same pattern works for CognitiveGoalOrchestrator. This avoids deepening CognitionCore's constructor telescope (13→14 params) while preserving clean separation of tick semantics.
**Trade-offs:** CognitiveGoalOrchestrator must be registered externally (in SocialAvatarCognition or CognitionCompiler wiring), not auto-wired via CognitionCore's constructor. This is the same pattern used for any custom participant — it's explicit but requires wiring at the composition root.
**Depends on:** D1 (merged prompt rendering)
**Sources:** CognitionCore.java (addParticipant, CognitionTickParticipant, CognitionPhase.TERMINAL), GoalPromptSection.java, SocialAvatarCognition.java
**Exploration:** deep-analysis
**Review findings:** R1-06 (CognitionTickParticipant re-evaluated and adopted), R1-23 (god-object trajectory addressed)
**Status:** revised

## D3: Surfacing tracking via ExperienceEvent + consolidation aggregation

**Choice:** When CognitiveGoalOrchestrator surfaces goals to the LLM, it records ExperienceEvent.action() with attributes event-type=goal-surfaced and subject=goal-node-id (via ExperienceAttributeKeys). A new SurfacingAggregationPhase in neocortex's consolidation pipeline aggregates these into goal node properties (surfaced-count, first-surfaced-at, last-surfaced-at). GoalAffectPhase reads the aggregated properties to compute worry.
**Alternatives:**
- Dedicated GoalSurfacingTracker SPI — explicit, testable, but adds a new SPI when ExperienceEvent already carries typed observations via attribute keys.
- MindMap property write from blocks — simplest, but blocks writing MindMap state crosses the neocortex ownership boundary.
- RetrievalAccessTracker pattern — designed for high-frequency access recording, but in-memory only (lost on restart) and doesn't distinguish surfacing from other access.
**Rationale:** Neocortex needs to "remember what it surfaced and when." Surfacing events use the ExperienceEvent path — ExperienceEvent types and ExperienceAttributeKeys are defined in neocortex-memory-api (which blocks already depends on as provided scope). Consolidation aggregates (existing pattern — see AccessFrequencyPhase, ExperienceConsolidationPhase). Node properties hold derived state (existing pattern).
**New cross-module integration:** This establishes blocks as a NEW consumer of ExperienceEvent/ExperienceRecorder. Blocks already depends on neocortex-memory-api (provided scope) but has not previously recorded ExperienceEvents. The dependency direction is correct (blocks → neocortex-api), but this is a new integration point, not an existing pattern in blocks. CognitiveGoalOrchestrator will need an ExperienceRecorder injected (via CDI Instance<> for graceful degradation when memory module is absent).
**Surfacing event structure:** `ExperienceAttributeKeys.EVENT_TYPE` and `SUBJECT` are RESERVED keys — `ExperienceEvents.toMemoryInput()` sets EVENT_TYPE to the event type name ("action") and throws `IllegalArgumentException` if metadata contains a reserved key. Surfacing events use custom metadata keys instead: `"cognitive-event"="goal-surfaced"` and `"goal-node-id"=goalNodeId`. SurfacingAggregationPhase filters by `cognitive-event="goal-surfaced"` when scanning experiences. Construction via `new Action(...)` (not static factory — ExperienceEvent has none).
**Volume:** With 5 active goals surfaced per tick and ticks per conversation turn, expect ~50-100 surfacing events per agent per day (not per minute — ticks happen per interaction, not on a timer). This is within the experience stream's designed capacity.
**Trade-offs:** Consolidation delay means worry updates aren't instant — but worry escalates over days, not seconds, so periodic consolidation is appropriate.
**Sources:** ExperienceEvent (neocortex-memory-api), ExperienceAttributeKeys (neocortex-memory-api), AccessFrequencyPhase (neocortex), ExperienceConsolidationPhase (neocortex)
**Exploration:** deep-analysis
**Review findings:** R1-08 (factual correction — ExperienceRecorder is in neocortex, not blocks; new integration acknowledged), R1-09 (attribute-based fields clarified), R1-10 (volume quantified), R2-02 (static factory corrected to constructor), R2-03 (reserved key collision fixed)
**Status:** revised

## D4: Adopt OCC + ALMA as the emotion model

**Choice:** Adopt the OCC taxonomy (Ortony, Clore, Collins 1988) for emotion types, ALMA (Gebhard 2005) for OCC→PAD mapping, and Scherer's SECs for the appraisal process. Replace raw PAD writes on goal nodes with typed CognitiveEmotion records that carry both OCC type and derived PAD projection.
**Scope for #296:** The full EmotionType enum is defined (all 22 types) but only the **prospect-based subset** is implemented: Hope, Fear, Satisfaction, Disappointment, Relief, Fears-confirmed. Plus Pity (fortunes-of-others, for empathic concern). Well-being types Joy and Distress are trivially mapped. Agent-based (Pride, Shame, Admiration, Reproach), object-based (Love, Hate), and compound (Anger, Gratitude, Remorse, Gratification) require action attribution and entity attitude infrastructure that is out of scope for #296.
**Type placement:** CognitiveEmotion record and EmotionType enum live in **neocortex cognitive-api** — the zero-deps shared module. This follows the existing dependency direction (blocks depends on neocortex-api modules). GoalAffectPhase in neocortex-mindmap-intelligence consumes these types directly.
**GoalAffectPhase impact:** The existing GoalAffectPhase (~40 lines, writes raw PAD via status switch) is replaced by an OCC appraisal → CognitiveEmotion → PAD projection pipeline. Consumers of goal node PAD properties (GoalPrioritizationPhase, CuriositySignalGenerator with affect dampening, AffectTrajectoryDecorator) continue reading PAD from node properties — the PAD values are still written, but now derived from OCC emotions rather than direct computation.
**Alternatives:**
- Custom taxonomy — reinvents 40 years of cognitive science.
- PAD-only — loses qualitative emotion distinction.
- Plutchik's wheel — less computationally tractable, no formal appraisal variables.
**Rationale:** OCC is the standard computational emotion framework, goal-oriented by design. The 22 types are formally specified (Steunebrink et al. 2009). ALMA provides empirically grounded OCC→PAD mappings.
**Trade-offs:** More complex than raw PAD — requires an appraisal layer. But "Fear about the birthday" is richer than "arousal=0.6, pleasure=-0.4".
**Sources:**
- [The OCC Model Revisited (Steunebrink et al. 2009)](https://people.idsia.ch/~steunebrink/Publications/KI09_OCC_revisited.pdf)
- [ALMA — A Layered Model of Affect (Gebhard 2005)](https://alma.dfki.de/papers/aamas05.pdf)
- [Component Process Model (Scherer 2001)](https://psu.pb.unizin.org/psych425/chapter/component-process-model-cpm/)
- [PAD ↔ OCC Mapping (Limbrecht et al.)](https://www.researchgate.net/publication/265439455_Pleasure_Arousal_Dominance_Mehrabian_and_Russell_revisited)
**Exploration:** deep-analysis
**Review findings:** R1-12 (scope clarified), R1-13 (GoalAffectPhase impact addressed), R1-22 (type placement specified)
**Status:** revised

## D5: Personality-modulated calibration via existing MoodBaseline

**Choice:** Base OCC emotion intensity from appraisal variables (desirability=priority, likelihood=feasibility, proximity=dynamic urgency, coping potential=agency). Personality modulation via the existing MoodBaseline (already personality-derived from eidos JPAF disposition axes via CognitiveDerivationEngine.deriveMoodBaseline()). The MoodBaseline PAD values serve as the emotion calibration anchor — agents with higher arousal baselines have lower thresholds for arousal-heavy emotions (Fear, Anger), agents with higher pleasure baselines have higher thresholds for displeasure-heavy emotions (Distress, Disappointment).
**Alternatives:**
- Fixed intensity — same emotional intensity for all agents. Flat, personality-less.
- OCEAN intermediate layer — introduce Big Five traits as an intermediate step between JPAF and PAD. Adds complexity without benefit since CognitiveDerivationEngine already maps directly from disposition axes to PAD baseline.
- Manual per-agent tuning — hand-calibrate emotion thresholds per agent profile. Doesn't scale.
**Rationale:** CognitiveDerivationEngine.deriveMoodBaseline() already maps JPAF disposition axes directly to MoodBaseline(pleasure, arousal, dominance). This IS the personality-derived PAD baseline. No OCEAN intermediate step is needed — Mehrabian's OCEAN→PAD equations are useful reference but our existing direct mapping achieves the same purpose. The Limbrecht et al. finding (Arousal and Dominance are personality-dependent) validates using a personality-derived baseline rather than fixed values.
**Calibration mechanism:** For each OCC emotion, the intensity threshold is modulated by the agent's MoodBaseline distance from the ALMA table's PAD coordinates for that emotion. An agent whose baseline is already close to the Fear PAD region (low P, high A, low D) has a lower threshold for Fear — it takes less to trigger it.
**Trade-offs:** Requires MoodBaseline to be set (via CognitiveDefaults or explicit config). Agents without MoodBaseline fall back to neutral (0,0,0) — no modulation.
**Depends on:** D4 (OCC emotion types exist to be calibrated)
**Sources:** CognitiveDerivationEngine.java (deriveMoodBaseline), MoodBaseline.java, CognitiveDefaults.java
**Exploration:** quick
**Review findings:** R1-15 (OCEAN intermediate removed — use existing direct mapping), R1-16 (PersonalityWeights reference removed — it's about memory domains, not emotion), R1-25 (infrastructure claims corrected)
**Status:** revised

## D6: GoalAppraisal SPI with heuristic implementation

**Choice:** Define a GoalAppraisal SPI. #296 delivers HeuristicGoalAppraisal. Future calibration via personality priors (JPAF-derived appraisal weights) and scenario-based calibration (per-agent interactive questionnaire) — not ONNX.
**GoalAppraisal SPI (binding interface for #296):**
```java
// In neocortex cognitive-api or mindmap-api
@FunctionalInterface
public interface GoalAppraisal {
    List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context);
}

public record AppraisalContext(
    String tenantId,
    PadProjection moodBaseline,    // personality-derived calibration
    int surfacingCount,
    Instant lastProgressAt,
    Map<String, Double> relationshipScores
) {}
```
**Why not ONNX:** Available datasets (ISEAR, GoEmotions, EmoBank) map text → emotion, but goal appraisal input is structured numeric features (priority, urgency, feasibility, surfacing count). No dataset maps these features to OCC emotions. Synthetic training data just learns the heuristic rules. Personalized calibration (personality priors + scenario questionnaire) is more practical and produces better results than training on generic cross-cultural averages.
**Future calibration path (not #296):**
1. Personality priors — derive appraisal weights from JPAF type (J→deadline-sensitive, Fe→empathy-amplified, Ni→early anticipation)
2. Scenario calibration — 15-20 interactive goal scenarios per agent, stored in cognitive profile YAML
3. Interaction refinement — bounded drift from calibrated baseline via real feedback
**Alternatives:**
- Inline appraisal in CognitiveGoalOrchestrator — works for #296 but prevents clean swap.
- ONNX model — impractical for structured-feature input, no suitable training data.
**Rationale:** The SPI ensures the heuristic implementation is replaceable. Heuristic rules are transparent, testable, and explainable ("Fear is high because you're a Judger with high Fe"). Calibration path fits existing architecture (CognitiveDefaults, CognitiveProfileWatcher, PersonalityEvolutionOrchestrator).
**Trade-offs:** Heuristic rules may not produce perfectly calibrated responses initially but are iterable and will improve with personality-prior and scenario calibration phases.
**Depends on:** D4 (CognitiveEmotion types), D5 (MoodBaseline as calibration input)
**Sources:** Research doc §6.3, CognitiveDerivationEngine.java, CognitiveProfileWatcher.java
**Review findings:** R1-18 (GoalAppraisal SPI specified), R1-26 (dependency on D4 explicit)
**Status:** revised
