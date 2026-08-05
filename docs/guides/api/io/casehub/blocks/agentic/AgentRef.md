# io.casehub.blocks.agentic.AgentRef

**Package:** `io.casehub.blocks.agentic`

**Kind:** `interface`

## Methods

### `public static io.casehub.blocks.agentic.AgentRef.ChannelAgent channel(java.util.UUID channelId, io.casehub.blocks.channel.ChannelAgentHandler handler)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `handler` (`io.casehub.blocks.channel.ChannelAgentHandler`)

### `public static io.casehub.blocks.agentic.AgentRef.ComposedAgent composed(io.casehub.blocks.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<?>`)

### `public static io.casehub.blocks.agentic.AgentRef.ExternalAgent external(java.lang.String label, java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.blocks.agentic.AgentResult>> fn)`

#### Parameters

- `label` (`java.lang.String`)
- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.blocks.agentic.AgentResult>>`)

### `public static io.casehub.blocks.agentic.AgentRef.ExternalAgent external(java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.blocks.agentic.AgentResult>> fn)`

#### Parameters

- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.blocks.agentic.AgentResult>>`)

### `public static io.casehub.blocks.agentic.AgentRef.HumanAgent human(WorkItemCreateRequest template)`

#### Parameters

- `template` (`WorkItemCreateRequest`)

### `public static io.casehub.blocks.agentic.AgentRef.WorkerAgent worker(Worker worker)`

#### Parameters

- `worker` (`Worker`)
