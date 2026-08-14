# ADR-0002: Channel Observer — Composition Over Context Enrichment

**Status:** Accepted
**Date:** 2026-08-14
**Issue:** casehubio/blocks#97

## Context

Execution drivers (OrchestratedDriver, ChoreographedDriver) need to observe inter-agent channel communication for supervisor termination/aggregation decisions. The `TerminationCondition<T>` SPI receives context `T` via `TerminationContext<T>`, but `T` is set once at `execute()` and never updated. Channel projection state doesn't reach the supervisor's decision SPIs.

## Decision

Use **composition** — `ChannelObserver<S>` as a standalone type that implements `EventSource` and holds projected state. `TerminationCondition` closes over the observer to read projected state. No changes to existing SPIs (`TerminationCondition`, `AggregationStrategy`) or drivers (`AbstractExecutionDriver`, `ChoreographedDriver`).

## Alternatives Considered

1. **Context update hook** — add `ContextUpdater<T>` to the driver loop so `T` carries projection state. Rejected: requires T mutation support, driver loop changes, more invasive.

2. **Observation as a driver phase** — add "observe" phase to the five-phase loop, enrich `TerminationContext`/`AggregationContext`. Rejected: broadest API change, couples driver to projection concept.

## Consequences

- The TerminationCondition reads from outside its TerminationContext (side-channel). Mitigated by event delivery ordering — observer updates BEFORE the DriverEvent enters the queue, so projected state is always current at evaluation time.
- No SPI or driver changes required. Zero regression risk.
- Natural completion of #19's event-driven model — observer IS an EventSource.
- For OrchestratedDriver (continuous loop), one-message lag is possible. Acceptable for convergence/consensus decisions.
