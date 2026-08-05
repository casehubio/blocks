# io.casehub.blocks.conversation.FlagEntry

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

A human-escalation flag raised during a conversation.

`entryId` and `round` are null/0 in v1 file-based paths — both are populated
only via the channel backend, where the MessageView carries a stable correlationId and round context.

## Fields

### `content` (`java.lang.String`)

### `entryId` (`java.lang.String`)

### `role` (`java.lang.String`)

### `round` (`int`)

## Record Components

### `content` (`java.lang.String`)

### `entryId` (`java.lang.String`)

### `role` (`java.lang.String`)

### `round` (`int`)

## Constructors

### `public FlagEntry(java.lang.String entryId, int round, java.lang.String role, java.lang.String content)`

#### Parameters

- `entryId` (`java.lang.String`)
- `round` (`int`)
- `role` (`java.lang.String`)
- `content` (`java.lang.String`)

## Methods

### `public java.lang.String content()`

### `public java.lang.String entryId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String role()`

### `public int round()`

### `public final java.lang.String toString()`
