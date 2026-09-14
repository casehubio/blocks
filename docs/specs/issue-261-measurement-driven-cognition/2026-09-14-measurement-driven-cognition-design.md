# Measurement-Driven Cognition Integration — Design Spec

## Goal

Iteratively wire neurocortex cognition into the historical encounter
showcase (#258), measuring the delta each stage adds over a pure-LLM
baseline. Evidence-based approach: capture full conversations and
cognitive state at each stage, compare quantitatively, produce a
research paper draft documenting the improvement.

Three deliverables:
1. **CognitionCore** — shared abstraction extracted from
   `SocialAvatarCognition` and `CognitionStack` (blocks main scope)
2. **CognitionSnapshot + CognitionMetrics** — observability
   infrastructure (blocks main scope, designed for neocortex #333
   compatibility)
3. **Staged showcase** — 7 stages of incremental cognition integration
   with per-stage results (agentic-yaml test scope)

## Architecture

### CognitionCore — shared orchestrator composition

`SocialAvatarCognition` (CDI, production) and `CognitionStack` (manual,
test) both solve the same problem: compose 12 orchestrators, record
interaction signals, build prompt sections, tick in correct order.
Currently they diverge — CognitionStack wires 5 orchestrators with
baseline drives; SocialAvatarCognition wires all 8+ with real signal
recording.

Extract the shared logic into `CognitionCore`:

```
blocks/src/main/java/.../agentic/social/
├── CognitionCore.java          # NEW — shared composition
├── prompt/
│   ├── SocialAvatarCognition.java  # MODIFIED — delegates to CognitionCore
│   └── SocialPromptAssembler.java  # unchanged
│   └── ...PromptSection.java       # unchanged

agentic-yaml/src/test/java/.../llm/
├── CognitionStack.java         # MODIFIED — delegates to CognitionCore
└── ...
```

```java
public class CognitionCore {

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final @Nullable StrategyLearningOrchestrator strategy;
    private final @Nullable NarrativeOrchestrator narrative;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable MemoryHygieneOrchestrator memoryHygiene;

    // Tick all orchestrators in dependency order:
    // 1. mood (standalone)
    // 2. memoryHygiene (standalone)
    // 3. narrative (standalone)
    // 4. drives (depends on mood, memoryHygiene, narrative for modulation)
    // 5. strategy (standalone tick)
    // 6. userModel per subject (subject-scoped)
    // 7. mentalModel per subject (subject-scoped)
    // 8. goals (depends on drives)
    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) { ... }

    // Record interaction signals from a conversation turn.
    // Mirrors SocialAvatarCognition.recordInteraction() — same signal
    // types dispatched to same orchestrators.
    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage,
                                   String response) { ... }

    // Build prompt sections from current orchestrator state
    public List<PromptSection> promptSections() { ... }

    // Snapshot current state for metrics/dump
    public CognitionSnapshot snapshot(String agentId, String tenantId,
                                       Set<String> subjectIds) { ... }

    // Orchestrator accessors for direct queries
    public MoodOrchestrator mood() { ... }
    public DriveOrchestrator drives() { ... }
    // ... etc
}
```

**Construction:** CognitionCore takes all orchestrators as constructor
parameters — no opinions about how they're created. `CognitionStack`
constructs them manually from `CompiledCognition`; `SocialAvatarCognition`
passes CDI-injected instances.

**Null handling:** Optional orchestrators are `@Nullable`. `tick()`,
`recordInteraction()`, and `promptSections()` skip null orchestrators.
This lets CognitionStack start minimal (Stage 0) and grow (Stages 1-4).

### CognitionSnapshot — observable cognitive state

A serializable snapshot of the full cognitive graph at a point in time.
Lives in blocks main scope — designed for future compatibility with
neocortex #333's `cognition_inspect` and `cognition_diff` tools.

```java
package io.casehub.blocks.agentic.social;

public record CognitionSnapshot(
    String agentId,
    String tenantId,
    int turnNumber,
    Instant capturedAt,
    @Nullable MoodState mood,
    @Nullable DriveProfile drives,
    Map<String, MentalModelSnapshot> mentalModels,   // keyed by subjectId
    Map<String, UserProfile> userProfiles,            // keyed by subjectId
    @Nullable StrategyProfile strategy,
    @Nullable NarrativeState narrative,
    List<DriveGoalProposal> goalProposals
) {

    // Diff against a previous snapshot — returns per-field deltas
    public CognitionDelta diffFrom(@Nullable CognitionSnapshot previous) { ... }

    // Serialize to JSON (for results files)
    public String toJson() { ... }
}
```

```java
public record CognitionDelta(
    @Nullable MoodDelta mood,
    @Nullable DriveDelta drives,
    Map<String, BdiDelta> mentalModelDeltas,     // per subject
    Map<String, ProfileDelta> userProfileDeltas,  // per subject
    @Nullable StrategyDelta strategy,
    @Nullable NarrativeDelta narrative,
    List<DriveGoalProposal> newGoals,
    List<DriveGoalProposal> removedGoals
) {
    public record MoodDelta(double pleasureDelta, double arousalDelta,
                            double dominanceDelta) {}
    public record DriveDelta(Map<DriveAxis, Double> intensityDeltas,
                             double compositeDelta,
                             @Nullable DriveAxis previousDominant,
                             @Nullable DriveAxis currentDominant) {}
    public record BdiDelta(List<AttributedState> newBeliefs,
                           List<AttributedState> newDesires,
                           List<AttributedState> newIntentions,
                           Map<String, Double> confidenceChanges) {}
    public record ProfileDelta(double familiarityDelta,
                               int interactionCountDelta,
                               @Nullable String previousStage,
                               @Nullable String currentStage) {}
    public record StrategyDelta(Map<String, Double> dimensionDeltas,
                                List<String> newGuidelines) {}
    public record NarrativeDelta(List<NarrativeFragment> newFragments,
                                 int episodeCountDelta,
                                 int themeCountDelta) {}
}
```

**neocortex #333 compatibility:** `CognitionSnapshot` maps to
`cognition_inspect` (current state), `CognitionDelta` maps to
`cognition_diff` (structured mutations). When #333 lands, these types
can serve as the backing model — the MCP tools query `CognitionCore`
via the same `snapshot()` method.

### CognitionMetrics — per-turn measurement

Captures what changed during a tick+record cycle. Built from snapshot
diffs, not from event callbacks — simple, non-intrusive.

```java
package io.casehub.blocks.agentic.social;

public record CognitionMetrics(
    int turnNumber,
    String agentId,
    int promptSectionsContributed,
    Map<String, String> promptSectionContent,  // section name → text
    CognitionDelta delta,
    CognitionSnapshot snapshotAfter
) {

    // Human-readable summary for console output
    public String summary() { ... }

    // Markdown table row for results files
    public String toMarkdownRow() { ... }
}
```

**Capture pattern in ConversationRunner:**
```java
var before = cognitionCore.snapshot(agentId, tenantId, subjectIds);
cognitionCore.tick(agentId, tenantId, descriptor, subjectIds);
cognitionCore.recordInteraction(agentId, tenantId, subjectId,
                                 lastMessage, response);
var after = cognitionCore.snapshot(agentId, tenantId, subjectIds);
var delta = after.diffFrom(before);

// Capture which prompt sections contributed
var sections = cognitionCore.promptSections();
var context = new PromptContext(agentId, tenantId, subjectId);
var sectionContent = new LinkedHashMap<String, String>();
for (var section : sections) {
    var text = section.contribute(context);
    if (text != null) sectionContent.put(section.getClass().getSimpleName(), text);
}

var metrics = new CognitionMetrics(turn, agentId,
    sectionContent.size(), sectionContent, delta, after);
```

### ConversationRunner changes

**Problem:** System prompt is fixed at session open. Cognition sections
in the system prompt never update. The LLM sees the same cognition
state every turn.

**Fix:** Switch from multi-turn `AgentSession` to single-turn
`AgentProvider.invoke()` queries. Each turn builds a fresh system prompt
(briefing + cognition sections + world) and user prompt (conversation
history + instruction). This matches production's `SocialPromptAssembler`
pattern where cognition goes in the system prompt.

```java
// System prompt: rebuilt each turn with fresh cognition state
private String buildSystemPrompt(AgentDescriptor speaker,
                                  AffordanceRenderer renderer,
                                  String otherAgent) {
    var sb = new StringBuilder();
    sb.append(speaker.briefing()).append("\n\n");

    // Cognition sections (dynamic per turn — matches SocialPromptAssembler)
    var context = new PromptContext(speaker.name(), tenantId, otherAgent);
    for (var section : cognitionCore.promptSections()) {
        var contribution = section.contribute(context);
        if (contribution != null && !contribution.isBlank()) {
            sb.append(contribution).append("\n\n");
        }
    }

    // World context
    if (world != null) { ... }
    sb.append("\n\nRespond in character. Keep responses to 2-3 paragraphs.");
    return sb.toString();
}

// User prompt: conversation history + instruction
private String buildUserPrompt(List<Turn> history, String currentAgent) {
    if (history.isEmpty()) {
        return "The conversation begins. Introduce yourself...";
    }
    var sb = new StringBuilder();
    // Include full conversation history (single-turn query, no session memory)
    for (var turn : history) {
        sb.append(turn.speakerName()).append(": ");
        sb.append(turn.dialogue()).append("\n\n");
    }
    sb.append("Respond in character.");
    return sb.toString();
}
```

**Turn lifecycle (each turn):**
1. Snapshot before
2. Tick all orchestrators (processes accumulated signals → updated state)
3. Build system prompt with fresh cognition sections (matches production)
4. Build user prompt with conversation history
5. Single-turn query via `AgentProvider.invoke()`
6. Record interaction signals (userMessage, response → orchestrators)
7. Snapshot after
8. Compute delta, capture metrics
9. Print turn output with metrics

### Stage-gated CognitionStack

`CognitionStack` grows through stages. Each stage adds orchestrators
and capabilities:

```java
public class CognitionStack {

    private final CognitionCore core;
    private final Stage stage;

    public enum Stage {
        BASELINE,       // Stage 0: no cognition sections in prompt
        SIGNALS,        // Stage 1: signal recording + prompt injection
        REAL_DRIVES,    // Stage 2: real drive sources
        NARRATIVE,      // Stage 3: narrative synthesis
        FULL            // Stage 4+: goals, full orchestration
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider,
                                       Stage stage) {
        // Build orchestrators based on stage
        var mood = new MoodOrchestrator(config.mood());

        // Stage 0-1: baseline drives
        // Stage 2+: real drive sources from orchestrators
        DriveOrchestrator drives = stage.ordinal() >= Stage.REAL_DRIVES.ordinal()
            ? buildRealDrives(config, mood, ...)
            : buildBaselineDrives(config, mood);

        // Stage 0-2: no narrative synthesis
        // Stage 3+: LLM-backed narrative
        NarrativeOrchestrator narrative = stage.ordinal() >= Stage.NARRATIVE.ordinal()
            ? buildNarrativeWithSynthesis(agentProvider, ...)
            : new NarrativeOrchestrator(new InMemoryNarrativeStore());

        // ... etc

        var core = new CognitionCore(mood, drives, ...);
        return new CognitionStack(core, stage);
    }

    // Delegates
    public CognitionCore core() { return core; }
    public void tick(...) { core.tick(...); }
    public List<PromptSection> promptSections() { return core.promptSections(); }
    public CognitionSnapshot snapshot(...) { return core.snapshot(...); }
}
```

### Results output

Each stage produces:

| File | Content |
|------|---------|
| `results/stage-N-conversation.md` | Full dialogue with per-turn metrics table |
| `results/stage-N-dump.json` | Final `CognitionSnapshot` as JSON |
| `results/stage-N-metrics.md` | Summary metrics table across all turns |

Format for `stage-N-conversation.md`:
```markdown
# Stage N: <stage name>

## Turn 1 — leonardo
[dialogue] "The water does not move the wheel by force alone..."

| Metric | Value |
|--------|-------|
| Sections contributed | 3 |
| Mood Δ | P+0.05, A+0.02, D-0.01 |
| Drive shift | CURIOSITY: 0.82 (+0.04) |
| Mental model beliefs | 2 new (nikola) |
| Narrative episodes | 0 |
| Goals proposed | 0 |

## Turn 2 — nikola
...

## Summary

| Turn | Agent | Sections | Mood Δ | Drive Δ | Beliefs | Episodes | Goals |
|------|-------|----------|--------|---------|---------|----------|-------|
| 1 | leonardo | 3 | +0.05 | +0.04 | 2 | 0 | 0 |
| 2 | nikola | 4 | +0.08 | +0.06 | 3 | 1 | 0 |
| ... | ... | ... | ... | ... | ... | ... | ... |
```

### ConversationEvaluator — A/B comparison (Stage 6)

```java
public class ConversationEvaluator {

    private final AgentProvider judge;

    public record EvaluationResult(
        Map<String, Double> dimensionScores,  // groundedness, adaptiveness, etc.
        String overallAssessment,
        String winner                          // "baseline" | "cognition" | "tie"
    ) {}

    // Scores a single conversation on quality dimensions
    public EvaluationResult evaluate(
        ConversationRunner.ConversationResult conversation,
        List<CognitionMetrics> metrics) { ... }

    // Compares two conversations head-to-head
    public ComparisonResult compare(
        ConversationRunner.ConversationResult baseline,
        ConversationRunner.ConversationResult withCognition,
        List<CognitionMetrics> cognitionMetrics) { ... }
}
```

Scoring dimensions:
- **Groundedness** — responses reference specific knowledge, not generic LLM prose
- **Adaptiveness** — dialogue evolves based on what was said (not just templated)
- **Character consistency** — speakers maintain distinct voices and perspectives
- **Depth** — conversation explores ideas in depth vs surface-level exchange
- **Memory utilisation** — later turns reference earlier statements or build on them

## Stages — detailed plan

### Stage 0: Baseline capture
- Run 4-turn conversation with `Stage.BASELINE` — cognition sections
  excluded from user prompt, no signal recording
- Establish CognitionMetrics framework (prints zeros — proves instrumentation)
- Establish CognitionSnapshot dump (exports empty graph — proves format)
- Save to `results/stage-0-*`
- **This is the control for all subsequent comparisons**

### Stage 1: Signal extraction + prompt injection
- Switch to `Stage.SIGNALS`
- Wire `CognitionCore.recordInteraction()` after each turn — dispatches:
  - `InteractionSignal.CustomSignal` → UserModelOrchestrator
  - `MentalStateSignal.VerbalCue` → MentalModelOrchestrator
  - `EngagementSignal.TurnOutcome` → StrategyLearningOrchestrator
- Inject cognition PromptSections into user prompt each turn
- **Measure:** which sections contribute non-null text, mood delta,
  beliefs formed per subject
- Save to `results/stage-1-*`
- Compare against Stage 0

### Stage 2: Real drive sources
- Switch to `Stage.REAL_DRIVES`
- Wire `CuriosityDrive` (from `MemoryHygieneOrchestrator.knowledgeGaps()`),
  `CompetenceDrive` (from `StrategyLearningOrchestrator.engagementTrend()`),
  `AffiliationDrive` (from `UserModelOrchestrator.activeProfiles()`),
  `AutonomyDrive` (from `MentalModelOrchestrator.activeSnapshots()`)
- Requires wiring `MemoryHygieneOrchestrator` (new in CognitionStack)
- **Measure:** drive shifts per turn, how drive state influences prompt
  content
- Save + compare against Stage 1

### Stage 3: Narrative synthesis
- Switch to `Stage.NARRATIVE`
- Wire `NarrativeContentSummariser` (LLM-backed via AgentProvider) into
  `NarrativeOrchestrator` — episodes form from conversation, themes derive
- Wire `NarrativeModulation` — narrative themes modulate drive intensities
- **Measure:** episodes formed, themes emerged, narrative-driven drive
  modulation, narrative content in prompts
- Save + compare against Stage 2

### Stage 4: Full orchestration + goal proposals
- Switch to `Stage.FULL`
- Wire `GoalProposalOrchestrator` with real mappers
  (`CuriosityGoalMapper`, `CompetenceGoalMapper`, `AffiliationGoalMapper`,
  `AutonomyGoalMapper`)
- Wire `GoalEscalationPolicy` (narrative-driven priority shifts)
- **Measure:** goals proposed, goals influencing dialogue, escalation events
- Save + compare against Stage 3

### Stage 5: Cognitive dump visualisation
- Per-turn `CognitionSnapshot` dumps (already available from metrics)
- Mermaid graph generation from snapshots:
  - Agent → beliefs → subject relationships
  - Theme → episode connections
  - Drive intensity bar charts per turn
- Side-by-side comparison: turn 1 vs turn 4 graph showing growth

### Stage 6: A/B comparison and research paper
- Run Stage 0 (baseline) and Stage 4 (full cognition) conversations
- `ConversationEvaluator` scores both on 5 dimensions
- Quantitative metrics comparison table (all stages)
- Research paper evidence package:
  - Hypothesis, method, results, discussion sections
  - Per-stage delta tables
  - Conversation excerpts showing cognition influence
  - Mermaid visualisations

## File layout (new and modified)

```
blocks/src/main/java/.../agentic/social/
├── CognitionCore.java              # NEW — shared orchestrator composition
├── CognitionSnapshot.java          # NEW — observable cognitive state
├── CognitionDelta.java             # NEW — structured state diff
├── CognitionMetrics.java           # NEW — per-turn measurement

agentic-yaml/src/test/java/.../llm/
├── CognitionStack.java             # MODIFIED — delegates to CognitionCore
├── ConversationRunner.java          # MODIFIED — user prompt injection, metrics
├── ConversationEvaluator.java       # NEW — A/B comparison judge
├── ResultsWriter.java               # NEW — markdown + JSON output
├── MermaidGenerator.java            # NEW — graph visualisation
├── HistoricalEncounterLlmTest.java  # MODIFIED — staged test methods

agentic-yaml/src/test/resources/
├── results/                         # NEW — stage output directory
│   ├── stage-0-conversation.md
│   ├── stage-0-dump.json
│   ├── stage-0-metrics.md
│   └── ...
```

## Dependencies

No new compile dependencies for blocks. CognitionCore uses existing
orchestrator types already on the compile classpath.

Test scope additions (under `-Pllm` profile in agentic-yaml):
- `com.google.code.gson:gson` — JSON serialization for CognitionSnapshot dumps
  (already in blocks' dependency tree via speech-sherpa)

## What this does NOT cover

- **SocialAvatarCognition refactor** — CognitionCore is built this branch
  and CognitionStack delegates to it. Refactoring SocialAvatarCognition
  to also delegate to CognitionCore is a separate follow-up — it requires
  adapting CDI `Instance<>` patterns and is not gated by this work.
- **neocortex #333 MCP tool integration** — CognitionSnapshot is designed
  for compatibility but does not implement the MCP tool surface. That
  belongs in the #333 epic.
- **Joint intention lifecycle** — parsed from `joint-intention.yaml` but
  not wired. No `JointIntention` integration in ConversationOrchestrator.
- **Persistent stores** — all orchestrator stores use in-memory
  implementations. Persistence is a consumer concern.

## References

- `CognitionStack.java` — existing test-scope orchestrator wiring
- `ConversationRunner.java` — existing conversation loop
- `SocialAvatarCognition.java` — production CDI orchestrator composition
- `SocialPromptAssembler.java` — prompt section composition pattern
- #258 spec — historical encounter showcase design
- #198 spec — avatar social cognition integration design
- neocortex #333 — cognitive observability epic (GraphDelta, cognition_inspect)
- `MoodOrchestrator`, `DriveOrchestrator`, `MentalModelOrchestrator`,
  `UserModelOrchestrator`, `StrategyLearningOrchestrator`,
  `NarrativeOrchestrator`, `GoalProposalOrchestrator`,
  `MemoryHygieneOrchestrator` — orchestrator query APIs
