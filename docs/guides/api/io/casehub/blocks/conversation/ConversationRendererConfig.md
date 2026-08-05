# io.casehub.blocks.conversation.ConversationRendererConfig

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `entryTypeLabel` (`java.util.Map<java.lang.String,java.lang.String>`)

### `escalatedStatuses` (`java.util.Set<java.lang.String>`)

### `groupByTopic` (`boolean`)

### `messageTypeLabel` (`java.util.Map<io.casehub.qhorus.api.message.MessageType,java.lang.String>`)

### `priorityLabel` (`java.util.Map<io.casehub.blocks.conversation.Priority,java.lang.String>`)

### `resolvedStatuses` (`java.util.Set<java.lang.String>`)

### `roleLabel` (`java.util.Map<java.lang.String,java.lang.String>`)

### `showConvergenceSignal` (`boolean`)

### `showEpistemicStatus` (`boolean`)

### `showObligationChain` (`boolean`)

### `showProgress` (`boolean`)

### `statusEmoji` (`java.util.Map<java.lang.String,java.lang.String>`)

## Record Components

### `entryTypeLabel` (`java.util.Map<java.lang.String,java.lang.String>`)

### `escalatedStatuses` (`java.util.Set<java.lang.String>`)

### `groupByTopic` (`boolean`)

### `messageTypeLabel` (`java.util.Map<io.casehub.qhorus.api.message.MessageType,java.lang.String>`)

### `priorityLabel` (`java.util.Map<io.casehub.blocks.conversation.Priority,java.lang.String>`)

### `resolvedStatuses` (`java.util.Set<java.lang.String>`)

### `roleLabel` (`java.util.Map<java.lang.String,java.lang.String>`)

### `showConvergenceSignal` (`boolean`)

### `showEpistemicStatus` (`boolean`)

### `showObligationChain` (`boolean`)

### `showProgress` (`boolean`)

### `statusEmoji` (`java.util.Map<java.lang.String,java.lang.String>`)

## Constructors

### `public ConversationRendererConfig(java.util.Map<java.lang.String,java.lang.String> statusEmoji, java.util.Map<io.casehub.blocks.conversation.Priority,java.lang.String> priorityLabel, java.util.Map<java.lang.String,java.lang.String> entryTypeLabel, java.util.Map<java.lang.String,java.lang.String> roleLabel, java.util.Set<java.lang.String> resolvedStatuses, java.util.Set<java.lang.String> escalatedStatuses, java.util.Map<io.casehub.qhorus.api.message.MessageType,java.lang.String> messageTypeLabel, boolean groupByTopic, boolean showObligationChain, boolean showEpistemicStatus, boolean showConvergenceSignal, boolean showProgress)`

#### Parameters

- `statusEmoji` (`java.util.Map<java.lang.String,java.lang.String>`)
- `priorityLabel` (`java.util.Map<io.casehub.blocks.conversation.Priority,java.lang.String>`)
- `entryTypeLabel` (`java.util.Map<java.lang.String,java.lang.String>`)
- `roleLabel` (`java.util.Map<java.lang.String,java.lang.String>`)
- `resolvedStatuses` (`java.util.Set<java.lang.String>`)
- `escalatedStatuses` (`java.util.Set<java.lang.String>`)
- `messageTypeLabel` (`java.util.Map<io.casehub.qhorus.api.message.MessageType,java.lang.String>`)
- `groupByTopic` (`boolean`)
- `showObligationChain` (`boolean`)
- `showEpistemicStatus` (`boolean`)
- `showConvergenceSignal` (`boolean`)
- `showProgress` (`boolean`)

## Methods

### `public static io.casehub.blocks.conversation.ConversationRendererConfig.Builder builder()`

### `public java.util.Map<java.lang.String,java.lang.String> entryTypeLabel()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Set<java.lang.String> escalatedStatuses()`

### `public boolean groupByTopic()`

### `public final int hashCode()`

### `public java.util.Map<io.casehub.qhorus.api.message.MessageType,java.lang.String> messageTypeLabel()`

### `public java.util.Map<io.casehub.blocks.conversation.Priority,java.lang.String> priorityLabel()`

### `public java.util.Set<java.lang.String> resolvedStatuses()`

### `public java.util.Map<java.lang.String,java.lang.String> roleLabel()`

### `public boolean showConvergenceSignal()`

### `public boolean showEpistemicStatus()`

### `public boolean showObligationChain()`

### `public boolean showProgress()`

### `public java.util.Map<java.lang.String,java.lang.String> statusEmoji()`

### `public io.casehub.blocks.conversation.ConversationRendererConfig.Builder toBuilder()`

### `public final java.lang.String toString()`
