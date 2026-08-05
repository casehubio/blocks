# io.casehub.blocks.prompt.VariantSelector

**Package:** `io.casehub.blocks.prompt`

**Kind:** `class`

## Fields

### `circuitBreakerThreshold` (`int`)

### `consecutiveFailures` (`java.util.Map<java.lang.String,java.util.concurrent.atomic.AtomicInteger>`)

### `experimentRatio` (`double`)

## Constructors

### `public VariantSelector(double experimentRatio, int circuitBreakerThreshold)`

#### Parameters

- `experimentRatio` (`double`)
- `circuitBreakerThreshold` (`int`)

## Methods

### `private boolean isCircuitOpen(java.lang.String capabilityName)`

#### Parameters

- `capabilityName` (`java.lang.String`)

### `public void recordOutcome(java.lang.String capabilityName, boolean success)`

#### Parameters

- `capabilityName` (`java.lang.String`)
- `success` (`boolean`)

### `public java.lang.String selectSlot(java.util.UUID caseId, java.lang.String capabilityName)`

#### Parameters

- `caseId` (`java.util.UUID`)
- `capabilityName` (`java.lang.String`)
