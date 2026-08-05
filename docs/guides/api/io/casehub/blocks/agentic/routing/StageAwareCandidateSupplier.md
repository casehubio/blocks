# io.casehub.blocks.agentic.routing.StageAwareCandidateSupplier

**Package:** `io.casehub.blocks.agentic.routing`

**Kind:** `class`

## Fields

### `bindingNameResolver` (`java.util.function.Function<io.casehub.blocks.agentic.AgentRef,java.lang.String>`)

### `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)

### `stageGate` (`io.casehub.blocks.agentic.routing.StageGate`)

## Constructors

### `public StageAwareCandidateSupplier(java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>> delegate, io.casehub.blocks.agentic.routing.StageGate stageGate, java.util.function.Function<io.casehub.blocks.agentic.AgentRef,java.lang.String> bindingNameResolver)`

#### Parameters

- `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)
- `stageGate` (`io.casehub.blocks.agentic.routing.StageGate`)
- `bindingNameResolver` (`java.util.function.Function<io.casehub.blocks.agentic.AgentRef,java.lang.String>`)

## Methods

### `public java.util.List<io.casehub.blocks.agentic.RoutingCandidate> get()`
