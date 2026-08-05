# io.casehub.blocks.conversation.ConvergencePolicies

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

## Fields

### `TIEBREAKER` (`java.util.Comparator<io.casehub.blocks.conversation.ConvergenceSignal>`)

## Constructors

### `private ConvergencePolicies()`

## Methods

### `public static io.casehub.blocks.conversation.ConvergencePolicy commonGroundRatio(double consensusThreshold, double deadlockDisputeRatio)`

#### Parameters

- `consensusThreshold` (`double`)
- `deadlockDisputeRatio` (`double`)

### `public static io.casehub.blocks.conversation.ConvergencePolicy composite(io.casehub.blocks.conversation.ConvergencePolicy[] policies)`

#### Parameters

- `policies` (`io.casehub.blocks.conversation.ConvergencePolicy[]`)

### `private static int severity(io.casehub.blocks.conversation.ConvergenceState state)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConvergenceState`)

### `public static io.casehub.blocks.conversation.ConvergencePolicy structural(double similarityThreshold, int staleRounds)`

#### Parameters

- `similarityThreshold` (`double`)
- `staleRounds` (`int`)
