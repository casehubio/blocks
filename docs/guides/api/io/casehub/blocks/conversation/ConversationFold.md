# io.casehub.blocks.conversation.ConversationFold

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

Pure state-transition operations on `ConversationState`.

<p>Each method takes an existing state and returns a new state with the
requested change applied. No parsing, no I/O — just the fold mechanics
that any conversation projection (sentinel-based or typed-message) needs.

## Constructors

### `private ConversationFold()`

## Methods

### `public static io.casehub.blocks.conversation.ConversationState addMemo(io.casehub.blocks.conversation.ConversationState state, java.lang.String role, int round, java.lang.String content)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `content` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState completeSubTask(io.casehub.blocks.conversation.ConversationState state, java.lang.String subTaskId, java.lang.String taskType, java.lang.String role, java.lang.String pointId, java.lang.String finding)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `subTaskId` (`java.lang.String`)
- `taskType` (`java.lang.String`)
- `role` (`java.lang.String`)
- `pointId` (`java.lang.String`)
- `finding` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState createPoint(io.casehub.blocks.conversation.ConversationState state, java.lang.String pointId, java.lang.String topic, java.lang.Long messageId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String sender, java.time.Instant createdAt, io.casehub.blocks.conversation.PointClassification classification, java.lang.String role, int round, java.lang.String entryType, java.lang.String content)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `pointId` (`java.lang.String`)
- `topic` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `sender` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `classification` (`io.casehub.blocks.conversation.PointClassification`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `entryType` (`java.lang.String`)
- `content` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState errorSubTask(io.casehub.blocks.conversation.ConversationState state, java.lang.String subTaskId, java.lang.String taskType, java.lang.String role, java.lang.String reason)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `subTaskId` (`java.lang.String`)
- `taskType` (`java.lang.String`)
- `role` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState flagHuman(io.casehub.blocks.conversation.ConversationState state, java.lang.String targetId, java.lang.Long messageId, java.lang.String sender, java.time.Instant createdAt, java.lang.String role, int round, java.lang.String content)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `targetId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
- `sender` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `content` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState reprioritisePoint(io.casehub.blocks.conversation.ConversationState state, java.lang.String targetId, java.lang.Long messageId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String sender, java.time.Instant createdAt, java.lang.String role, int round, io.casehub.blocks.conversation.Priority newPriority, java.lang.String content)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `targetId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `sender` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `newPriority` (`io.casehub.blocks.conversation.Priority`)
- `content` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState requestSubTask(io.casehub.blocks.conversation.ConversationState state, java.lang.String subTaskId, java.lang.String taskType, java.lang.String requestingRole, java.lang.String pointId)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `subTaskId` (`java.lang.String`)
- `taskType` (`java.lang.String`)
- `requestingRole` (`java.lang.String`)
- `pointId` (`java.lang.String`)

### `public static io.casehub.blocks.conversation.ConversationState respondToPoint(io.casehub.blocks.conversation.ConversationState state, java.lang.String targetId, java.lang.Long messageId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String sender, java.time.Instant createdAt, java.lang.String role, int round, java.lang.String entryType, java.lang.String content, java.lang.String newStatus)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `targetId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `sender` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `role` (`java.lang.String`)
- `round` (`int`)
- `entryType` (`java.lang.String`)
- `content` (`java.lang.String`)
- `newStatus` (`java.lang.String`)
