# io.casehub.blocks.conversation.ConversationPoint

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `classification` (`io.casehub.blocks.conversation.PointClassification`)

### `id` (`java.lang.String`)

### `status` (`java.lang.String`)

### `thread` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)

### `topic` (`java.lang.String`)

## Record Components

### `classification` (`io.casehub.blocks.conversation.PointClassification`)

### `id` (`java.lang.String`)

### `status` (`java.lang.String`)

### `thread` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)

### `topic` (`java.lang.String`)

## Constructors

### `public ConversationPoint(java.lang.String id, java.lang.String topic, io.casehub.blocks.conversation.PointClassification classification, java.util.List<io.casehub.blocks.conversation.ThreadEntry> thread, java.lang.String status)`

#### Parameters

- `id` (`java.lang.String`)
- `topic` (`java.lang.String`)
- `classification` (`io.casehub.blocks.conversation.PointClassification`)
- `thread` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)
- `status` (`java.lang.String`)

## Methods

### `public io.casehub.blocks.conversation.PointClassification classification()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String id()`

### `public java.lang.String status()`

### `public java.util.List<io.casehub.blocks.conversation.ThreadEntry> thread()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`
