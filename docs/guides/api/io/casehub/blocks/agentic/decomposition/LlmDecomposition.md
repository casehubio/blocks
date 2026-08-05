# io.casehub.blocks.agentic.decomposition.LlmDecomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `FLAT_SYSTEM_PROMPT` (`java.lang.String`)

### `LOG` (`java.lang.System.Logger`)

### `MAPPER` (`ObjectMapper`)

### `RECURSIVE_SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

### `maxDepth` (`int`)

### `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `systemPromptCustomiser` (`io.casehub.blocks.prompt.SystemPromptCustomiser`)

### `variantSelector` (`io.casehub.blocks.prompt.VariantSelector`)

### `variantStore` (`io.casehub.blocks.prompt.PromptVariantStore`)

## Constructors

### `public LlmDecomposition(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public LlmDecomposition(AgentProvider agentProvider, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `maxDepth` (`int`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth, io.casehub.blocks.prompt.SystemPromptCustomiser systemPromptCustomiser, io.casehub.blocks.prompt.VariantSelector variantSelector, io.casehub.blocks.prompt.PromptVariantStore variantStore)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)
- `systemPromptCustomiser` (`io.casehub.blocks.prompt.SystemPromptCustomiser`)
- `variantSelector` (`io.casehub.blocks.prompt.VariantSelector`)
- `variantStore` (`io.casehub.blocks.prompt.PromptVariantStore`)

## Methods

### `private void appendFewShotExamples(java.lang.StringBuilder sb)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)

### `private java.lang.String buildUserPrompt(TaskNode.CompoundTask<T> goal, io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T> context)`

#### Parameters

- `goal` (`TaskNode.CompoundTask<T>`)
- `context` (`io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T>`)

### `private static java.util.List<java.lang.String> collectEntryNames(JsonNode root)`

#### Parameters

- `root` (`JsonNode`)

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound, DecompositionContext<T> context)`

#### Parameters

- `compound` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private static io.casehub.blocks.agentic.AgentRef resolveAgent(java.lang.String name, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents)`

#### Parameters

- `name` (`java.lang.String`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `private TaskNode.@Nullable LeafTask<T> resolveAgentEntry(JsonNode node, java.lang.String taskName, io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T> ctx)`

#### Parameters

- `node` (`JsonNode`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T>`)

### `private DagPlan<TaskNode.LeafTask<T>> resolveEntries(java.lang.String text, java.lang.String taskName, io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T> ctx)`

#### Parameters

- `text` (`java.lang.String`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T>`)

### `private DagPlan<TaskNode.LeafTask<T>> resolveSubtaskEntry(JsonNode node, java.lang.String taskName, io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T> ctx, java.util.List<java.lang.String> siblingNames)`

#### Parameters

- `node` (`JsonNode`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<T>`)
- `siblingNames` (`java.util.List<java.lang.String>`)

### `private java.lang.String selectSystemPrompt(int currentDepth)`

#### Parameters

- `currentDepth` (`int`)

### `private static java.lang.String stripCodeFence(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)
