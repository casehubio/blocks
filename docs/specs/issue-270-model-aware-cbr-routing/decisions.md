## D1: Build signal provider now, engine recording as prerequisite

**Choice:** Create `ModelPreferenceSignalProvider` implementing `RoutingSignalProvider` in blocks. Reads `modelId` from `ExperiencePlanStep.parameters()`. Returns empty signals when no model data exists. Engine prerequisite: record `modelId` in `ResolutionStep.parameters()` — file as casehubio/engine issue.
**Alternatives:**
- Wait for engine recording to land first — avoids shipping code with no data source but blocks downstream consumer (fsitrading#46)
**Rationale:** The signal provider is independently testable with synthetic data, follows the existing pattern (PredecessorAnalyser, CoordinationSignalProvider), and activates automatically when the engine starts recording. No blocks change needed when data arrives.
**Trade-offs:** Signal provider returns empty until engine records modelId — documented, not a bug.
**Sources:** CbrAgentRoutingStrategy.java:131 (signal assembler integration), ExperiencePlanStep.java:27 (parameters map), CbrRetrievalService.java:386 (parameters pass-through)
**Exploration:** quick
**Status:** captured

## D2: Weighted success rate by (workerId, modelId) compound key

**Choice:** Score by `(workerId, modelId)` compound key using similarity-weighted success rates. Same approach as `ExperienceAnalyser.workerSuccessRates()` but grouped by compound key. Signal's `reason` carries the recommended modelId for the winning candidate.
**Alternatives:**
- Global model ranking independent of worker — simpler but loses per-agent model affinity (agent X may perform better on model Y while agent Z is model-agnostic)
**Rationale:** Model performance varies by agent and capability. A global model score misses the interaction between agent implementation and model capability.
**Trade-offs:** Needs more data to converge — compound key means sparser observations per bucket.
**Depends on:** D1 (signal provider approach)
**Sources:** ExperienceAnalyser.workerSuccessRates() (existing scoring pattern), CbrOutcomeWeights (weight reuse)
**Exploration:** quick
**Status:** captured

## D3: No cold-start logic — defer to engine model selection policy

**Choice:** Signal provider scores from evidence only. No exploration bonus, no trial period. When no model data exists for a candidate, the signal returns nothing — base CBR score and other signals determine selection. The engine's static model policy (eidos#172) handles initial assignment.
**Alternatives:**
- Exploration bonus for unseen models — adds state tracking and exploration/exploitation complexity
- Configurable trial period per new model — most complex, consumer-configured
**Rationale:** Exploration is a policy concern at the engine level, not a scoring concern. The signal provider's job is to report what the data says, not to drive experimentation.
**Trade-offs:** New models won't be preferred until they accumulate enough outcomes — acceptable since the static policy assigns them initially.
**Depends on:** D1 (signal provider approach)
**Sources:** eidos#172 (static model selection policy)
**Exploration:** quick
**Status:** captured
