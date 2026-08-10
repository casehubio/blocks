# AI Routing Strategy Implementations — Design Spec

**Issue:** blocks#30
**Date:** 2026-07-04
**Status:** Approved

---

## Summary

Two new `AgentRoutingStrategy` implementations in casehub-blocks that provide AI-powered agent selection for the engine's routing pipeline. Both implement the engine-api SPI (landed via engine#634) and are selected by name via `StrategyResolver`.

Existing strategies stay where they are — `TrustWeightedAgentStrategy` in engine-ledger, `SemanticAgentRoutingStrategy` in engine-ai. Blocks adds strategies whose differentiating dependency is LLM reasoning or case-based reasoning — capabilities that have no natural home in existing engine submodules.

---

## Strategies

### `LlmAgentRoutingStrategy` (id: `"llm"`)

**Package:** `io.casehub.blocks.routing.agent`

**Pipeline:**
1. If `TrustCandidateClassifier` available → classify candidates via `classifier.classify()`:
   - Remove EXCLUDED (Phase 2B, Phase 3) — never reach the LLM
   - BORDERLINE candidates scored 0.0 — excluded from LLM selection, tracked for escalation
   - Bootstrap guard: if `bootstrapEscalationRequired` and only BOOTSTRAP candidates → return `EscalateToOversight(capabilityName, NO_QUALIFIED_AGENT)`
   - Strip BOOTSTRAP from scoring when guard is active (only reached if QUALIFIED exists)
2. Build prompt from eligible candidates' descriptors (name, briefing, capabilities, slot) + case context
   - Prompt building and response parsing shared with `LlmSelectedRouting` via `LlmRoutingSupport` utility
3. Offload to worker pool: `.emitOn(Infrastructure.getDefaultWorkerPool())` — `AgentProvider.invoke()` blocks the calling thread (starts subprocess, collects LLM response)
4. Call `AgentProvider.invoke()` with the prompt on the worker thread
5. Parse the LLM's selection, match to a candidate's `workerId`
6. Final decision: if `TrustCandidateClassifier` present → delegate to `classifier.decide()` (handles BORDERLINE_STALEMATE escalation). Otherwise → `Assigned(workerId)` or `Unresolvable` on parse failure / LLM error

**Return values:**
- `Assigned(workerId)` — LLM selected a qualified candidate
- `Unresolvable` — parse failure, LLM error, empty candidates, or all excluded with none borderline
- `EscalateToOversight(capabilityName, BORDERLINE_STALEMATE)` — all non-bootstrap candidates borderline (via `classifier.decide()`)
- `EscalateToOversight(capabilityName, NO_QUALIFIED_AGENT)` — bootstrap guard fired

**Dependencies:**
- `AgentProvider` from platform-agent-api (already provided in blocks)
- `TrustCandidateClassifier` from engine-ledger (new provided dep) — optional via `Instance<T>`
- `TrustScoreSource` from casehub-ledger-api (new provided dep) — optional via `Instance<T>`
- `TrustRoutingPolicyProvider` from engine-api (already compile dep) — optional via `Instance<T>`

### `CbrAgentRoutingStrategy` (id: `"cbr"`)

**Package:** `io.casehub.blocks.routing.agent`

**Prerequisite:** `AgentRoutingContext` must carry `tenantId` — required by `CbrQuery` constructor (throws NPE on null). No existing strategy needs tenantId; this is the first consumer. Tracked as engine-api follow-up. CBR strategy is blocked until this lands.

**Pipeline:**
1. If `TrustCandidateClassifier` available → classify, remove EXCLUDED. BORDERLINE/BOOTSTRAP handling identical to LLM strategy (classifier.decide() for escalation, bootstrap guard for NO_QUALIFIED_AGENT).
2. Build `CbrQuery`:
   - `tenantId`: from `AgentRoutingContext.tenantId()` (prerequisite)
   - `domain`: `new MemoryDomain(capabilityName)` — MemoryDomain is a record (`record MemoryDomain(String name)`), not an enum; direct construction
   - `caseType`: `"agent-routing"` (convention: scopes retrieval to routing-decision cases)
   - `features`: empty map by default; extractable from `caseContext` for feature-vector matching
   - `topK`: configurable via MP config (`casehub.blocks.cbr.top-k`, default 10)
   - `minSimilarity`: configurable via MP config (`casehub.blocks.cbr.min-similarity`, default 0.5)
   - `problem`: text summary from `caseContext` via JQ expression (follows SemanticAgentRoutingStrategy pattern)
3. Offload to worker pool: `.emitOn(Infrastructure.getDefaultWorkerPool())` — `CbrCaseMemoryStore.retrieveSimilar()` may block
4. Call `CbrCaseMemoryStore.retrieveSimilar(query, CbrCase.class)` on worker thread
5. Worker analysis based on `cbrType()` of returned cases:
   - **PlanBased** (primary path): extract `workerName()` from `PlanTrace` entries matching current `capabilityName` → tally success rates per worker → rank candidates. Only `PlanCbrCase` carries worker identity via `PlanTrace`.
   - **Textual**: no worker identity available (`TextualCbrCase` has problem/solution/outcome/confidence only). Use problem/solution similarity to match candidate capabilities against descriptor vocabulary — weaker signal, informs selection but cannot tally worker success rates.
   - **FeatureVector**: no worker identity unless features conventionally include a worker key. Use structural feature similarity for candidate matching; fall through if no match.
6. Cross-reference scored workers with candidate list → pick highest
7. Final decision via `classifier.decide()` when available (same escalation handling as LLM strategy)
8. Fallback: if `CbrCaseMemoryStore` unavailable AND `AgentGraphQuery` available → use `AgentGraphQuery.topAgentsByOutcome()`. **Caveat:** this method currently has zero implementations and zero references in the codebase — the fallback path is contingent on eidos-api receiving a working implementation. Until then, the strategy returns `Unresolvable` when CbrCaseMemoryStore is unavailable.
9. If both `CbrCaseMemoryStore` and `AgentGraphQuery` unavailable → `Unresolvable`

**Retain (lifecycle dependency):** This strategy handles Retrieve + Reuse. Retain (storing routing decisions as new CBR cases) is an engine-level lifecycle concern — the engine records routing outcomes after strategy execution. Without Retain, the CBR store starts empty and the strategy falls through to the AgentGraphQuery fallback (if available) or returns Unresolvable. Tracked as follow-up.

**Return values:** Same three outcomes as LLM strategy (Assigned, Unresolvable, EscalateToOversight).

**Dependencies:**
- `CbrCaseMemoryStore` from neocortex-memory-api (new provided dep) — optional via `Instance<T>`
- `AgentGraphQuery` from eidos-api (already compile dep) — optional via `Instance<T>`, used as fallback. Note: `topAgentsByOutcome()` currently has no exercised implementation; fallback path is contingent on eidos-api validation (follow-up)
- Trust deps same as LlmAgentRoutingStrategy — optional via `Instance<T>`

---

## Dependencies

**New provided-scope additions to blocks pom.xml:**

| Artifact | Scope | Used by |
|----------|-------|---------|
| `casehub-engine-ledger` | provided | Both strategies — TrustCandidateClassifier (optional via `Instance<T>`) |
| `casehub-ledger-api` | provided | Both strategies — TrustScoreSource (optional via `Instance<T>`) |
| `casehub-neocortex-memory-api` | provided | CbrAgentRoutingStrategy — CbrCaseMemoryStore |

**Existing deps used (no changes):**

| Artifact | Scope | Used by |
|----------|-------|---------|
| `casehub-platform-agent-api` | provided | LlmAgentRoutingStrategy — AgentProvider |
| `casehub-eidos-api` | compile | CbrAgentRoutingStrategy — AgentGraphQuery |
| `casehub-engine-api` | compile | Both — AgentRoutingStrategy SPI, AgentCandidate, AgentAssignment, AgentRoutingContext, TrustRoutingPolicyProvider |

---

## CDI Wiring

Both strategies are `@ApplicationScoped`. All optional dependencies injected via `Instance<T>` per the `optional-platform-spi-instance-injection` protocol.

**Activation behaviour:**

| Dependency | Unavailable behaviour |
|------------|----------------------|
| `AgentProvider` | LLM strategy inert — returns `Unresolvable` |
| `TrustCandidateClassifier` | Skip trust classification, all candidates pass through |
| `CbrCaseMemoryStore` | Fall back to `AgentGraphQuery` |
| `AgentGraphQuery` | If also no CBR store → `Unresolvable` |

**No `@Alternative @Priority`.** These strategies are name-selected via `StrategyResolver.resolve(AgentRoutingStrategy.class, "llm")` when a YAML case definition specifies `strategy: llm` or `strategy: cbr`. They never compete with priority-based strategies.

---

## Package Structure

```
src/main/java/io/casehub/blocks/routing/agent/
    LlmAgentRoutingStrategy.java
    CbrAgentRoutingStrategy.java
    LlmRoutingSupport.java          — shared prompt building + response parsing

src/test/java/io/casehub/blocks/routing/agent/
    LlmAgentRoutingStrategyTest.java
    CbrAgentRoutingStrategyTest.java
    LlmRoutingSupportTest.java
```

Existing packages:
- `io.casehub.blocks.agentic.routing` — blocks-internal orchestration model (RoutingStrategy<T>, LlmSelectedRouting, etc.)
  - `LlmSelectedRouting` will be refactored to delegate to `LlmRoutingSupport` (shared utility extraction, not parallel code)

---

## Testing

Plain JUnit 5 + Mockito — no CDI container in blocks tests.

### LlmAgentRoutingStrategyTest

- Mock `AgentProvider` — canned TextDelta events
- Trust classifier present: EXCLUDED candidates filtered before LLM
- Trust classifier present: BORDERLINE-only pool → `EscalateToOversight(BORDERLINE_STALEMATE)` via classifier.decide()
- Trust classifier present: bootstrap guard → `EscalateToOversight(NO_QUALIFIED_AGENT)` when only BOOTSTRAP
- Trust classifier absent: all candidates reach LLM, returns Assigned or Unresolvable only
- LLM selects valid candidate → `Assigned`
- LLM selects unknown name → `Unresolvable`
- LLM returns unparseable response → `Unresolvable`
- LLM invocation throws → `Unresolvable`
- Empty candidate list → `Unresolvable` (no LLM call)
- Prompt includes descriptor briefing, capabilities, case context
- Verify blocking work runs on worker pool (not IO thread)

### CbrAgentRoutingStrategyTest

- Mock `CbrCaseMemoryStore` — canned ScoredCbrCase results per type
- PlanCbrCase: workerName extracted from PlanTrace, step-level success tallying
- TextualCbrCase: problem similarity matching (no worker identity)
- FeatureVectorCbrCase: feature similarity matching
- CBR store unavailable + AgentGraphQuery available: fallback works
- Both sources unavailable → `Unresolvable`
- CBR returns workers not in candidate list → ignored
- Trust classifier present: EXCLUDED filtered, BORDERLINE escalation via classifier.decide()
- Trust classifier present: bootstrap guard fires correctly
- Empty candidate list → `Unresolvable`
- Verify blocking work runs on worker pool (not IO thread)

### LlmRoutingSupportTest

- Prompt building: includes candidate name, briefing, capabilities, slot, case context
- Response parsing: valid JSON → agent name + reason extracted
- Response parsing: malformed JSON → null
- Response parsing: "done" agent → handled correctly

---

## What This Does NOT Do

- Does not move `TrustWeightedAgentStrategy` from engine-ledger — trust scoring is engine-ledger's concern. Trust routing is already name-selectable via `StrategyResolver.resolve(AgentRoutingStrategy.class, "trust-weighted")`
- Does not move `SemanticAgentRoutingStrategy` from engine-ai — embeddings are engine-ai's concern
- Does not change the blocks-internal routing SPI (`RoutingStrategy<T>` interface) — the engine-api `AgentRoutingStrategy` SPI is architecturally distinct (different type hierarchy, context model, candidate model, result types)
- Does not duplicate LLM routing logic — shared prompt building and response parsing extracted into `LlmRoutingSupport`; `LlmSelectedRouting` refactored to use it
- Does not add `@Alternative @Priority` — name-based selection only
- Does not require blocks to become multi-module
- Does not implement CBR Retain — routing outcome recording is an engine-level lifecycle concern

---

## Consumer Assembly

| Want | Add to classpath |
|------|-----------------|
| LLM routing | blocks + engine-ledger + platform-agent-claude (or langchain4j) |
| CBR routing | blocks + engine-ledger + neocortex-memory |
| LLM routing (no trust) | blocks + platform-agent-claude |
| CBR routing (no trust, fallback only) | blocks (AgentGraphQuery via eidos-api) — contingent on eidos-api having a working implementation |

Note: `casehub-ledger-api` (needed for `TrustScoreSource`) is not listed separately — engine-ledger transitively depends on it via casehub-ledger. Consumers who add engine-ledger automatically get TrustScoreSource availability.

---

## Follow-up (not in scope)

- PLATFORM.md capability ownership update — engine#643
- Garden protocol for routing strategy naming convention — to be filed (separate from engine#643)
- Consumer repo migration (adding `id()` to existing classifiers) — engine#644
- casehub-work#287 — retrofit work-side strategies to NamedStrategy
- AgentRoutingContext tenantId extension — to be filed on engine-api (prerequisite for CBR strategy; CbrQuery mandates non-null tenantId)
- CBR Retain: engine records routing outcomes as CBR cases — to be filed on engine (without Retain, CBR store stays empty)
- LlmSelectedRouting refactoring to use LlmRoutingSupport — to be filed on blocks (consolidation, eliminates IO-thread bug in existing LlmSelectedRouting)
- Extract TrustCandidateClassifier interface to casehub-ledger-api — to be filed on engine (architectural improvement: blocks could depend on lightweight API instead of full engine-ledger)
- Validate AgentGraphQuery.topAgentsByOutcome() — to be filed on eidos (method currently has zero implementations and zero references; CBR fallback path is blocked until validated)
