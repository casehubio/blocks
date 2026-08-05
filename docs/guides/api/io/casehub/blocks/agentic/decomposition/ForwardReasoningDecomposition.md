# io.casehub.blocks.agentic.decomposition.ForwardReasoningDecomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

SHOP-style forward-reasoning decomposition. Applies `PrimitiveTask.effect()`
to a projected copy of state during planning so downstream method guards see
the effects of upstream tasks.

<p>Drop-in replacement for `StaticDecomposition` on pure-symbolic trees.
The original state is never modified — a copy is made via the supplied copier.

## Fields

### `stateCopier` (`java.util.function.UnaryOperator<T>`)

## Constructors

### `public ForwardReasoningDecomposition(java.util.function.UnaryOperator<T> stateCopier)`

#### Parameters

- `stateCopier` (`java.util.function.UnaryOperator<T>`)

## Methods

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> task, DecompositionContext<T> ctx)`

#### Parameters

- `task` (`TaskNode<T>`)
- `ctx` (`DecompositionContext<T>`)

### `private void expand(TaskNode<T> node, T state, java.util.List<TaskNode.LeafTask<T>> result)`

#### Parameters

- `node` (`TaskNode<T>`)
- `state` (`T`)
- `result` (`java.util.List<TaskNode.LeafTask<T>>`)

### `private void expandMethod(DecompositionMethod<T> method, TaskNode.CompoundTask<T> ct, T state, java.util.List<TaskNode.LeafTask<T>> result)`

#### Parameters

- `method` (`DecompositionMethod<T>`)
- `ct` (`TaskNode.CompoundTask<T>`)
- `state` (`T`)
- `result` (`java.util.List<TaskNode.LeafTask<T>>`)
