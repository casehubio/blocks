# io.casehub.blocks.conversation.GroundedFact

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `acknowledgedBy` (`java.util.Set<java.lang.String>`)

### `content` (`java.lang.String`)

### `disputedBy` (`java.util.Set<java.lang.String>`)

### `pointId` (`java.lang.String`)

### `round` (`int`)

### `status` (`io.casehub.blocks.conversation.EpistemicStatus`)

### `topic` (`java.lang.String`)

## Record Components

### `acknowledgedBy` (`java.util.Set<java.lang.String>`)

### `content` (`java.lang.String`)

### `disputedBy` (`java.util.Set<java.lang.String>`)

### `pointId` (`java.lang.String`)

### `round` (`int`)

### `status` (`io.casehub.blocks.conversation.EpistemicStatus`)

### `topic` (`java.lang.String`)

## Constructors

### `public GroundedFact(java.lang.String pointId, java.lang.String topic, io.casehub.blocks.conversation.EpistemicStatus status, java.lang.String content, java.util.Set<java.lang.String> acknowledgedBy, java.util.Set<java.lang.String> disputedBy, int round)`

#### Parameters

- `pointId` (`java.lang.String`)
- `topic` (`java.lang.String`)
- `status` (`io.casehub.blocks.conversation.EpistemicStatus`)
- `content` (`java.lang.String`)
- `acknowledgedBy` (`java.util.Set<java.lang.String>`)
- `disputedBy` (`java.util.Set<java.lang.String>`)
- `round` (`int`)

## Methods

### `public java.util.Set<java.lang.String> acknowledgedBy()`

### `public java.lang.String content()`

### `public java.util.Set<java.lang.String> disputedBy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String pointId()`

### `public int round()`

### `public io.casehub.blocks.conversation.EpistemicStatus status()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`
