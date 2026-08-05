# io.casehub.blocks.channel.ChannelAgentHandler

**Package:** `io.casehub.blocks.channel`

**Kind:** `interface`

SPI for channel-reactive agent handlers.
Handlers must have non-overlapping handles() predicates — first-match routing.

## Methods

### `public abstract MessageDispatch buildResponse(java.util.UUID channelId, java.lang.String senderId, java.lang.String llmOutput, io.casehub.blocks.channel.ChannelAgentRequest trigger)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `senderId` (`java.lang.String`)
- `llmOutput` (`java.lang.String`)
- `trigger` (`io.casehub.blocks.channel.ChannelAgentRequest`)

### `public abstract boolean handles(io.casehub.blocks.channel.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.blocks.channel.ChannelAgentRequest`)

### `public abstract io.casehub.blocks.channel.AgentTask prepareTask(io.casehub.blocks.channel.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.blocks.channel.ChannelAgentRequest`)
