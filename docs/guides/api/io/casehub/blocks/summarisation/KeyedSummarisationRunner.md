# io.casehub.blocks.summarisation.KeyedSummarisationRunner

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `accumulator` (`io.casehub.blocks.summarisation.KeyedAccumulator<K,IN>`)

### `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)

### `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

### `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)

### `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)

## Constructors

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.blocks.summarisation.Compactor<IN> compactor, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.blocks.summarisation.Compactor<IN> compactor, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

## Methods

### `public void clear()`

### `public void collect(io.casehub.blocks.summarisation.LevelEvent<IN> event)`

#### Parameters

- `event` (`io.casehub.blocks.summarisation.LevelEvent<IN>`)

### `public int eventCount()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> flush()`

Unconditional drain — bypasses completion test and stale timeout.
Use at shutdown to capture all remaining buffered events.

### `public int groupCount()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> tick(long now)`

Drains completed/stale groups, applies compaction, and submits each to the summariser.
Synchronized — concurrent tick() calls are serialized. The hot path
(no groups ready) acquires and releases the lock without blocking.

#### Parameters

- `now` (`long`)
