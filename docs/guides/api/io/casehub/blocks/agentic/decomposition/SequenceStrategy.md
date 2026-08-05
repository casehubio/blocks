# io.casehub.blocks.agentic.decomposition.SequenceStrategy

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `children` (`java.util.List<TaskNode<T>>`)

## Constructors

### `SequenceStrategy(java.util.List<TaskNode<T>> children)`

#### Parameters

- `children` (`java.util.List<TaskNode<T>>`)

## Methods

### `java.util.List<TaskNode<T>> children()`

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> ignored, DecompositionContext<T> ctx)`

#### Parameters

- `ignored` (`TaskNode<T>`)
- `ctx` (`DecompositionContext<T>`)

### `private static Uni<DagPlan<TaskNode.LeafTask<T>>> resolvePlan(TaskNode<T> node, DecompositionStrategy<T> decomposer, DecompositionContext<T> ctx)`

#### Parameters

- `node` (`TaskNode<T>`)
- `decomposer` (`DecompositionStrategy<T>`)
- `ctx` (`DecompositionContext<T>`)
