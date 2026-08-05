# io.casehub.blocks.channel.ChannelEventAdapter

**Package:** `io.casehub.blocks.channel`

**Kind:** `class`

## Fields

### `LOG` (`java.util.logging.Logger`)

### `channelNames` (`java.util.Set<java.lang.String>`)

### `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)

### `level` (`io.casehub.blocks.summarisation.EventLevel`)

### `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<E>`)

## Constructors

### `public ChannelEventAdapter(java.util.function.Function<MessageReceivedEvent,E> extractor, io.casehub.blocks.summarisation.EventLevel level, io.casehub.blocks.summarisation.EventStreamBus<E> outputBus)`

#### Parameters

- `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)
- `level` (`io.casehub.blocks.summarisation.EventLevel`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<E>`)

### `public ChannelEventAdapter(java.util.function.Function<MessageReceivedEvent,E> extractor, io.casehub.blocks.summarisation.EventLevel level, io.casehub.blocks.summarisation.EventStreamBus<E> outputBus, java.util.Set<java.lang.String> channelNames)`

#### Parameters

- `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)
- `level` (`io.casehub.blocks.summarisation.EventLevel`)
- `outputBus` (`io.casehub.blocks.summarisation.EventStreamBus<E>`)
- `channelNames` (`java.util.Set<java.lang.String>`)

## Methods

### `public java.util.Set<java.lang.String> channels()`

### `public void onMessage(MessageReceivedEvent event)`

#### Parameters

- `event` (`MessageReceivedEvent`)

### `public Scope scope()`
