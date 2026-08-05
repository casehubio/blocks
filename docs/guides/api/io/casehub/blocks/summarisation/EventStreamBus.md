# io.casehub.blocks.summarisation.EventStreamBus

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `class`

Predicate-based pub/sub for typed events. Backed by `CopyOnWriteArrayList` —
concurrent `publish()` and `subscribe()` calls are safe. Thread-safety
of the overall pipeline depends on subscriber callbacks: when using
`SummarisationRunner`, the downstream `EventAccumulator`'s synchronized
methods ensure safe cross-thread access.

## Fields

### `subscriptions` (`java.util.List<io.casehub.blocks.summarisation.EventStreamBus.Subscription<E>>`)

## Constructors

### `public EventStreamBus()`

## Methods

### `public void clear()`

### `public void clearSubscriptions()`

### `public void publish(io.casehub.blocks.summarisation.LevelEvent<E> event)`

Dispatches the event to all matching subscribers synchronously on
the calling thread. A slow or blocking callback delays all
subsequent subscribers in this publish call.

#### Parameters

- `event` (`io.casehub.blocks.summarisation.LevelEvent<E>`)

### `public void subscribe(java.util.function.Predicate<E> filter, java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>> callback)`

Registers a subscription. The callback runs synchronously on the
thread that calls `.publish` — blocking callbacks block
the entire publish loop. Wrap in `CompletableFuture.runAsync()`
if the callback may block.

#### Parameters

- `filter` (`java.util.function.Predicate<E>`)
- `callback` (`java.util.function.Consumer<io.casehub.blocks.summarisation.LevelEvent<E>>`)
