# io.casehub.blocks.conversation.ConvergenceContext

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `disputedCount` (`int`)

### `establishedCount` (`int`)

### `messageLengthTrend` (`double`)

### `pendingCount` (`int`)

### `recentMessageTypeCounts` (`java.util.Map<MessageType,java.lang.Integer>`)

### `recentSimilarity` (`double`)

### `roundsSinceNewPoint` (`int`)

### `roundsSinceStatusChange` (`int`)

### `totalPoints` (`int`)

## Record Components

### `disputedCount` (`int`)

### `establishedCount` (`int`)

### `messageLengthTrend` (`double`)

### `pendingCount` (`int`)

### `recentMessageTypeCounts` (`java.util.Map<MessageType,java.lang.Integer>`)

### `recentSimilarity` (`double`)

### `roundsSinceNewPoint` (`int`)

### `roundsSinceStatusChange` (`int`)

### `totalPoints` (`int`)

## Constructors

### `public ConvergenceContext(int totalPoints, int establishedCount, int pendingCount, int disputedCount, double recentSimilarity, double messageLengthTrend, int roundsSinceNewPoint, int roundsSinceStatusChange, java.util.Map<MessageType,java.lang.Integer> recentMessageTypeCounts)`

#### Parameters

- `totalPoints` (`int`)
- `establishedCount` (`int`)
- `pendingCount` (`int`)
- `disputedCount` (`int`)
- `recentSimilarity` (`double`)
- `messageLengthTrend` (`double`)
- `roundsSinceNewPoint` (`int`)
- `roundsSinceStatusChange` (`int`)
- `recentMessageTypeCounts` (`java.util.Map<MessageType,java.lang.Integer>`)

## Methods

### `public int disputedCount()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int establishedCount()`

### `public final int hashCode()`

### `public double messageLengthTrend()`

### `public int pendingCount()`

### `public java.util.Map<MessageType,java.lang.Integer> recentMessageTypeCounts()`

### `public double recentSimilarity()`

### `public int roundsSinceNewPoint()`

### `public int roundsSinceStatusChange()`

### `public final java.lang.String toString()`

### `public int totalPoints()`
