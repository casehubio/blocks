# io.casehub.blocks.summarisation.EventStreamBus.Subscription

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `record`

## Fields

### `callback` (`java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>>`)

### `filter` (`java.util.function.Predicate<E>`)

## Record Components

### `callback` (`java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>>`)

### `filter` (`java.util.function.Predicate<E>`)

## Constructors

### `private Subscription(java.util.function.Predicate<E> filter, java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>> callback)`

#### Parameters

- `filter` (`java.util.function.Predicate<E>`)
- `callback` (`java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>>`)

## Methods

### `public java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>> callback()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.function.Predicate<E> filter()`

### `public final int hashCode()`

### `public final java.lang.String toString()`
