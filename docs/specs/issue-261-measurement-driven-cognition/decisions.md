## D1: CognitionCore shared abstraction

**Choice:** Extract a `CognitionCore` class that both `SocialAvatarCognition` (CDI) and `CognitionStack` (manual construction) delegate to. Signal recording, prompt section building, tick ordering — defined once. Measurement hooks go on CognitionCore.

**Alternatives:**
- Grow CognitionStack independently — faster to build but two diverging implementations of the same orchestrator wiring
- Use SocialAvatarCognition directly in tests — CDI `Instance<>` patterns are awkward without a container

**Rationale:** CognitionStack and SocialAvatarCognition solve the same problem (compose 12 orchestrators, record signals, build prompt sections) in different environments (test vs CDI). A shared core eliminates divergence and ensures measurement hooks are available in production too.

**Trade-offs:** Requires refactoring SocialAvatarCognition to delegate to CognitionCore. CDI-specific patterns (Instance<> for optional orchestrators) need adapter handling.

**Sources:** SocialAvatarCognition.java (production wiring), CognitionStack.java (test wiring), #198 spec (avatar social cognition design)

**Exploration:** quick
**Status:** captured

## D2: Snapshot-diff metrics

**Choice:** CognitionMetrics captures per-turn deltas via snapshot diffing. Take a snapshot of all observable state before and after each tick+record cycle. Diffs computed by comparing snapshots.

**Alternatives:**
- Event-based recording — more precise deltas but requires modifying orchestrator internals, intrusive to production code for a test-scope concern
- Hybrid (snapshot + prompt section decorator) — best of both but more complex

**Rationale:** Simple, no orchestrator changes needed, works with neocortex #333's cognition_diff model. When #333 arrives, snapshot-diff naturally maps to GraphDelta.

**Trade-offs:** Less precise than event-based — can't distinguish "changed then changed back" within a tick. Acceptable for per-turn granularity.

**Depends on:** D1 (CognitionCore exposes orchestrator query methods for snapshotting)

**Sources:** neocortex #333 (GraphDelta structure), orchestrator query APIs (currentMood, currentDrives, etc.)

**Exploration:** quick
**Status:** captured

## D3: Single branch, all stages

**Choice:** One branch, one spec, one plan. Stages 0-6 are batches within the plan. Results accumulate in `results/` directory.

**Alternatives:**
- Split into 2-3 issues — creates artificial boundaries between tightly coupled stages
- Per-stage child issues — heavy overhead for an exploratory research task

**Rationale:** Each stage's evidence informs the next. The iterative measurement approach is the point — splitting it fragments the narrative.

**Trade-offs:** Large branch. Mitigated by clear stage boundaries and incremental commits.

**Sources:** Issue #261 body (7-stage plan)

**Exploration:** quick
**Status:** captured

## D4: CognitionSnapshot in blocks main scope

**Choice:** A `CognitionSnapshot` record + `CognitionSnapshotBuilder` that queries all orchestrators and produces a serializable snapshot. Lives in blocks main scope (not test-only). Missing orchestrator query methods get added as needed.

**Alternatives:**
- Test scope only — simpler boundary but duplicates work when #333 arrives
- Built into CognitionCore — ties dump lifecycle to tick lifecycle, mixing concerns

**Rationale:** When neocortex #333 arrives, `CognitionSnapshot` can serve as the backing data model for `cognition_inspect` and `cognition_diff` MCP tools. Building it in main scope now means #333 consumes it rather than reinventing it.

**Trade-offs:** Adds a main-scope class driven primarily by test/research needs. Justified because the same introspection is needed in production for #333.

**Depends on:** D1 (CognitionCore provides the orchestrator access), D2 (snapshot-diff metrics consume CognitionSnapshot)

**Sources:** neocortex #333 (cognition_inspect, cognition_diff tools), orchestrator query APIs

**Exploration:** quick
**Status:** captured

## D5: Single-turn queries with system-prompt cognition injection

**Choice:** Switch ConversationRunner from multi-turn `AgentSession` to
single-turn `AgentProvider.invoke()` queries. Each turn builds a fresh
system prompt (briefing + cognition sections + world) and user prompt
(conversation history + instruction). Cognition sections go in the
system prompt — matching production's `SocialPromptAssembler` pattern.

**Alternatives:**
- User-prompt injection with multi-turn AgentSession — diverges from
  production's system-prompt pattern; LLMs treat user-prompt cognition
  as conversational input rather than behavioral framing, confounding
  the measurement
- Close and reopen AgentSession each turn — works but adds session
  lifecycle overhead for no benefit over single-turn queries
- Insert cognition as synthetic system message in conversation history —
  pollutes history with meta-information

**Rationale:** Production `SocialPromptAssembler` injects cognition into
the system prompt. The research should measure cognition through the same
injection pattern. Single-turn queries are simpler than session management
and match how `SpeechSession.handleStop()` works in production. For 4-8
turn conversations, the prompt caching benefit of multi-turn sessions is
negligible.

**Trade-offs:** Loses session-level conversation memory — the full
conversation history must be included in each turn's user prompt. For
short conversations (4-8 turns) this is manageable and actually makes
the prompt contents more inspectable for measurement.

**Depends on:** D1 (CognitionCore's promptSections() provides the section content)

**Sources:** SocialPromptAssembler.java (production pattern — appends to systemPrompt),
SpeechSession.java (single-turn query model), ConversationRunner.java (current implementation),
decision review R1-15/R1-16 (production divergence finding)

**Exploration:** quick
**Status:** revised (was: user-prompt injection; revised per decision review R1-15)

**Exploration:** quick
**Status:** captured

## D6: Automated LLM judge + manual paper

**Choice:** Build a `ConversationEvaluator` that takes two `ConversationResult` + `CognitionSnapshot` pairs and asks an LLM to score on dimensions (groundedness, adaptiveness, character consistency, depth). Quantitative metrics table computed from CognitionMetrics diffs. Research paper written manually using the evidence.

**Alternatives:**
- Fully automated paper generation — risks generic prose; evidence is the valuable part
- Manual comparison only — no automated judge, loses structured scoring

**Rationale:** The judge provides structured, repeatable scoring. The paper benefits from human analysis of what the evidence means.

**Trade-offs:** Judge quality depends on prompt quality. Multiple judge runs for statistical significance.

**Sources:** Issue #261 Stage 6 description

**Exploration:** quick
**Status:** captured
