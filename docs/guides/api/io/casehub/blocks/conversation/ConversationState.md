# io.casehub.blocks.conversation.ConversationState

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `humanFlags` (`java.util.List<io.casehub.blocks.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.blocks.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.SubTaskFinding>`)

## Record Components

### `humanFlags` (`java.util.List<io.casehub.blocks.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.blocks.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.SubTaskFinding>`)

## Constructors

### `public ConversationState(java.util.Map<java.lang.String,io.casehub.blocks.conversation.ConversationPoint> points, java.util.List<io.casehub.blocks.conversation.FlagEntry> humanFlags, java.util.List<io.casehub.blocks.conversation.RoundMemo> memos, java.util.Map<java.lang.String,io.casehub.blocks.conversation.SubTaskFinding> subTaskFindings)`

#### Parameters

- `points` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.ConversationPoint>`)
- `humanFlags` (`java.util.List<io.casehub.blocks.conversation.FlagEntry>`)
- `memos` (`java.util.List<io.casehub.blocks.conversation.RoundMemo>`)
- `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.blocks.conversation.SubTaskFinding>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.blocks.conversation.FlagEntry> humanFlags()`

### `public java.util.List<io.casehub.blocks.conversation.RoundMemo> memos()`

### `public java.util.Map<java.lang.String,io.casehub.blocks.conversation.ConversationPoint> points()`

### `public java.util.Map<java.lang.String,io.casehub.blocks.conversation.SubTaskFinding> subTaskFindings()`

### `public final java.lang.String toString()`
