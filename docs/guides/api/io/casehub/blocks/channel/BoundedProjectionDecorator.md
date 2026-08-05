# io.casehub.blocks.channel.BoundedProjectionDecorator

**Package:** `io.casehub.blocks.channel`

**Kind:** `class`

Decorator that skips messages whose extracted value exceeds a bound.
Enables "replay state up to round N" queries without modifying the base projection.

## Fields

### `delegate` (`ChannelProjection<S>`)

### `maxValue` (`int`)

### `valueExtractor` (`java.util.function.ToIntFunction<MessageView>`)

## Constructors

### `public BoundedProjectionDecorator(int maxValue, ChannelProjection<S> delegate, java.util.function.ToIntFunction<MessageView> valueExtractor)`

#### Parameters

- `maxValue` (`int`) — messages with extracted value above this are skipped
- `delegate` (`ChannelProjection<S>`) — the base projection to fold into
- `valueExtractor` (`java.util.function.ToIntFunction<MessageView>`) — extracts the bound value from each message (e.g. round number)

## Methods

### `public S apply(S state, MessageView message)`

#### Parameters

- `state` (`S`)
- `message` (`MessageView`)

### `public S identity()`
