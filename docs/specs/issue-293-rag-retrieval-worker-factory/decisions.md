# Decisions — #293 RagRetrievalWorkerFactory

## D1: Module placement

**Choice:** `blocks` module with `casehub-neocortex-rag-api` as `<scope>provided</scope>`
**Alternatives:**
- New sub-module (`rag-worker`) — isolates dependency but creates module for ~2-3 files; maintenance cost exceeds benefit
- `engine-adapter` module — wrong concern; engine-adapter is deployment/CDI integration, not reusable factories
**Rationale:** Follows the established pattern for cross-domain optional deps in blocks (`neocortex-memory-api`, `platform-agent-api`, `engine-ledger` are all provided). No transitive pollution. Easy to split later if the surface area grows.
**Trade-offs:** Consumers must explicitly declare `rag-api` dependency. NoClassDefFoundError at runtime if they forget — standard provided-scope contract.
**Sources:** blocks pom.xml provided-scope precedent, CLAUDE.md Blocks Scope Criteria (criterion 3: foundational platform integration)
**Exploration:** deep-analysis
**Status:** captured

## D2: Factory API shape

**Choice:** Minimal static factory with two overloads (with/without maxResults), default 10. All parameters required — no builder, no config record.
**Alternatives:**
- Builder pattern — adds ceremony for all-required parameters; warranted later if optional config grows
- Config record + factory method — wrapping 3 fields doesn't earn its keep at this scale
**Rationale:** Matches the SOC reference pattern. All parameters are required, so a flat method signature is the simplest correct API. Default overload covers the common case (maxResults=10).
**Trade-offs:** If optional configuration grows (description, executionPolicy, result transformer), a builder will be needed — but that's a future concern.
**Sources:** SOC RuleRagRetrievalWorker.java, Worker.builder() API
**Exploration:** quick
**Status:** captured
