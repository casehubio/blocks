# io.casehub.blocks.conversation.ParticipantContext

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `acknowledgedBy` (`java.util.Set<java.lang.String>`)

### `allParticipants` (`java.util.Set<java.lang.String>`)

### `completedBy` (`java.util.Set<java.lang.String>`)

### `disputedBy` (`java.util.Set<java.lang.String>`)

### `failedBy` (`java.util.Set<java.lang.String>`)

### `respondedBy` (`java.util.Set<java.lang.String>`)

### `roundsSinceLastActivity` (`int`)

## Record Components

### `acknowledgedBy` (`java.util.Set<java.lang.String>`)

### `allParticipants` (`java.util.Set<java.lang.String>`)

### `completedBy` (`java.util.Set<java.lang.String>`)

### `disputedBy` (`java.util.Set<java.lang.String>`)

### `failedBy` (`java.util.Set<java.lang.String>`)

### `respondedBy` (`java.util.Set<java.lang.String>`)

### `roundsSinceLastActivity` (`int`)

## Constructors

### `public ParticipantContext(java.util.Set<java.lang.String> allParticipants, java.util.Set<java.lang.String> respondedBy, java.util.Set<java.lang.String> acknowledgedBy, java.util.Set<java.lang.String> completedBy, java.util.Set<java.lang.String> disputedBy, java.util.Set<java.lang.String> failedBy, int roundsSinceLastActivity)`

#### Parameters

- `allParticipants` (`java.util.Set<java.lang.String>`)
- `respondedBy` (`java.util.Set<java.lang.String>`)
- `acknowledgedBy` (`java.util.Set<java.lang.String>`)
- `completedBy` (`java.util.Set<java.lang.String>`)
- `disputedBy` (`java.util.Set<java.lang.String>`)
- `failedBy` (`java.util.Set<java.lang.String>`)
- `roundsSinceLastActivity` (`int`)

## Methods

### `public java.util.Set<java.lang.String> acknowledgedBy()`

### `public java.util.Set<java.lang.String> allParticipants()`

### `public java.util.Set<java.lang.String> completedBy()`

### `public java.util.Set<java.lang.String> disputedBy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Set<java.lang.String> failedBy()`

### `public final int hashCode()`

### `public java.util.Set<java.lang.String> respondedBy()`

### `public int roundsSinceLastActivity()`

### `public final java.lang.String toString()`
