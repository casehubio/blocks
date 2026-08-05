# io.casehub.blocks.channel.ContextSnapshot

**Package:** `io.casehub.blocks.channel`

**Kind:** `record`

## Fields

### `agentReportedPercent` (`java.lang.Double`)

### `effectivePercent` (`double`)

### `messageCount` (`int`)

### `serverContributionChars` (`long`)

### `thresholdExceeded` (`boolean`)

### `windowSizeChars` (`long`)

## Record Components

### `agentReportedPercent` (`java.lang.Double`)

### `effectivePercent` (`double`)

### `messageCount` (`int`)

### `serverContributionChars` (`long`)

### `thresholdExceeded` (`boolean`)

### `windowSizeChars` (`long`)

## Constructors

### `public ContextSnapshot(long serverContributionChars, long windowSizeChars, java.lang.Double agentReportedPercent, int messageCount, double effectivePercent, boolean thresholdExceeded)`

#### Parameters

- `serverContributionChars` (`long`)
- `windowSizeChars` (`long`)
- `agentReportedPercent` (`java.lang.Double`)
- `messageCount` (`int`)
- `effectivePercent` (`double`)
- `thresholdExceeded` (`boolean`)

## Methods

### `public java.lang.Double agentReportedPercent()`

### `public double effectivePercent()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int messageCount()`

### `public long serverContributionChars()`

### `public boolean thresholdExceeded()`

### `public final java.lang.String toString()`

### `public long windowSizeChars()`
