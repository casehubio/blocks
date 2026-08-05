# io.casehub.blocks.agentic.decomposition.StructuralCostHeuristic

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `opaqueCost` (`double`)

## Constructors

### `public StructuralCostHeuristic()`

### `public StructuralCostHeuristic(double opaqueCost)`

#### Parameters

- `opaqueCost` (`double`)

## Methods

### `private double estimateCost(DecompositionMethod<T> method)`

#### Parameters

- `method` (`DecompositionMethod<T>`)

### `private double estimateNodeCost(TaskNode<T> node)`

#### Parameters

- `node` (`TaskNode<T>`)

### `public Uni<java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>>> evaluate(TaskNode.CompoundTask<T> task, java.util.List<DecompositionMethod<T>> methods, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode.CompoundTask<T>`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)
- `context` (`DecompositionContext<T>`)
