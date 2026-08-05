# io.casehub.blocks.routing.agent.DispositionProfile

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `record`

## Fields

### `desired` (`java.util.Map<DispositionAxis,java.lang.String>`)

### `weights` (`java.util.Map<DispositionAxis,java.lang.Double>`)

## Record Components

### `desired` (`java.util.Map<DispositionAxis,java.lang.String>`)

### `weights` (`java.util.Map<DispositionAxis,java.lang.Double>`)

## Constructors

### `public DispositionProfile(java.util.Map<DispositionAxis,java.lang.String> desired)`

#### Parameters

- `desired` (`java.util.Map<DispositionAxis,java.lang.String>`)

### `public DispositionProfile(java.util.Map<DispositionAxis,java.lang.String> desired, java.util.Map<DispositionAxis,java.lang.Double> weights)`

#### Parameters

- `desired` (`java.util.Map<DispositionAxis,java.lang.String>`)
- `weights` (`java.util.Map<DispositionAxis,java.lang.Double>`)

## Methods

### `public java.util.Map<DispositionAxis,java.lang.String> desired()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public double weight(DispositionAxis axis)`

#### Parameters

- `axis` (`DispositionAxis`)

### `public java.util.Map<DispositionAxis,java.lang.Double> weights()`
