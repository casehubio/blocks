# io.casehub.blocks.conversation.ThreadEntry

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `content` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `entryId` (`java.lang.String`)

### `entryType` (`java.lang.String`)

### `messageId` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `role` (`java.lang.String`)

### `round` (`int`)

### `sender` (`java.lang.String`)

## Record Components

### `content` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `entryId` (`java.lang.String`)

### `entryType` (`java.lang.String`)

### `messageId` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `role` (`java.lang.String`)

### `round` (`int`)

### `sender` (`java.lang.String`)

## Constructors

### `public ThreadEntry(java.lang.String entryId, java.lang.Long messageId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String sender, java.time.Instant createdAt, java.lang.String role, int round, java.lang.String entryType, java.lang.String content)`

#### Parameters

- `entryId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `sender` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `entryType` (`java.lang.String`)
- `content` (`java.lang.String`)

## Methods

### `public java.lang.String content()`

### `public java.time.Instant createdAt()`

### `public java.lang.String entryId()`

### `public java.lang.String entryType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long messageId()`

### `public io.casehub.qhorus.api.message.MessageType messageType()`

### `public java.lang.String role()`

### `public int round()`

### `public java.lang.String sender()`

### `public final java.lang.String toString()`
