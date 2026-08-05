# io.casehub.blocks.agentic.decomposition.CompositeHeuristic

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `delegates` (`java.util.List<io.casehub.blocks.agentic.decomposition.CompositeHeuristic.WeightedHeuristic<T>>`)

## Constructors

### `public CompositeHeuristic(java.util.List<io.casehub.blocks.agentic.decomposition.CompositeHeuristic.WeightedHeuristic<T>> delegates)`

#### Parameters

- `delegates` (`java.util.List<io.casehub.blocks.agentic.decomposition.CompositeHeuristic.WeightedHeuristic<T>>`)

## Methods

### `private java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>> combine(java.util.List<DecompositionMethod<T>> methods, java.util.List<java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>>> delegateResults)`

#### Parameters

- `methods` (`java.util.List<DecompositionMethod<T>>`)
- `delegateResults` (`java.util.List<java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>>>`)

### `public Uni<java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>>> evaluate(TaskNode.CompoundTask<T> task, java.util.List<DecompositionMethod<T>> methods, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode.CompoundTask<T>`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)
- `context` (`DecompositionContext<T>`)

### `private static double[] normalize(double[] values)`

#### Parameters

- `values` (`double[]`)
