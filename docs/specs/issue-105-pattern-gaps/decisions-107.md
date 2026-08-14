# Decisions — #107 Normative Conflict Resolution

## D1: Build vs document

**Choice:** Build ConflictResolutionStrategy SPI speculatively
**Alternatives:**
- Document gap analysis + defer — no concrete consumer yet
- File engine issue + build blocks SPI — split approach
**Rationale:** User wants SPIs and implementations in place now, ready for engine integration.
**Trade-offs:** No consumer to validate the API against. May need revision when engine integrates.
**Exploration:** quick
**Status:** captured

## D2: Norm source

**Choice:** ActionRiskClassifier decisions (wrapped in generic NormDecision<T>)
**Alternatives:**
- Broader rule sources — too abstract without concrete use cases
- Engine binding outputs — requires engine context not in blocks
**Rationale:** Risk classifiers are the existing norm mechanism. NormDecision<T> wraps any decision type.
**Exploration:** quick
**Status:** captured

## D3: Package

**Choice:** New `io.casehub.blocks.normative`
**Alternatives:**
- `io.casehub.blocks.oversight` (create it) — oversight is the primary consumer but the SPI is generic
**Rationale:** Generic norm conflict resolution is broader than oversight. Clean boundary.
**Exploration:** quick
**Status:** captured

## D4: Abstraction level

**Choice:** Generic `NormDecision<T>` and `ConflictResolutionStrategy<T>`
**Alternatives:**
- Tight to RiskDecision — simpler but locked to oversight
**Rationale:** Works for RiskDecision today, extensible to other decision types.
**Exploration:** quick
**Status:** captured

## D5: Resolution output

**Choice:** `NormResolution<T>` with winner, overridden list, reason, and ResolutionMethod enum
**Alternatives:**
- Minimal (just the winner) — no audit trail
**Rationale:** Audit trail for what was overridden and why, without coupling to specific audit infrastructure.
**Exploration:** quick
**Status:** captured
