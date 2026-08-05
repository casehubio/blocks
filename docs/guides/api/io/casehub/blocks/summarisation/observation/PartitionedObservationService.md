# io.casehub.blocks.summarisation.observation.PartitionedObservationService

**Package:** `io.casehub.blocks.summarisation.observation`

**Kind:** `class`

## Fields

### `eventLevel` (`io.casehub.blocks.summarisation.EventLevel`)

### `observers` (`java.util.concurrent.ConcurrentHashMap<java.lang.String,io.casehub.blocks.summarisation.observation.ObserverState<E,K>>`)

### `renderer` (`io.casehub.blocks.summarisation.observation.ObservationRenderer<E>`)

### `timestampExtractor` (`java.util.function.Function<E,java.lang.Long>`)

### `visibilityPolicy` (`io.casehub.blocks.summarisation.observation.VisibilityPolicy<E,K>`)

## Constructors

### `public PartitionedObservationService(io.casehub.blocks.summarisation.observation.ObservationRenderer<E> renderer, io.casehub.blocks.summarisation.observation.VisibilityPolicy<E,K> visibilityPolicy, java.util.function.Function<E,java.lang.Long> timestampExtractor, io.casehub.blocks.summarisation.EventLevel eventLevel)`

#### Parameters

- `renderer` (`io.casehub.blocks.summarisation.observation.ObservationRenderer<E>`)
- `visibilityPolicy` (`io.casehub.blocks.summarisation.observation.VisibilityPolicy<E,K>`)
- `timestampExtractor` (`java.util.function.Function<E,java.lang.Long>`)
- `eventLevel` (`io.casehub.blocks.summarisation.EventLevel`)

## Methods

### `public void addObserver(java.lang.String observerId, K initialPartition)`

#### Parameters

- `observerId` (`java.lang.String`)
- `initialPartition` (`K`)

### `public void clear()`

### `public io.casehub.blocks.summarisation.observation.PartitionedDrain<K> drain(java.lang.String observerId, K currentPartition, long now)`

#### Parameters

- `observerId` (`java.lang.String`)
- `currentPartition` (`K`)
- `now` (`long`)

### `public io.casehub.blocks.summarisation.observation.ObserverState<E,K> observerState(java.lang.String observerId)`

#### Parameters

- `observerId` (`java.lang.String`)

### `public void publishEvent(E event)`

#### Parameters

- `event` (`E`)
