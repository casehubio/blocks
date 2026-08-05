# io.casehub.blocks.routing.agent.CoordinationOutcomeWeights

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `interface`

SPI for case-level outcome weights used by `CoordinationSignalProvider`.
Returns `Map<String, Double>` mapping case outcome labels to score weights.
Domain repos override `DefaultCoordinationOutcomeWeights` with `@ApplicationScoped`.

## Methods

### `public abstract java.util.Map<java.lang.String,java.lang.Double> weights()`
