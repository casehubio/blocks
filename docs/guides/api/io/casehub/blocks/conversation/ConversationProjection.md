# io.casehub.blocks.conversation.ConversationProjection

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

Abstract base class for conversation-style channel projections.

<p>Folds channel messages into `ConversationState` by dispatching on
metadata entry types. Infrastructure types (`MEMO`, `SUB_TASK_*`,
`FLAG_HUMAN`, `RESTART_CONTEXT`) are handled by the base class;
domain entry types are dispatched via three hook methods that subclasses override.

<p>`.apply` never throws (protocol PP-20260610-a47ef5). Malformed or
unrecognised messages are logged and the state is returned unchanged.

## Fields

### `LOG` (`java.lang.System.Logger`)

## Constructors

### `public ConversationProjection()`

## Methods

### `public io.casehub.blocks.conversation.ConversationState apply(io.casehub.blocks.conversation.ConversationState state, MessageView message)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)

### `private io.casehub.blocks.conversation.ConversationState doApply(io.casehub.blocks.conversation.ConversationState state, MessageView message)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)

### `private io.casehub.blocks.conversation.ConversationState handleDomain(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta, java.lang.String entryType)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)
- `entryType` (`java.lang.String`)

### `private io.casehub.blocks.conversation.ConversationState handleFlagHuman(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)

### `private io.casehub.blocks.conversation.ConversationState handleMemo(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)

### `private io.casehub.blocks.conversation.ConversationState handlePointInitiation(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta, java.lang.String role, java.lang.String entryType)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)
- `role` (`java.lang.String`)
- `entryType` (`java.lang.String`)

### `private io.casehub.blocks.conversation.ConversationState handlePointResponse(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta, java.lang.String role, java.lang.String entryType)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)
- `role` (`java.lang.String`)
- `entryType` (`java.lang.String`)

### `private io.casehub.blocks.conversation.ConversationState handleSubTaskError(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)

### `private io.casehub.blocks.conversation.ConversationState handleSubTaskFinding(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)

### `private io.casehub.blocks.conversation.ConversationState handleSubTaskRequest(io.casehub.blocks.conversation.ConversationState state, MessageView message, java.util.Map<java.lang.String,java.lang.String> meta)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `message` (`MessageView`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public io.casehub.blocks.conversation.ConversationState identity()`

### `protected abstract boolean isPointInitiator(java.lang.String entryType)`

Returns `true` if the given entry type initiates a new conversation point
(e.g. `RAISE` in DraftHouse debates). The point is created with
`ConversationProtocol.STATUS_OPEN` status.

#### Parameters

- `entryType` (`java.lang.String`)

### `private io.casehub.blocks.conversation.Priority parsePriority(java.lang.String s)`

#### Parameters

- `s` (`java.lang.String`)

### `protected abstract java.lang.String sentinel()`

The sentinel prefix used to parse metadata headers from message content.
Must match the encoding sentinel used by the channel backend.

### `protected java.util.Set<java.lang.String> skipEntryTypes()`

### `protected abstract java.lang.String statusAfter(java.lang.String entryType)`

Returns the status string a point should transition to after a response of
the given entry type, or `null` if the status should remain unchanged.

#### Parameters

- `entryType` (`java.lang.String`)
