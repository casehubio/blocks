# io.casehub.blocks.channel.ContextTracker

**Package:** `io.casehub.blocks.channel`

**Kind:** `class`

Tracks cumulative LLM context window usage for a channel session.
Thread-safe via atomic counters.

## Fields

### `agentReportedPercent` (`java.lang.Double`)

### `messageCount` (`java.util.concurrent.atomic.AtomicInteger`)

### `serverContributionChars` (`java.util.concurrent.atomic.AtomicLong`)

## Constructors

### `public ContextTracker()`

## Methods

### `public void addContribution(long chars)`

#### Parameters

- `chars` (`long`)

### `public void addInitialContribution(long chars)`

#### Parameters

- `chars` (`long`)

### `public void reportAgentUsage(double percent)`

#### Parameters

- `percent` (`double`)

### `public io.casehub.blocks.channel.ContextSnapshot snapshot(long windowSizeChars, double thresholdPercent)`

#### Parameters

- `windowSizeChars` (`long`)
- `thresholdPercent` (`double`)
