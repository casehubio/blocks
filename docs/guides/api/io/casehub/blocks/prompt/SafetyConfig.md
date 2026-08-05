# io.casehub.blocks.prompt.SafetyConfig

**Package:** `io.casehub.blocks.prompt`

**Kind:** `record`

## Fields

### `circuitBreakerThreshold` (`int`)

### `enabled` (`boolean`)

### `maxExperimentAge` (`java.time.Duration`)

### `maxExperimentCycles` (`int`)

### `qualityFloor` (`double`)

## Record Components

### `circuitBreakerThreshold` (`int`)

### `enabled` (`boolean`)

### `maxExperimentAge` (`java.time.Duration`)

### `maxExperimentCycles` (`int`)

### `qualityFloor` (`double`)

## Constructors

### `public SafetyConfig(double qualityFloor, int maxExperimentCycles, java.time.Duration maxExperimentAge, int circuitBreakerThreshold, boolean enabled)`

#### Parameters

- `qualityFloor` (`double`)
- `maxExperimentCycles` (`int`)
- `maxExperimentAge` (`java.time.Duration`)
- `circuitBreakerThreshold` (`int`)
- `enabled` (`boolean`)

## Methods

### `public int circuitBreakerThreshold()`

### `public static io.casehub.blocks.prompt.SafetyConfig defaults()`

### `public boolean enabled()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Duration maxExperimentAge()`

### `public int maxExperimentCycles()`

### `public double qualityFloor()`

### `public final java.lang.String toString()`
