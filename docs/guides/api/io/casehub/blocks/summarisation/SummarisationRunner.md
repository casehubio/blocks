# io.casehub.blocks.summarisation.SummarisationRunner

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `accumulator` (`io.casehub.blocks.summarisation.EventAccumulator<IN>`)

### `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)

### `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

### `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)

### `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)

## Constructors

### `public SummarisationRunner(io.casehub.blocks.summarisation.WindowPolicy policy, io.casehub.blocks.summarisation.Compactor<IN> compactor, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel)`

#### Parameters

- `policy` (`io.casehub.blocks.summarisation.WindowPolicy`)
- `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `public SummarisationRunner(io.casehub.blocks.summarisation.WindowPolicy policy, io.casehub.blocks.summarisation.Compactor<IN> compactor, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `policy` (`io.casehub.blocks.summarisation.WindowPolicy`)
- `compactor` (`io.casehub.blocks.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

### `public SummarisationRunner(io.casehub.blocks.summarisation.WindowPolicy policy, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel)`

#### Parameters

- `policy` (`io.casehub.blocks.summarisation.WindowPolicy`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `public SummarisationRunner(io.casehub.blocks.summarisation.WindowPolicy policy, io.casehub.blocks.summarisation.Summariser<IN,OUT> summariser, io.casehub.blocks.summarisation.EventStreamBus<OUT> outputBus, io.casehub.blocks.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `policy` (`io.casehub.blocks.summarisation.WindowPolicy`)
- `summariser` (`io.casehub.blocks.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.blocks.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>>`)

## Methods

### `public void clear()`

### `public void collect(io.casehub.blocks.summarisation.LevelEvent<IN> event)`

#### Parameters

- `event` (`io.casehub.blocks.summarisation.LevelEvent<IN>`)

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> flush()`

Unconditional drain — bypasses WindowPolicy. Use at shutdown to
capture all remaining buffered events regardless of count or age.

### `public int size()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> tick(long now)`

Drains ready events, applies compaction, and submits to the summariser.
Synchronized — concurrent tick() calls are serialized. The hot path
(no events ready) acquires and releases the lock without blocking.

#### Parameters

- `now` (`long`)
