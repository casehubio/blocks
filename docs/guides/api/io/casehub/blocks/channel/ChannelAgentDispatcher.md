# io.casehub.blocks.channel.ChannelAgentDispatcher

**Package:** `io.casehub.blocks.channel`

**Kind:** `class`

First-match handler routing for channel agent sub-task requests.
Routes a request to the first matching handler, invokes the agent provider,
and posts the result back to the channel.

<p>Apps subclass this to provide error dispatch and instance registration.
The routing loop and agent invocation are generic.

## Fields

### `LOG` (`java.util.logging.Logger`)

### `agentProvider` (`java.util.function.Function<io.casehub.blocks.channel.AgentTask,java.lang.String>`)

### `handlers` (`java.lang.Iterable<io.casehub.blocks.channel.ChannelAgentHandler>`)

### `messageSink` (`java.util.function.Consumer<MessageDispatch>`)

### `senderId` (`java.lang.String`)

## Constructors

### `protected ChannelAgentDispatcher()`

### `public ChannelAgentDispatcher(java.util.function.Function<io.casehub.blocks.channel.AgentTask,java.lang.String> agentProvider, java.util.function.Consumer<MessageDispatch> messageSink, java.lang.Iterable<io.casehub.blocks.channel.ChannelAgentHandler> handlers, java.lang.String senderId)`

#### Parameters

- `agentProvider` (`java.util.function.Function<io.casehub.blocks.channel.AgentTask,java.lang.String>`) — invokes the LLM — takes AgentTask, returns output string
- `messageSink` (`java.util.function.Consumer<MessageDispatch>`) — posts messages to the channel (typically messageService::dispatch)
- `handlers` (`java.lang.Iterable<io.casehub.blocks.channel.ChannelAgentHandler>`) — handler beans, iterated in CDI discovery order
- `senderId` (`java.lang.String`) — the instance ID used as sender for dispatched messages

## Methods

### `public void dispatch(io.casehub.blocks.channel.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.blocks.channel.ChannelAgentRequest`)

### `protected java.util.function.Consumer<MessageDispatch> messageSink()`

### `protected void onError(io.casehub.blocks.channel.ChannelAgentRequest request, java.lang.String reason)`

Called when dispatch fails. Apps override to encode error messages
in their protocol format and post to the channel.

#### Parameters

- `request` (`io.casehub.blocks.channel.ChannelAgentRequest`)
- `reason` (`java.lang.String`)

### `protected java.lang.String senderId()`
