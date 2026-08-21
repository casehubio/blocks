# ADR-0001: DriveOrchestrator uses compositor pattern (tick without record)

**Status:** Accepted
**Date:** 2026-08-21
**Issue:** #129

## Context

All six social cognition orchestrators follow the `record()` + `tick()` pattern:
signals are pushed via `record()`, accumulated in per-agent state, and processed
on `tick()`. DriveOrchestrator reads derived state from these orchestrators rather
than accumulating raw signals.

## Decision

DriveOrchestrator uses `tick()` + `currentDrives()` without `record()`. It is a
compositor — it pulls from source orchestrators' public accessors, applies
modulation, and caches the result. The caller is responsible for ensuring source
orchestrators are ticked before DriveOrchestrator.

## Consequences

- Breaks the universal `record()` + `tick()` contract established by the six
  social cognition orchestrators
- No raw signal queue — drive state is fully derivable from source state
- No persistence needed (in-memory only) — recomputable on restart
- Ordering constraint: source ticks must precede drive tick
- Future Layer 3 (narrative feedback) may re-introduce a signal path if narrative
  events need to influence drive intensity directly rather than through source
  orchestrators

## Alternatives Considered

- **Add a vestigial `record()`**: Would conform to the pattern but accumulate
  nothing — misleading API surface
- **Event-bus subscription**: Source orchestrators publish tick events; drives
  subscribe. Adds infrastructure overhead for 4 data sources
- **Pure accessor (no tick)**: Recompute on every call. No lifecycle control,
  no caching, no change detection
