# io.casehub.blocks.agentic.pattern.DebateBuilder

**Package:** `io.casehub.blocks.agentic.pattern`

**Kind:** `class`

## Fields

### `convergenceExplicitlySet` (`boolean`)

### `judge` (`io.casehub.blocks.agentic.AgentRef`)

### `maxRounds` (`int`)

## Constructors

### `public DebateBuilder()`

## Methods

### `public io.casehub.blocks.agentic.model.ExecutionModel<T> build()`

### `public io.casehub.blocks.agentic.pattern.DebateBuilder<T> convergence(io.casehub.blocks.agentic.termination.TerminationCondition<T> convergence)`

#### Parameters

- `convergence` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)

### `public io.casehub.blocks.agentic.pattern.DebateBuilder<T> debaters(io.casehub.blocks.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.blocks.agentic.AgentRef[]`)

### `public io.casehub.blocks.agentic.pattern.DebateBuilder<T> judge(io.casehub.blocks.agentic.AgentRef judge)`

#### Parameters

- `judge` (`io.casehub.blocks.agentic.AgentRef`)

### `public io.casehub.blocks.agentic.pattern.DebateBuilder<T> maxRounds(int rounds)`

#### Parameters

- `rounds` (`int`)
