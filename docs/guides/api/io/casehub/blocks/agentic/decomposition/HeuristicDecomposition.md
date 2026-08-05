# io.casehub.blocks.agentic.decomposition.HeuristicDecomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `heuristic` (`io.casehub.blocks.agentic.decomposition.DecompositionHeuristic<T>`)

## Constructors

### `public HeuristicDecomposition(io.casehub.blocks.agentic.decomposition.DecompositionHeuristic<T> heuristic)`

#### Parameters

- `heuristic` (`io.casehub.blocks.agentic.decomposition.DecompositionHeuristic<T>`)

## Methods

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> task, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private DecompositionContext<T> enrichContext(DecompositionContext<T> context)`

#### Parameters

- `context` (`DecompositionContext<T>`)

### `public java.lang.String id()`

### `private Uni<DagPlan<TaskNode.LeafTask<T>>> tryRanked(java.util.List<DecompositionMethod<T>> ranked, int index, TaskNode.CompoundTask<T> ct, DecompositionContext<T> ctx)`

#### Parameters

- `ranked` (`java.util.List<DecompositionMethod<T>>`)
- `index` (`int`)
- `ct` (`TaskNode.CompoundTask<T>`)
- `ctx` (`DecompositionContext<T>`)
