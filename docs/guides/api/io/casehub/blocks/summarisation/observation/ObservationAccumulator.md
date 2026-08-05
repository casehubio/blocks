# io.casehub.blocks.summarisation.observation.ObservationAccumulator

**Package:** `io.casehub.blocks.summarisation.observation`

**Kind:** `class`

## Fields

### `buffer` (`java.util.List<io.casehub.blocks.summarisation.LevelEvent<E>>`)

### `lastDrainTimestamp` (`long`)

### `renderer` (`io.casehub.blocks.summarisation.observation.ObservationRenderer<E>`)

## Constructors

### `public ObservationAccumulator(io.casehub.blocks.summarisation.observation.ObservationRenderer<E> renderer)`

#### Parameters

- `renderer` (`io.casehub.blocks.summarisation.observation.ObservationRenderer<E>`)

### `ObservationAccumulator(io.casehub.blocks.summarisation.observation.ObservationRenderer<E> renderer, long createdAt)`

#### Parameters

- `renderer` (`io.casehub.blocks.summarisation.observation.ObservationRenderer<E>`)
- `createdAt` (`long`)

## Methods

### `public synchronized void clear()`

### `public synchronized void collect(io.casehub.blocks.summarisation.LevelEvent<E> event)`

#### Parameters

- `event` (`io.casehub.blocks.summarisation.LevelEvent<E>`)

### `public java.util.concurrent.CompletionStage<io.casehub.blocks.summarisation.observation.ObservationResult> drainObservation(long now)`

#### Parameters

- `now` (`long`)

### `public synchronized int eventCount()`
