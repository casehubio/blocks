# io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `interface`

Configurable weights for case-level outcomes used by `PlanCompositionAnalyser` when
scoring workers based on the overall outcome of multi-step plans they participated in.

<p>Case-level outcomes (COMPLETED, FAULTED, CANCELLED) use a different vocabulary from
step-level outcomes (`io.casehub.api.spi.routing.RoutingOutcome`). String keys because
case-level outcomes are domain-dependent strings from `CaseOutcomeEvent.outcomeLabel()`.

<p>Domain repos override the `DefaultCbrCaseOutcomeWeights` `@DefaultBean` with
`@ApplicationScoped` to tune how different case outcomes influence plan-fit scoring.

## Methods

### `public abstract java.util.Map<java.lang.String,java.lang.Double> weights()`
