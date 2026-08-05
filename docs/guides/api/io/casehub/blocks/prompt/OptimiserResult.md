# io.casehub.blocks.prompt.OptimiserResult

**Package:** `io.casehub.blocks.prompt`

**Kind:** `record`

## Fields

### `estimatedQuality` (`double`)

### `examples` (`java.util.List<io.casehub.blocks.prompt.FewShotExample>`)

### `instructionDelta` (`java.lang.String`)

## Record Components

### `estimatedQuality` (`double`)

### `examples` (`java.util.List<io.casehub.blocks.prompt.FewShotExample>`)

### `instructionDelta` (`java.lang.String`)

## Constructors

### `public OptimiserResult(java.util.List<io.casehub.blocks.prompt.FewShotExample> examples, java.lang.String instructionDelta, double estimatedQuality)`

#### Parameters

- `examples` (`java.util.List<io.casehub.blocks.prompt.FewShotExample>`)
- `instructionDelta` (`java.lang.String`)
- `estimatedQuality` (`double`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public double estimatedQuality()`

### `public java.util.List<io.casehub.blocks.prompt.FewShotExample> examples()`

### `public final int hashCode()`

### `public java.lang.String instructionDelta()`

### `public final java.lang.String toString()`
